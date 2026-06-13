# 后端 Attendance 测试修复计划 (TDD)

## 概述

后端 3 个 Attendance 测试文件中出现 8 个失败用例。根因是测试**硬编码了只特定星期才匹配的课程 ID**，而今天 (2026-06-13) 是**周六**，种子数据中没有周六的课程，导致 `checkIn()` 返回 `{success: false, message: "今天没有该课程安排"}`。

---

## 根因分析

### 故障链

```
测试执行 (周六)
  → @BeforeEach: 登录 S001/T001 成功 ✓
  → testCheckIn_Success: POST /api/attendance/check-in {studentId: 1, courseId: 3}
    → AttendanceServiceImpl.checkIn()
      → courseRepository.findById(3) → Web开发技术 (周三 5-6节) ✓
      → studentRepository.findById(1) → 王小明 ✓
      → 解析 courseTime: "周三 5-6节" → dayOfWeek=3
      → today.getDayOfWeek().getValue() → 6 (周六) 
      → 3 ≠ 6 → courseStartTime = null
      → return {success: false, message: "今天没有该课程安排"} ← BUG触发点
  → jsonPath("$.success").value(true) → FAIL
```

### 关键代码位置

**`AttendanceServiceImpl.java` Line 207-212**:
```java
if (courseStartTime == null) {
    result.put("success", false);
    result.put("message", "今天没有该课程安排");  // ← 周六触发此行
    return result;
}
```

### 种子数据课程-日期映射

| 课程 ID | 课程名 | 星期 | dayOfWeek |
|---------|--------|------|-----------|
| 1 | Java程序设计 | 周一 | 1 |
| 2 | 数据库原理 | 周二 | 2 |
| 3 | Web开发技术 | 周三 | 3 |
| 4 | 计算机网络 | 周四 | 4 |
| 5 | 软件测试 | 周五 | 5 |
| _(无)_ | _(无)_ | 周六/日 | 6/7 |

### 影响的 8 个用例

| 测试文件 | 测试方法 | 依赖的课程 | 所需星期 |
|----------|---------|-----------|---------|
| `AttendanceServiceTest` | `testCheckIn_Success` | CID=3 (周三) | 周三 |
| `AttendanceServiceTest` | `testAddAttendance_Success` | CID=3 (周三) | 周三 |
| `AttendanceServiceTest` | `testMultiStudentCheckIn` | CID=3 (周三) | 周三 |
| `AttendanceServiceTest` | `testCheckIn_DuplicateRejected` | CID=3 (周三) | 周三 |
| `AttendanceLoggingTest` | `scenario1_NormalCheckIn` | CID=3 (周三) | 周三 |
| `AttendanceLoggingTest` | `scenario2_LateCheckIn` | CID=3 (周三) | 周三 |
| `OfflineQueueRecoveryTest` | `testRapidFire_ThreeRequests` | CID=3 (周三) | 周三 |
| `OfflineQueueRecoveryTest` | `testConcurrent_MultiThreadCheckIn` | CID=3 (周三) | 周三 |

---

## 修复方案: 动态课程选择 (Date-Independent)

### 策略

在 3 个测试文件的 `@BeforeEach` 中，根据今天的星期自动选择匹配的课程 ID。工作日(1-5)使用对应课程，周末(6-7)使用 `Assumptions.assumeTrue` 跳过依赖 check-in 的测试。

### 周日到周五的映射

```java
// DayOfWeek (1=周一 ~ 7=周日) → courseId
private static final Map<Integer, Long> DAY_TO_COURSE = Map.of(
    1, 1L,  // 周一: Java程序设计
    2, 2L,  // 周二: 数据库原理
    3, 3L,  // 周三: Web开发技术
    4, 4L,  // 周四: 计算机网络
    5, 5L   // 周五: 软件测试
);
```

### 需要修改的文件

#### 文件 1: `AttendanceServiceTest.java`

**变更**: 
- 废弃硬编码 `CID3 = 3L`，改用动态 `activeCourseId`
- `@BeforeEach` 中计算 `activeCourseId = DAY_TO_COURSE.get(todayDayOfWeek)`
- 若今天为周末(`null`)，标记 `isWeekend = true`，后续 check-in 测试通过 `Assumptions` 跳过
- `@BeforeEach` 中清理逻辑使用 `activeCourseId`

```java
// 在类级别添加
private static final Map<Integer, Long> DAY_TO_COURSE = Map.of(
    1, 1L, 2, 2L, 3, 3L, 4, 4L, 5, 5L
);
private Long activeCourseId;  // 替换硬编码 CID3
private boolean isWeekend;

// @BeforeEach 中添加
int todayDayOfWeek = today.getDayOfWeek().getValue();
activeCourseId = DAY_TO_COURSE.get(todayDayOfWeek);
isWeekend = activeCourseId == null;
if (isWeekend) {
    activeCourseId = 3L; // fallback for non-check-in tests
}
```

