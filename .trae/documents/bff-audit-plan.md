# BFF 中间层检查计划

## 概述

对 `d:\789\bff` 目录下的 Fastify BFF 中间层进行全面审计，识别代码质量、安全性和功能性缺陷，并提供修复方案。

---

## 当前状态分析

### BFF 中间层架构

```
index.js (入口)
  ├── config.js (配置)
  ├── routes/auth.js (认证路由: 登录/刷新/登出)
  ├── middleware/
  │   ├── jwtVerify.js (JWT 验证 + optionalJwt)
  │   ├── errorHandler.js (全局错误处理)
  │   └── requestLogger.js (请求日志)
  ├── proxy/
  │   ├── transparentProxy.js (透明代理 /api/* → 后端)
  │   └── proxyMapping.js (路由认证策略)
  ├── services/backendClient.js (后端 HTTP 客户端)
  └── utils/logger.js (日志工具 + maskSensitive)
```

### 数据流分析

**登录流程 (BFF模式)**:
```
前端 Login.vue
  → POST /api/student/login {studentNo, password}
  → BFF auth.js createLoginHandler
    → 参数校验 → backendClient.post('/api/student/login', body)
    → 后端返回 {success, data, token, message}
    → BFF 提取 token → setCookie (HttpOnly)
    → 返回 {success, data: {...userData}, message}  ← 注意：没有顶层 token
  → 前端 handleLogin()
    → result.token → undefined! ← BUG
    → localStorage 存储 {token: undefined, ...}
    → router guard: user.token → undefined → 永远无法通过登录校验
```

---

## 发现的问题

### 🔴 严重 (Critical)

#### 问题 1: BFF 模式登录后前端无法判断已登录状态

