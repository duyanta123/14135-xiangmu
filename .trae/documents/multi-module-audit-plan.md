# 项目多模块检查计划 (Test-Driven Audit)

## 概述

基于 BFF 中间层审计发现的模式（环境变量导致的运行时 Bug、死代码、路由匹配不一致、未使用依赖），对前端 `utils/`、`views/`、`api/`、`router/` 和后端 `service/`、`config/` 进行同类问题扫描。共发现 **8 个问题**（3 高 / 3 中 / 2 低）。

---

## 发现的问题

### 🟠 高 (High)

#### 问题 1: Layout 退出登录在 BFF 模式下未清除 HttpOnly Cookie

**涉及文件**:
- [`frontend/src/views/student/StudentLayout.vue`](file:///d:/789/frontend/src/views/student/StudentLayout.vue#L83-L85)
- [`frontend/src/views/teacher/TeacherLayout.vue`](file:///d:/789/frontend/src/views/teacher/TeacherLayout.vue#L73-L75)
- [`frontend/src/views/admin/AdminLayout.vue`](file:///d:/789/frontend/src/views/admin/AdminLayout.vue#L77-L79)

**问题描述**: 
三个 Layout 组件的 `handleLogout()` 都只执行了 `localStorage.removeItem('user')`，在 BFF 模式下没有调用 `/api/auth/logout` 清除 HttpOnly Cookie。用户退出后 `bff_token` Cookie 仍然存在，下次访问时路由守卫会认为已登录（因为 localStorage 中有 `token: 'bff-cookie'` 标记），但实际 Cookie 中的 Token 可能已过期，导致请求 401 循环。

**影响**: 退出登录后 Cookie 残留，可能导致重新登录时的状态不一致。

**修复方案**: 在 BFF 模式下，`handleLogout()` 增加调用 `/api/auth/logout` 接口清除 Cookie。

---

#### 问题 2: LoginAttemptService 线程安全问题

**涉及文件**: [`backend/src/main/java/com/labcourse/service/LoginAttemptService.java`](file:///d:/789/backend/src/main/java/com/labcourse/service/LoginAttemptService.java#L42-L49)

**问题描述**: 
`recordFailedAttempt()` 方法使用 `ConcurrentHashMap.computeIfAbsent()` 获取 `LoginAttempt` 对象后，直接调用 `recordFailure()` 修改其 `int` 类型字段 `attempts`。`attempts++` 对原始 int 类型不是原子操作，两个并发线程可能读到相同的值后各自递增，导致实际失败次数大于记录值。`firstAttemptTime` 字段也存在类似竞态。

**当前影响**: 在高并发场景下（如暴力破解攻击），攻击者可能比预期多尝试 1-2 次才被锁定，影响有限。

**修复方案**: 将 `LoginAttempt` 内部状态字段改为 `AtomicInteger`，或使用 `synchronized` 保护 `recordFailure()` / `lock()` 方法。

---

#### 问题 3: tokenManager.refreshTokenIfNeeded BFF 模式静默失败

**涉及文件**: [`frontend/src/utils/tokenManager.js`](file:///d:/789/frontend/src/utils/tokenManager.js#L58-L68)

**问题描述**: 
BFF 模式下 `refreshTokenIfNeeded()` 使用裸 `fetch('/api/auth/refresh')` 并 `.catch()` 静默吞掉所有错误。如果 BFF 服务未运行或网络异常，Token 刷新失败没有任何日志或降级处理，请求将继续携带过期 Cookie 直到 401 错误。

```javascript
async refreshTokenIfNeeded() {
    if (BFF_ENABLED) {
      try {
        await fetch('/api/auth/refresh', { method: 'POST', credentials: 'include' })
      } catch {
        // 静默失败，下次请求会触发 401 ← 无日志，无用户提示
      }
      return null
    }
```

**修复方案**: 添加 `console.warn` 日志记录刷新失败，便于排查问题。

---

### 🟡 中 (Medium)

#### 问题 4: BFF 刷新 Token 缺少 `sub` 声明

**涉及文件**:
- [`bff/src/routes/auth.js`](file:///d:/789/bff/src/routes/auth.js) (refresh handler)
- [`backend/src/main/java/com/labcourse/util/JwtUtil.java`](file:///d:/789/backend/src/main/java/com/labcourse/util/JwtUtil.java#L26-L31)

**问题描述**: 
BFF 的 `/api/auth/refresh` 使用 `jwt.sign()` 签发新 Token，只包含 `{userId, username, role}` 三个 claims。但后端 `JwtUtil.generateToken()` 除此外还将 `username` 设为 JWT 的 `subject`（`sub` claim）。虽然当前代码中 `JwtFilter` 仅读取 `userId` 和 `role` claims，不依赖 `sub`，但结构不一致可能在未来引用 `sub` 时出现问题。

**当前影响**: 无功能影响，`JwtFilter` 不读取 `sub`。

**修复方案**: BFF 刷新时对标后端，使用 `jwt.sign({...}, secret, { subject: username, ... })` 设置 sub 字段。

---

#### 问题 5: ScoreServiceImpl.addScore() 缺少 @Transactional

**涉及文件**: [`backend/src/main/java/com/labcourse/service/impl/ScoreServiceImpl.java`](file:///d:/789/backend/src/main/java/com/labcourse/service/impl/ScoreServiceImpl.java#L20-L34)

**问题描述**: 
`addScore()` 方法先查询是否存在记录，再决定 insert 或 update。虽然只涉及单表操作，但 `findBy*` 和 `save()` 之间缺少事务边界，理论上可能出现脏数据。其他服务类（如 `CourseServiceImpl.removeById`、`SelectionServiceImpl.addSelection`）已正确标注 `@Transactional`。

**当前影响**: 低 — 单表操作且 `findByStudentIdAndCourseId` 有唯一约束，实际风险低。

**修复方案**: 添加 `@Transactional` 注解，保持与其他 Service 方法一致。

---

#### 问题 6: 前端 Layout 渲染时依赖 localStorage 但缺少异常处理

**涉及文件**:
- [`frontend/src/views/student/StudentLayout.vue`](file:///d:/789/frontend/src/views/student/StudentLayout.vue#L81)
- [`frontend/src/views/teacher/TeacherLayout.vue`](file:///d:/789/frontend/src/views/teacher/TeacherLayout.vue#L71)
- [`frontend/src/views/admin/AdminLayout.vue`](file:///d:/789/frontend/src/views/admin/AdminLayout.vue#L75)

**问题描述**: 
Layout 组件的 `user` 计算属性使用 `JSON.parse(localStorage.getItem('user') || '{}')`。当 localStorage 中 `user` 值为损坏的 JSON 时（如手动编辑、浏览器扩展干扰），`JSON.parse()` 会抛出异常导致整个组件渲染失败。

**当前影响**: 低 — 仅在用户手动篡改 localStorage 时发生。

**修复方案**: 将 `JSON.parse` 包裹在 try-catch 中，失败时返回空对象。

---

### 🟢 低 (Low)

#### 问题 7: scheduleEventBus.js BroadcastChannel 无法恢复

**涉及文件**: [`frontend/src/utils/scheduleEventBus.js`](file:///d:/789/frontend/src/utils/scheduleEventBus.js#L11-L16)

**问题描述**: 
`getChannel()` 使用懒初始化单例模式。如果首次 `new BroadcastChannel()` 失败，`channel` 被设为 `null` 且永远不会重试。`notifyScheduleUpdate()` 和 `onScheduleUpdate()` 在 channel 为 null 时静默无操作。降级到 localStorage 方案虽然可用（`notifyScheduleUpdate` 也写入了 localStorage），但 `onScheduleUpdate` 的 localStorage 监听在 channel 失败时不会被注册，跨 Tab 通知失效。

**修复方案**: 在初始化失败时直接使用 localStorage + `storage` 事件作为 fallback 监听器。

---

#### 问题 8: CourseServiceImpl.removeById 使用 JdbcTemplate 绕过 JPA

**涉及文件**: [`backend/src/main/java/com/labcourse/service/impl/CourseServiceImpl.java`](file:///d:/789/frontend/src/main/java/com/labcourse/service/impl/CourseServiceImpl.java#L68-L85)

**问题描述**: 
`removeById()` 方法直接使用 `jdbcTemplate.update()` 删除 `selection`、`score`、`attendance` 表中的关联数据，然后才调用 `courseRepository.deleteById()`。这种模式绕过了 JPA 的 `@OneToMany` / 级联删除机制，如果将来新增与 Course 关联的实体表，此处代码容易遗漏更新。

**当前影响**: 无功能影响，现有逻辑正确。

**修复方案**: 在 Service 层增加注释说明关联表清单，或重构为通过 Repository 逐表删除。

---

## 修复计划

### 第一步: 修复 Layout 退出登录 BFF Cookie 清除 (High)

**文件**: `frontend/src/views/student/StudentLayout.vue`、`TeacherLayout.vue`、`AdminLayout.vue`

修改 `handleLogout()` 方法:

```javascript
// 修改前 (所有三个 Layout)
const handleLogout = () => {
  localStorage.removeItem('user')
  router.push('/login')
}

// 修改后
const handleLogout = async () => {
  const BFF_ENABLED = import.meta.env.VITE_BFF_ENABLED !== 'false'
  if (BFF_ENABLED) {
    try {
      await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' })
    } catch { /* 静默处理 */ }
  }
  localStorage.removeItem('user')
  router.push('/login')
}
```

### 第二步: 修复 LoginAttemptService 线程安全 (High)

**文件**: `backend/src/main/java/com/labcourse/service/LoginAttemptService.java`

修改 `LoginAttempt` 内部类，将 `int attempts` 替换为 `AtomicInteger`:

```java
private static class LoginAttempt {
    private final AtomicInteger attempts = new AtomicInteger(0);
    private volatile LocalDateTime firstAttemptTime;
    private volatile LocalDateTime lockUntil;

    void recordFailure() {
        LocalDateTime now = LocalDateTime.now();
        if (firstAttemptTime == null || isWindowExpired()) {
            attempts.set(0);
            firstAttemptTime = now;
        }
        attempts.incrementAndGet();
    }

    int getAttempts() {
        return attempts.get();
    }
}
```

### 第三步: 修复 tokenManager 静默失败 (High)

**文件**: `frontend/src/utils/tokenManager.js`

```javascript
// 修改 catch 块，添加日志
} catch (err) {
  console.warn('[TokenManager] BFF Token 刷新失败:', err.message)
}
```

### 第四步: 修复 BFF 刷新 Token sub 声明 (Medium)

**文件**: `bff/src/routes/auth.js`

```javascript
// 修改前
const newToken = jwt.sign(
  { userId: oldDecoded.userId, username: oldDecoded.username, role: oldDecoded.role },
  config.jwt.secret,
  { expiresIn: '24h', algorithm: 'HS256' }
)

// 修改后
const newToken = jwt.sign(
  { userId: oldDecoded.userId, username: oldDecoded.username, role: oldDecoded.role },
  config.jwt.secret,
  { expiresIn: '24h', algorithm: 'HS256', subject: oldDecoded.username || String(oldDecoded.userId) }
)
```

### 第五步: ScoreServiceImpl 添加 @Transactional (Medium)

**文件**: `backend/src/main/java/com/labcourse/service/impl/ScoreServiceImpl.java`

在 `addScore()` 方法上添加 `@Transactional` 注解。

### 第六步: Layout 组件 localStorage 异常加固 (Medium)

**文件**: 三个 Layout 组件

```javascript
// 修改前
const user = computed(() => JSON.parse(localStorage.getItem('user') || '{}'))

// 修改后
const user = computed(() => {
  try {
    return JSON.parse(localStorage.getItem('user') || '{}')
  } catch {
    return {}
  }
})
```

### 第七步: scheduleEventBus 降级优化 (Low)

**文件**: `frontend/src/utils/scheduleEventBus.js`

在 `BroadcastChannel` 不可用时，为 `onScheduleUpdate` 注册 `storage` 事件监听器作为 fallback。

### 第八步: CourseServiceImpl 添加注释 (Low)

**文件**: `backend/src/main/java/com/labcourse/service/impl/CourseServiceImpl.java`

在 `removeById()` 方法上增加关联表清单注释。

---

## 验证方式

1. **退出登录测试**: BFF 模式下登录 → 点击退出 → 检查 Cookie 是否已清除 → 刷新页面确认被重定向到 `/login`
2. **LoginAttemptService 并发测试**: 运行现有后端 JUnit 测试确保无回归 (`mvn verify`)
3. **BFF Token 刷新一致性测试**: 运行 BFF 测试 (`npm test`) 确保 66 个用例全部通过
4. **Layout 渲染异常测试**: 在浏览器 console 中手动 `localStorage.setItem('user', 'bad json')` → 刷新页面 → 确认页面不崩溃
5. **scheduleEventBus 降级测试**: 在 BroadCastChannel 不支持的环境中验证跨 Tab 通知仍可用
6. **ScoreService 测试**: 运行后端测试确认 `@Transactional` 不引入副作用

---

## 假设与决策

- 不改变现有 BFF 架构设计
- `LoginAttemptService` 的 `AtomicInteger` 方案不改变外部 API
- Layout 组件的三个相同逻辑保持一致的修复方式
- `broadcastChannel` 降级方案使用已有的 localStorage + storage 事件模式