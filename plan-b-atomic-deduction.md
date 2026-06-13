# 方案 B：原子扣减（Atomic Deduction）— 详细实现文档

> 基于 [performance-optimization-report.md](file:///d:/789/performance-optimization-report.md) 整理

---

## 一、核心原理

**类比：库存扣减。** 课程的 `max_count` 等价于库存总量，`current_count` 等价于已消耗库存。每次选课等价于"库存 -1"，需要确保"库存 ≥ 0"。

**关键思想：** 用一个原子的 `UPDATE ... WHERE current_count < max_count` 替代"先 SELECT 再 INSERT"的两步操作，将"检查+扣减"合并为 1 条语句，利用 InnoDB 的隐式行锁保证原子性。

```
悲观锁 (V2):               原子扣减 (方案B):
┌──────────────────┐      ┌──────────────────┐
│ START TX          │      │ UPDATE course     │
│ SELECT ... FOR UP │      │ SET current_count │
│   (获取行锁)      │      │   = current_count+1│
│ IF cnt >= max     │      │ WHERE id=?        │
│   ROLLBACK        │      │   AND current_count│
│ INSERT            │      │   < max_count      │
│ COMMIT            │      │ (此行原子，无显式TX) │
│   (释放锁)        │      │                   │
└──────────────────┘      │ IF ROW_COUNT()=0  │
                           │   → 课程已满      │
                           │ ELSE              │
                           │ INSERT selection  │
                           └──────────────────┘
```

**与乐观锁 (方案A) 的关键区别：**

| 维度 | 方案A 乐观锁 | 方案B 原子扣减 |
|------|:----------:|:-----------:|
| 版本控制 | `version` 字段 (额外列) | 无 (利用 `current_count` 本身) |
| 冲突检测 | version ≠ old → 重试 | `ROW_COUNT() = 0` → 立即失败 |
| 重试机制 | SLEEP + 循环 (最多10次) | 无重试 (直接失败) |
| 并发模型 | CAS (Compare-And-Swap) | Test-and-Set |
| 锁粒度 | 无显式锁 (UPDATE 行锁瞬间) | 无显式锁 (UPDATE 行锁瞬间) |
| 实现复杂度 | 较高 (重试循环) | **最低** |

---

## 二、完整 SQL 实现

### 2.1 前置条件

```sql
-- 方案B 依赖 course.current_count 字段，确保其存在并准确
ALTER TABLE course ADD COLUMN IF NOT EXISTS current_count INT NOT NULL DEFAULT 0 COMMENT '当前已选人数';

-- 同步已有数据
UPDATE course c SET c.current_count = (
    SELECT COUNT(*) FROM selection WHERE course_id = c.id
);
```

### 2.2 存储过程实现

```sql
DROP PROCEDURE IF EXISTS enroll_course;

DELIMITER $$
CREATE PROCEDURE enroll_course(
    IN p_student_id BIGINT,
    IN p_course_id BIGINT
)
BEGIN
    DECLARE v_affected INT DEFAULT 0;

    -- Step 1: 快速失败 — 检查是否已选（无锁）
    IF EXISTS (SELECT 1 FROM selection
               WHERE student_id = p_student_id AND course_id = p_course_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '已选过该课程';
    END IF;

    -- Step 2: 原子扣减名额（单条 UPDATE，锁仅在本语句期间）
    UPDATE course
    SET current_count = current_count + 1
    WHERE id = p_course_id
      AND current_count < max_count;

    -- Step 3: 检查扣减结果
    IF ROW_COUNT() = 0 THEN
        -- 扣减失败 → 课程已满 或 课程不存在
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '课程已满';
    END IF;

    -- Step 4: 插入选课记录
    INSERT INTO selection (student_id, course_id)
    VALUES (p_student_id, p_course_id);
    -- 注意：current_count 已由 Step 2 原子 +1，
    --        因此需要禁用 trg_selection_count_inc 触发器，
    --        否则会双重自增！
END$$
DELIMITER ;
```

### 2.3 触发器冲突处理

**关键问题：** `trg_selection_count_inc` 触发器会在 INSERT selection 时自动执行 `UPDATE course SET current_count = current_count + 1`。如果存储过程已经 +1，触发器再次 +1，导致 `current_count` 实际 +2。

**解决方案（三选一）：**

| 方案 | 描述 | 推荐度 |
|------|------|:---:|
| ① 删除触发器 | `DROP TRIGGER trg_selection_count_inc` | 简单但影响其他调用方 |
| ② 条件触发器 | 利用 `@skip_trigger` 会话变量控制 | 精巧但需改触发器 |
| ③ 不依赖 current_count | 存储过程直接用 `selection` 表 COUNT | 回归 V1 问题 |

**推荐方案 ②：**

```sql
-- 修改触发器，增加条件判断
DROP TRIGGER IF EXISTS trg_selection_count_inc;
DELIMITER $$
CREATE TRIGGER trg_selection_count_inc
AFTER INSERT ON selection
FOR EACH ROW
BEGIN
    IF @skip_count_trigger IS NULL OR @skip_count_trigger = 0 THEN
        UPDATE course SET current_count = current_count + 1 WHERE id = NEW.course_id;
    END IF;
END$$
DELIMITER ;

-- 存储过程中设置标记
-- SET @skip_count_trigger = 1;  -- 在原子扣减前设置
```

**或方案 ①（最简，如果仅通过存储过程操作）：**

```sql
DROP TRIGGER IF EXISTS trg_selection_count_inc;
-- current_count 完全由存储过程的原子 UPDATE 维护
```

---

## 三、适用场景

| 场景 | 适用性 | 说明 |
|------|:---:|---|
| 小规模并发 (<50) | **最佳** | 极少 UPDATE 冲突，响应极快 |
| 中规模并发 (50-200) | **适合** | 轻微 UPDATE 行锁竞争，远优于悲观锁 |
| 大规模并发 (200+) | **适合** | 无需重试，UPDATE 行锁持有时长 μs 级 |
| 瞬时热点 (秒杀) | **建议方案A** | 原子扣减无重试 → 用户直接看到"已满"；方案A有重试机制，用户体验更好 |
| 需要"排队"语义 | 不适用 | 一次性判断，失败即终；需要排队选课系统 |
| 与触发器共存 | **需解决冲突** | current_count 双重自增问题 |

---

## 四、性能特点

### 4.1 锁分析

```
InnoDB 对 UPDATE ... WHERE 的实现：
1. 扫描 WHERE 条件匹配的行
2. 对匹配行加 X 锁（排他锁）
3. 执行 SET current_count = current_count + 1
4. 检查 current_count < max_count (WHERE 条件)
5. 如果条件不满足 → 不更新 → ROW_COUNT() = 0
6. 释放行锁
```

**锁持有时间：** 仅 UPDATE 语句的执行时间（~μs 级），远短于悲观锁的"START TRANSACTION ... COMMIT"（~ms 级）。

### 4.2 理论性能对比

| 操作 | V2 悲观锁 | 方案 B 原子扣减 | 差异 |
|------|:------:|:------:|:---:|
| 锁类型 | 显式 FOR UPDATE | 隐式 UPDATE 行锁 | — |
| 锁持有时间 | 整个事务 (~0.24s) | UPDATE 语句 (~10μs) | **24000x** |
| 锁队列深度 | 59 (60-1) | 0 (无排队) | 消除 |
| SQL 语句数 | 4 (SELECTx2,INSERT,COMMIT) | 2 (UPDATE,INSERT) | -50% |
| 需要事务 | 是 | 否 | 简化 |

### 4.3 预期性能表现

| 指标 | V1 原始 | V2 缓存 | 方案A 乐观(实测) | **方案B 原子(预期)** |
|------|:-----:|:-----:|:------:|:------:|
| 60 并发耗时 | 19.83s | 14.56s | 14.91s | **~2-4s** |
| TPS | 3.02 | 4.12 | 4.02 | **~15-30** |
| 锁等待 | 排队 | 排队 | 重试SLEEP | **无** |
| 双写风险 | 否 | 否 | 否 | **需处理触发器** |

---

## 五、实施步骤

```
Step 1: DROP TRIGGER trg_selection_count_inc （删除计数器触发器）
         ↓
Step 2: ALTER TABLE course 确保 current_count 存在并同步
         ↓
Step 3: CREATE PROCEDURE enroll_course (原子扣减版)
         ↓
Step 4: 调整 score 触发器：保留 trg_selection_after_insert（自动创建成绩）
         ↓
Step 5: 功能验证：正常选课 / 重复 / 已满
         ↓
Step 6: 压力测试：60/100/200 并发
         ↓
Step 7: 对比分析 V1/V2/方案A/方案B
```

---

## 六、优势总结

1. **实现简单：** 核心仅 1 条 UPDATE 语句，无需版本列、重试循环、事务管理
2. **锁开销极小：** 隐式行锁仅在 UPDATE 期间（μs 级），无长事务锁等待
3. **无死锁风险：** 不涉及多表加锁或锁升级
4. **TPS 上限高：** 理论 ∞，只受 InnoDB 行锁性能限制（约 5000-10000 TPS）
5. **代码可读性强：** 逻辑直观，`ROW_COUNT()` 语义明确