**涉及的测试方法修改**:
- `testCheckIn_Success` (Line 102-115): 开头添加 `Assumptions.assumeFalse(isWeekend, "周末无课程")`; `CID3` → `activeCourseId`
- `testAddAttendance_Success` (Line 117-129): `CID3` → `activeCourseId`
- `testMultiStudentCheckIn` (Line 191-202): `CID3` → `activeCourseId` + `Assumptions`
- `testCheckIn_DuplicateRejected` (Line 208-229): `CID3` → `activeCourseId` + `Assumptions`
- `setUp()` cleanup (Line 89-95): `CID3` → `activeCourseId`

#### 文件 2: `AttendanceLoggingTest.java`

**变更**:
- 废弃硬编码 `COURSE_ID = 3L`，改用动态 `activeCourseId`
- `@BeforeEach` 中计算 `activeCourseId`，若周末则标记 `isWeekend`

**涉及的测试方法修改**:
- `scenario1_NormalCheckIn` (Line 135-183): `COURSE_ID` → `activeCourseId` + `Assumptions`; 日志断言中的"星期三"改为动态
- `scenario2_LateCheckIn` (Line 188-): `COURSE_ID` → `activeCourseId` + `Assumptions`; 日志断言中的"星期三"改为动态

#### 文件 3: `OfflineQueueRecoveryTest.java`

**变更**:
- 废弃硬编码 `CID1 = 3L`，改用动态 `activeCourseId`
- `@BeforeEach` 中计算 `activeCourseId`，周末时标记 `isWeekend`
- `@BeforeEach` 中清理逻辑使用 `activeCourseId`

**涉及的测试方法修改**:
- `testRapidFire_ThreeRequests` (Line 445-469): `CID1` → `activeCourseId` + `Assumptions`
- `testConcurrent_MultiThreadCheckIn` (Line 496-535): `CID1` → `activeCourseId` + `Assumptions`
- `testGraceful_LargeBatchQueueReplay` (Line 471-494): `CID1` → `activeCourseId` + `Assumptions`

### 日志断言修正

`AttendanceLoggingTest` 中的日志断言硬编码了课程名和星期信息:
- `"courseName=Web开发技术"` → 需改为动态（或使用 `CourseRepository` 查询）
- `"todayDayOfWeek=3"` → 需改为 `"todayDayOfWeek=" + todayDayOfWeek`
- 学生名 `"王小明"` → 保持不变（S001 始终是 id=1）

由于日志是 DEBUG 级别，`assertLogContains` 检查关键词即可。可将课程名断言从具体值改为使用 `courseRepository.findById(activeCourseId).getCourseName()` 查询。

### 非 check-in 测试处理

`testGetCourseAttendance`, `testExportAttendance`, `testGetAttendanceDates` 等测试不依赖 check-in，但仍需有效的 `courseId`。这些测试应使用 `activeCourseId` 而非硬编码。若周末 `activeCourseId == null`，可以使用 `3L` 作为 fallback（这些端点不检查当天是否有课）。

---

## 验证方式

1. **周三执行**: 所有 80 个测试全部通过（回归原期望行为）
2. **周末执行**: 依赖 check-in 的测试通过 `Assumptions` 跳过，其余测试全部通过
3. **TDD 红-绿-重构**: 
   - RED: 周末执行当前测试 → 确认 8 个失败（已确认）
   - GREEN: 应用修复 → 周末测试全部通过（8 个 Skipped，其余 PASS）
   - REFACTOR: 检查是否可进一步简化

---

## 备选方案（未采用）

| 方案 | 优点 | 缺点 | 未采用原因 |
|------|------|------|-----------|
| `Clock` 注入 | 真正的时间无关 | 需改 production 代码 | 过度工程，非最小修复 |
| 添加周末课程种子数据 | 简单 | 污染已有种子数据 | 测试覆盖不充分 |
| `@DisabledOnOs` + 日期 | 无代码改动 | Junit 不支持按星期 Skip | 不可行 |

---

## 假设与决策

- 保持现有 `@SpringBootTest` + 真实 MySQL 的集成测试架构
- 不修改 production 代码 (`AttendanceServiceImpl`)
- 周末时跳过 check-in 相关测试是合理行为（种子数据无周末课程）
- 使用 `Assumptions.assumeFalse` 而非 `@Disabled` 以便 JUnit 报告区分 "跳过" 和 "失败"