**涉及文件**: 
- [`frontend/src/views/Login.vue`](file:///d:/789/frontend/src/views/Login.vue#L140-L148)
- [`frontend/src/router/index.js`](file:///d:/789/frontend/src/router/index.js#L134-L135)
- [`bff/src/routes/auth.js`](file:///d:/789/bff/src/routes/auth.js#L72-L83)

**问题描述**: 
BFF 模式下 Token 存储在 HttpOnly Cookie 中，登录响应体不包含 `token` 字段。但前端 `Login.vue` 的 `handleLogin()` 方法尝试从 `result.token` 获取 token 存入 localStorage，导致存储的 token 为 `undefined`。路由守卫 `router.beforeEach` 通过 `user.token` 判断登录状态，因此用户登录后永远被重定向回 `/login`。

**影响**: BFF 模式完全不可用，所有用户无法登录。

**修复方案**:
修改前端 Login.vue，在 BFF 模式下不依赖 `result.token`，改用 `result.success` + role 判断登录状态，并在 localStorage 中存储登录标记替代 token 检查；同时修改 router 守卫适配 BFF 模式。

---

### 🟠 高 (High)

#### 问题 2: `optionalJwt` 死代码

**涉及文件**: [`bff/src/middleware/jwtVerify.js`](file:///d:/789/bff/src/middleware/jwtVerify.js#L133-L142)

**问题描述**: `optionalJwt` 函数已导出但从未在任何地方被 import 或使用。该函数是为公开接口兼容设计的（验证通过则注入 user，失败也继续），但目前没有任何路由使用它。

**影响**: 无功能影响，但增加维护负担和代码混淆。

**修复方案**: 如果确实不需要可选 JWT 验证，删除该函数。如果未来需要（如需要在公开接口中获取当前用户信息），则保留并在透明代理中使用。

---

### 🟡 中 (Medium)

#### 问题 3: `proxyMapping.requiresAuth()` 对公开路径使用前缀匹配

**涉及文件**: [`bff/src/proxy/proxyMapping.js`](file:///d:/789/bff/src/proxy/proxyMapping.js#L42-L46)

**问题描述**: `requiresAuth()` 方法使用 `path.startsWith(p)` 匹配公开路径。如果存在路径如 `/api/course/list/delete`（虽然目前不存在），它也会被误判为公开路径。`transparentProxy.js` 的 preHandler 使用精确匹配 (`path === p`)，但 `requiresAuth()` 单独调用时（如果被使用）会有风险。

**当前影响**: 低 — `requiresAuth()` 方法未被外部调用，实际认证逻辑在 `transparentProxy.js` 的 preHandler 中使用精确匹配。但如果将来复用 `requiresAuth()`，会有安全隐患。

**修复方案**: 将 `requiresAuth()` 中的公开路径匹配改为精确匹配，与 `transparentProxy.js` 中的逻辑保持一致。

#### 问题 4: BFF Token 刷新不验证用户存在性

**涉及文件**: [`bff/src/routes/auth.js`](file:///d:/789/bff/src/routes/auth.js#L119-L172)

**问题描述**: BFF 的 `/api/auth/refresh` 直接从旧 Token 的 claims 中提取 `userId`、`username`、`role` 并重新签发，不经过后端验证。如果用户在 Token 有效期内被删除或禁用，刷新后的 Token 仍然有效。

**当前影响**: 中等 — 由于 Token 有效期只有 24 小时且系统为教学演示项目，实际风险可控。

**修复方案**: 将刷新请求代理到后端 `/api/auth/refresh`，或添加后端验证步骤。

#### 问题 5: `transparentProxy` 中已登录路由的权限路径匹配有遗漏

**涉及文件**: [`bff/src/proxy/proxyMapping.js`](file:///d:/789/bff/src/proxy/proxyMapping.js#L17-L27)

**问题描述**: BFF 的 `authenticatedPaths` 使用前缀匹配（如 `/api/student/`），这会导致 `/api/student/login` 也被匹配为需要认证的路径。虽然由于 `setupAuthRoutes(app)` 先注册了显式路由，登录请求不会到达代理层，但这种设计不够精确。

**当前影响**: 低 — 由于路由注册顺序，实际行为正确。

**修复方案**: 在 `authenticatedPaths` 中排除公开路径，或在代理的 preHandler 中增加对已注册路由的显式跳过。

---

### 🟢 低 (Low)

#### 问题 6: `backendClient` 对 HTTP 错误的处理不完整

**涉及文件**: [`bff/src/services/backendClient.js`](file:///d:/789/bff/src/services/backendClient.js)

**问题描述**: `backendClient.request()` 使用 `fetch()` 但没有检查 `response.ok`。HTTP 4xx/5xx 错误不会抛出异常，而是直接尝试 `response.json()` 解析。如果后端返回非 JSON 错误页面，会抛出 JSON 解析异常。

**当前影响**: 低 — Spring Boot 后端始终返回 JSON 格式响应。

**修复方案**: 添加 `response.ok` 检查，在非成功状态码时提供更明确的错误信息。

#### 问题 7: 未使用的 `zod` 依赖

**涉及文件**: [`bff/package.json`](file:///d:/789/bff/package.json#L20)

**问题描述**: `package.json` 声明了 `zod` 依赖(用于参数校验)，但代码中未实际使用。登录接口的参数校验使用手动 `if` 判断。

**当前影响**: 无功能影响，增加依赖体积。

**修复方案**: 移除未使用的 `zod` 依赖，或用于替换手动的参数校验逻辑。

---

## 修复计划

### 第一步: 修复 BFF 模式登录 Bug (Critical)

**文件**: `frontend/src/views/Login.vue`

修改 `handleLogin()` 中的用户数据存储逻辑:
```javascript
// 修改前 (Line 140-146)
const userData = {
  ...result.data,
  role: loginForm.role,
  token: result.token,                    // BFF 模式下为 undefined
  tokenExpireTime: Date.now() + 86400 * 1000
}
localStorage.setItem('user', JSON.stringify(userData))
```

```javascript
// 修改后
const BFF_ENABLED = import.meta.env.VITE_BFF_ENABLED !== 'false'
const userData = {
  ...result.data,
  role: loginForm.role,
  token: result.token || (BFF_ENABLED ? 'bff-cookie' : undefined),
  tokenExpireTime: result.data?.tokenExpireTime || (Date.now() + 86400 * 1000),
  _bffMode: BFF_ENABLED
}
localStorage.setItem('user', JSON.stringify(userData))
```

**文件**: `frontend/src/router/index.js`

修改路由守卫中的登录判断:
```javascript
// 修改前 (Line 134-135)
const user = JSON.parse(localStorage.getItem('user') || '{}')
const isLoggedIn = user && user.token

// 修改后
const user = JSON.parse(localStorage.getItem('user') || '{}')
const isLoggedIn = user && user.token  // BFF 模式 token 为 'bff-cookie'
```

### 第二步: 清理死代码 (High)

**文件**: `bff/src/middleware/jwtVerify.js`

移除未使用的 `optionalJwt` 函数（第 133-142 行）。

### 第三步: 修复路由匹配逻辑 (Medium)

**文件**: `bff/src/proxy/proxyMapping.js`

修改 `requiresAuth()` 中公开路径的匹配方式:
```javascript
// 修改前
if (publicPaths.some(p => path.startsWith(p))) return false

// 修改后
if (publicPaths.some(p => path === p)) return false
```

### 第四步: 移除未使用依赖 (Low)

**文件**: `bff/package.json`

移除 `zod` 依赖（如不计划使用）。

---

## 验证方式

1. **BFF 模式登录**: 启动后端 + BFF + 前端，使用 BFF 模式 (`VITE_BFF_ENABLED=true`) 进行完整登录流程验证
2. **运行 BFF 测试**: `cd bff && npm test` 确保现有 4 个测试文件全部通过
3. **检查路由守卫**: 登录成功后刷新页面，确认不会被重定向到 `/login`
4. **Cookie 安全性**: 确认登录后 `bff_token` Cookie 设置了 `HttpOnly`、`SameSite=Lax` 属性
5. **降级模式兼容**: 设置 `VITE_BFF_ENABLED=false`，确认降级模式仍正常工作

---

## 假设与决策

- 保持现有 BFF 架构设计（登录由 BFF 处理，Token 存储在 HttpOnly Cookie）
- 不改变后端 SecurityConfig 的 RBAC 配置
- 不引入新的依赖
- BFF 模式继续作为默认模式