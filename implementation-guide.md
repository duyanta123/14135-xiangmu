# 选课系统三方案共存 — 实施指南

> **版本：** v1.0  
> **日期：** 2026-06-09  
> **数据库：** MySQL 8.0.44 / Windows / PowerShell Start-Job  
> **参考报告：** [final-recommendation-report.md](file:///d:/789/final-recommendation-report.md)

---

## 一、架构总览

```
                       ┌─────────────────────────┐
                       │   enroll_course         │
                       │   (统一路由入口)          │
                       └───────────┬─────────────┘
                                   │
                    读取 course.max_count
                                   │
                 ┌─────────────────┴─────────────────┐
                 │                                   │
          max_count ≤ 10                      max_count > 10
                 │                                   │
                 ▼                                   ▼
     ┌─────────────────────┐           ┌─────────────────────┐
     │  enroll_course_a    │           │  enroll_course_b    │
     │  方案A: 乐观锁+重试   │           │  方案B: 原子扣减      │
     │  - CAS version      │           │  - UPDATE WHERE      │
     │  - SLEEP 50~150ms   │           │  - ROW_COUNT()判定   │
     │  - 最多重试10次      │           │  - 无事务/无重试      │
     └─────────────────────┘           └─────────────────────┘

        特殊场景（直接调用，不经过路由）:
     ┌─────────────────────┐
     │  enroll_course_v2   │
     │  V2: 悲观锁          │
     │  - SELECT FOR UPDATE │
     │  - 事务内锁定         │
     │  退课+补选/跨行事务   │
     └─────────────────────┘
```

### 1.1 文件清单

| 文件 | 路径 | 用途 |
|------|------|------|
| 部署脚本 | [deploy_final.sql](file:///d:/789/database/deploy_final.sql) | 一键部署三方案共存的存储过程和触发器 |
| 数据校准 | [maintenance.sql](file:///d:/789/database/maintenance.sql) | 定时校准 current_count + 超选审计 |
| 回滚脚本 | [rollback_final.sql](file:///d:/789/database/rollback_final.sql) | 紧急回退至 V2 单方案 |

---

## 二、三方案锁机制深度对比

### 2.1 锁持有时间线

```
方案B 原子扣减（锁 ≈ 单条UPDATE的 InnoDB 隐式行锁）
│ UPDATE WHERE current_count<max_count ──锁(μs)──┤
│ ROW_COUNT()=1? → INSERT                         │
└─────────────────────────────────────────────────┘
锁持有: ~10μs | 锁排队: 无 | 冲突窗口: 不存在

方案A 乐观锁（锁 ≈ CAS UPDATE 的 InnoDB 行锁 + 重试退避）
│ SELECT(快照,无锁) → UPDATE WHERE version=? ──锁(μs)──┤
│ ROW_COUNT=0? → SLEEP(50-150ms) → [重试]              │
└──────────────────────────────────────────────────────┘
锁持有: ~10μs/次 | 重试: 0~10次 | 冲突窗口: SELECT→UPDATE 之间

V2 悲观锁（锁 = 整个事务）
│ START TX → SELECT FOR UPDATE ─────────锁──────────────┐
│            满员检查 → UPDATE → INSERT → COMMIT → 释放锁 │
└──────────────────────────────────────────────────────┘
锁持有: ~240ms | 锁排队: N-1个 | 冲突窗口: 不存在（串行化）
```

### 2.2 冲突率与性能模型

| 维度 | 方案B (原子扣减) | 方案A (乐观锁) | V2 (悲观锁) |
|------|:---:|:---:|:---:|
| **冲突检测时机** | UPDATE 语句内 (InnoDB 隐式锁) | CAS 判定后 SLEEP 重试 | 事务排队等待 |
| **冲突率** | 0%（一次判定） | 高（小名额时重试频繁） | 0%（串行化） |
| **锁开销** | 极低 (μs) | 低 (μs × 重试次数) | 高 (ms × 排队长度) |
| **TPS 上限** | ~5000-10000 | ~100-500 | ~4-10 |
| **可扩展性** | 水平扩展友好 | 中等 | 差 |
| **实现复杂度** | 极简 (~25行) | 中等 (~55行) | 中等 (~45行) |
| **依赖项** | current_count | current_count + version | current_count + 事务 |

---

## 三、适用场景判定流程图

```
                     开始：选课请求
                          │
                          ▼
              ┌─────────────────────┐
              │ 是否退课+补选场景？     │
              │ (需跨行原子事务)        │
              └──────────┬──────────┘
                    是   │   否
                    ▼    │    ▼
          ┌──────────┐  │  ┌─────────────────────┐
          │ 调用      │  │  │ 是否管理员后台操作？    │
          │ V2 悲观锁  │  │  │ (纯串行无并发)         │
          └──────────┘  │  └──────────┬──────────┘
                        │        是   │   否
                        │        ▼    │    ▼
                        │  ┌──────────┐│  ┌──────────────────────┐
                        │  │ 调用      ││  │ 调用 enroll_course    │
                        │  │ 方案B/V2  ││  │ (统一路由入口)         │
                        │  └──────────┘│  └──────────┬───────────┘
                        │              │             │
                        │              │    读取 course.max_count
                        │              │             │
                        │              │    ┌────────┴────────┐
                        │              │    │                 │
                        │              │ max_count ≤ 10   max_count > 10
                        │              │    │                 │
                        │              │    ▼                 ▼
                        │              │ ┌──────────┐  ┌──────────┐
                        │              │ │方案A      │  │方案B      │
                        │              │ │乐观锁+重试 │  │原子扣减    │
                        │              │ └──────────┘  └──────────┘
```

### 3.1 技术选型决策树

```
课程特征分析
│
├─ max_count ≤ 10（小名额热门课）
│  ├─ 瞬时并发 > 100 → 方案A + Redis队列（P2）
│  ├─ 瞬时并发 20-100 → 方案A（乐观锁+重试）
│  └─ 瞬时并发 < 20  → 方案A 或 方案B 均可
│
├─ max_count > 10（大名额普通课）
│  └─ 任意并发 → 方案B（原子扣减）★ 首选
│
├─ 退课+补选（跨行事务）
│  └─ 必须 V2（悲观锁）— 多行锁保证一致性
│
└─ 管理员手动操作
   └─ 方案B 或 V2（无并发压力，简单即可）
```

---

## 四、部署流程

### 4.1 部署前检查清单

```
[ ] 1. 备份当前数据库
       mysqldump -u root -p lab_course_system > backup_$(date +%Y%m%d).sql

[ ] 2. 确认 MySQL 版本 ≥ 8.0
       mysql -u root -p -e "SELECT VERSION();"

[ ] 3. 确认 lab_course_system 数据库存在
       mysql -u root -p -e "SHOW DATABASES LIKE 'lab_course_system';"

[ ] 4. 记录当前存储过程（用于回滚）
       mysql -u root -p -e "SHOW CREATE PROCEDURE enroll_course\G"

[ ] 5. 确认 course 表存在且包含 max_count 列
```

### 4.2 部署步骤

```powershell
# Step 1: 进入 MySQL 执行部署脚本
cd d:\789\database
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p123456 < deploy_final.sql
```

### 4.3 部署后验证

```sql
-- 检查存储过程是否全部部署
SELECT ROUTINE_NAME FROM information_schema.ROUTINES
WHERE ROUTINE_SCHEMA = 'lab_course_system'
  AND ROUTINE_NAME LIKE 'enroll_course%';
-- 预期: enroll_course, enroll_course_a, enroll_course_b, enroll_course_v2

-- 检查触发器（应有3个，不应有 trg_selection_count_inc）
SELECT TRIGGER_NAME FROM information_schema.TRIGGERS
WHERE TRIGGER_SCHEMA = 'lab_course_system';
-- 预期: trg_selection_count_dec, trg_selection_after_insert, trg_score_after_update

-- 快速冒烟测试：正常选课
CALL enroll_course(1, 1);  -- 学生1选课程1（max=35 → 方案B）
-- 预期: 成功

-- 快速冒烟测试：重复选课
CALL enroll_course(1, 1);
-- 预期: ERROR "已选过该课程"
```

---

## 五、测试方案

功能验证和并发压力测试已通过后端 JUnit 5 集成测试套件覆盖（`AttendanceServiceTest.java` 等），无需额外的 PowerShell 测试脚本。

### 5.1 功能验证覆盖

---

## 六、方案切换与回滚机制

### 6.1 灰度切换方案

```
Phase 1（当前）: 全部请求 → enroll_course (V2)
     │  部署 deploy_final.sql，三方案共存
     ▼
Phase 2（灰度）: 修改应用层代码
     │  - 正常选课 → enroll_course (自动分流 A/B)
     │  - 特殊场景 → enroll_course_v2 (直接调用)
     │  观察 1-2 天，确认无异常
     ▼
Phase 3（全量）: 全部切换至自动路由
     │  废弃旧版 enroll_course，使用统一路由入口
     ▼
Phase 4（进阶）: 按场景精细调整
        - 秒杀场景 → 直接 enroll_course_a
        - 日常选课 → 直接 enroll_course_b
        - 混合策略（可选）
```

### 6.2 回滚操作

```powershell
# 紧急回滚：恢复至 V2 悲观锁单方案
cd d:\789\database
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p123456 < rollback_final.sql

# 回滚后验证
mysql -u root -p123456 -e "
  SELECT ROUTINE_NAME FROM information_schema.ROUTINES
  WHERE ROUTINE_SCHEMA = 'lab_course_system'
    AND ROUTINE_NAME LIKE 'enroll_course%';
"
# 预期: 仅有 enroll_course (回滚后的 V2 版本)
```

### 6.3 切换影响矩阵

| 操作 | 影响范围 | 风险等级 | 停机时间 |
|------|:---:|:---:|:---:|
| 部署 deploy_final.sql | 仅替换存储过程/触发器，不影响数据 | 低 | 0 |
| 应用层切换路由调用 | 仅影响新请求的路由方式 | 低 | 0 |
| 执行回滚脚本 | 恢复存储过程，不影响数据 | 低 | 0 |
| DROP TABLE / TRUNCATE | 数据丢失 | 高 | 永久 |

---

## 七、问题分析与应对

### 7.1 方案B 原子扣减 — 关键风险

| 风险 | 根因 | 缓解 |
|------|------|------|
| current_count 双重自增 | 旧触发器 `trg_selection_count_inc` 与 SP 内 UPDATE 同时 +1 | **已固定**：部署脚本自动 DROP 该触发器 |
| 用户无重试感知 | 失败即返回"已满"，无自动排队 | 前端提示"名额已满，可稍后重试" |
| current_count 漂移 | 异常断电/手动删选课记录 | 定时校准脚本 ([maintenance.sql](file:///d:/789/database/maintenance.sql)) |

### 7.2 方案A 乐观锁 — 关键风险

| 风险 | 根因 | 缓解 |
|------|------|------|
| SLEEP 累积延迟 | 每次 CAS 失败需等 50-150ms，高冲突时叠加 | 限制最大重试 10 次；仅小名额（≤10）使用 |
| 重试耗尽 | 极端热点下 10 次均 CAS 失败 | 返回"系统繁忙"，提示用户手动重试 |
| version 无限增长 | 每次 CAS 成功 version+1 | INT 上限 21 亿，实际无影响 |

### 7.3 V2 悲观锁 — 关键风险

| 风险 | 根因 | 缓解 |
|------|------|------|
| 锁排队导致 TPS 低 | 事务持有锁期间阻塞所有并发 | 仅限特殊场景（退课+补选、管理员操作） |
| 潜在死锁 | 多行 FOR UPDATE 时的锁顺序 | 确保始终按相同顺序锁定（如按 course_id 升序） |

---

## 八、日常运维

### 8.1 定时校准（建议每天凌晨执行）

```sql
-- 直接执行 maintenance.sql 或设置 MySQL EVENT:
CREATE EVENT IF NOT EXISTS evt_calibrate_current_count
ON SCHEDULE EVERY 1 DAY STARTS '2026-06-10 03:00:00'
DO
    UPDATE lab_course_system.course c
    SET c.current_count = (
        SELECT COUNT(*) FROM selection WHERE course_id = c.id
    );
```

### 8.2 超选监控告警

```sql
-- 实时查询：任何课程 current_count > max_count 即告警
SELECT id, course_name, max_count, current_count,
       current_count - max_count AS 超选人数
FROM course
WHERE current_count > max_count;
-- 正常情况下应返回空结果集
```

### 8.3 性能趋势监控

```sql
-- 查看各课程选课速率（最近1小时）
SELECT c.id, c.course_name, c.max_count,
       c.current_count,
       ROUND(c.current_count / c.max_count * 100, 1) AS 满员率
FROM course c
ORDER BY 满员率 DESC;
```

---

## 九、文件索引

| 文件 | 行数 | 用途 |
|------|:---:|------|
| [database/deploy_final.sql](file:///d:/789/database/deploy_final.sql) | ~480 | 完整部署：结构准备 + 触发器 + 4个SP |
| [database/maintenance.sql](file:///d:/789/database/maintenance.sql) | ~90 | 数据校准 + 超选检测 |
| [database/rollback_final.sql](file:///d:/789/database/rollback_final.sql) | ~130 | 紧急回滚至 V2 单方案 |
| [final-recommendation-report.md](file:///d:/789/final-recommendation-report.md) | ~220 | 方案选型原始报告 |

---

> **一句话总结：** 常规选课 → `enroll_course`（自动分流 B/A），退课补选 → `enroll_course_v2`，出问题 → `rollback_final.sql`。