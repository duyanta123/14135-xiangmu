# Node.js BFF 中间层完整落地方案

> **基于**: [nodejs-bff-feasibility-report.md](file:///d:/789/nodejs-bff-feasibility-report.md) 第 8.2 节三阶段路径 + 第 8.3 节建议
> **项目**: 实验选课系统
> **日期**: 2026-06-12

---

## 目录

1. [总览与前置准备](#1-总览与前置准备)
2. [第一阶段：透明代理 + 认证增强（MVP，20h）](#2-第一阶段透明代理--认证增强mvp20h)
3. [第二阶段：API 聚合 + 响应适配（增量，30h）](#3-第二阶段api-聚合--响应适配增量30h)
4. [第三阶段：高级特性（增强，27h）](#4-第三阶段高级特性增强27h)
5. [附录：关键代码清单](#5-附录关键代码清单)

---

## 1. 总览与前置准备

### 1.1 技术栈（遵循 8.3 建议）

| 层级 | 技术 | 版本 |
|------|------|------|
| 运行时 | Node.js | **20.x LTS** |
| 框架 | Fastify | **4.x** |
| HTTP 客户端 | undici | **6.x** |
| JWT | jsonwebtoken | **9.x** |
| 缓存（三阶段） | ioredis | **5.x** |
| 限流（三阶段） | @fastify/rate-limit | **9.x** |
| WebSocket（三阶段） | @fastify/websocket | **10.x** |
| 日志 | pino | **8.x** |
| 校验 | zod | **3.x** |
| 测试 | vitest | **1.x** |
| 进程管理 | PM2 | **5.x** |

### 1.2 项目目录结构

```
d:\789\
├── bff/                              # ← 新增 BFF 目录
│   ├── package.json
│   ├── .env                          # 环境变量（不提交）
│   ├── .env.example                  # 环境变量模板
│   ├── ecosystem.config.cjs          # PM2 配置
│   ├── src/
│   │   ├── index.js                  # 入口：Fastify 启动
│   │   ├── config.js                 # 配置聚合（环境变量 + 默认值）
│   │   ├── middleware/
│   │   │   ├── jwtVerify.js          # JWT 验证中间件（Phase 1）
│   │   │   ├── requestLogger.js      # 请求日志中间件（Phase 1）
│   │   │   ├── rateLimiter.js        # 限流中间件（Phase 3）
│   │   │   └── errorHandler.js       # 统一错误处理（Phase 1）
│   │   ├── proxy/
│   │   │   ├── transparentProxy.js   # 透明代理基础框架（Phase 1）
│   │   │   └── proxyMapping.js       # 代理路由映射表（Phase 1）
│   │   ├── routes/
│   │   │   ├── auth.js               # 认证路由（Phase 1）
│   │   │   ├── aggregate/
│   │   │   │   ├── studentDashboard.js  # 学生首页聚合（Phase 2）
│   │   │   │   ├── teacherAttendance.js # 教师考勤页聚合（Phase 2）
│   │   │   │   └── adminDashboard.js    # 管理员仪表盘聚合（Phase 2）
│   │   │   ├── offlineCheckin.js     # 离线签到路由（Phase 3）
│   │   │   └── ws/
│   │   │       └── realtime.js       # WebSocket 实时推送（Phase 3）
│   │   ├── services/
│   │   │   ├── backendClient.js      # 后端 HTTP 调用封装（Phase 1）
│   │   │   ├── cacheService.js       # 缓存服务（Phase 2/3）
│   │   │   └── queueService.js       # 离线队列服务（Phase 3）
│   │   └── utils/
│   │       └── createTokenCookie.js  # HttpOnly Cookie 工具（Phase 1）
│   └── tests/
│       ├── jwt.test.js               # JWT 验证测试（Phase 1）
│       ├── proxy.test.js             # 代理转发测试（Phase 1）
│       ├── aggregate.test.js         # 聚合端点测试（Phase 2）
│       └── queue.test.js             # 离线队列测试（Phase 3）
├── frontend/                         # 现有前端
├── backend/                          # 现有后端
└── database/                         # 现有数据库脚本
```

### 1.3 环境变量（`.env.example`）

```bash
# ── 服务器配置 ──
NODE_ENV=development
BFF_PORT=4000
BACKEND_URL=http://localhost:8080

# ── JWT ──
JWT_SECRET=lab-course-system-secret-key-2024-very-long-secret
JWT_EXPIRATION=86400000

# ── Redis（Phase 3 启用）──
REDIS_URL=redis://localhost:6379

# ── 日志级别 ──
LOG_LEVEL=info
```

### 1.4 现有系统关键参数（从源码提取）

| 参数 | 值 | 来源文件 |
|------|---|----------|
| JWT 密钥 | `lab-course-system-secret-key-2024-very-long-secret` | [application.yml](file:///d:/789/backend/src/main/resources/application.yml#L23) |
| JWT 过期 | `86400000`（24 小时） | [application.yml](file:///d:/789/backend/src/main/resources/application.yml#L24) |
| JWT 算法 | HS256 | [JwtUtil.java](file:///d:/789/backend/src/main/java/com/labcourse/util/JwtUtil.java) |
| 前端刷新阈值 | 10 分钟 | [tokenManager.js](file:///d:/789/frontend/src/utils/tokenManager.js#L3) |
| 后端端口 | 8080 | [application.yml](file:///d:/789/backend/src/main/resources/application.yml#L20) |
| 前端端口 | 3000 | [vite.config.js](file:///d:/789/frontend/vite.config.js#L13) |
| CORS 允许源 | `http://localhost:3000` | [SecurityConfig.java](file:///d:/789/backend/src/main/java/com/labcourse/config/SecurityConfig.java#L76) |
| BFF 端口 | **4000**（新增） | — |

### 1.5 降级策略（遵循 8.3 建议第 4 条）

```
┌──────────┐   VITE_BFF_ENABLED=true    ┌──────────┐       ┌──────────┐
│ Vue 3    │ ──────────────────────────> │ BFF :4000│ ────> │ SB :8080 │
│ :3000    │                             │          │       │          │
└──────────┘                             └──────────┘       └──────────┘
     │                                                            ▲
     │           VITE_BFF_ENABLED=false（降级）                     │
     └────────────────────────────────────────────────────────────┘
```

- 前端 [vite.config.js](file:///d:/789/frontend/vite.config.js) 通过 `VITE_BFF_ENABLED` 环境变量控制代理目标
- `true` → BFF `:4000`，`false` → 直连 `:8080`
- 零停机切换

---

## 2. 第一阶段：透明代理 + 认证增强（MVP，20h）

### 2.1 目标

- 搭建 Fastify 基础框架，实现 `/api/*` 透明代理到 Spring Boot
- BFF 层集中验证 JWT，前端改为使用 HttpOnly Cookie 存储 Token
- 前端 Token 管理逻辑大幅简化，安全性提升
- **零后端改动**

### 2.2 任务分解

| # | 任务 | 文件 | 工时 |
|---|------|------|------|
| 1.1 | 创建 `bff/` 项目骨架 | `bff/package.json`, `bff/src/index.js`, `bff/src/config.js`, `bff/.env.example` | 2h |
| 1.2 | 实现透明代理框架 | `bff/src/proxy/transparentProxy.js`, `bff/src/proxy/proxyMapping.js` | 3h |
| 1.3 | 实现后端 HTTP 客户端 | `bff/src/services/backendClient.js` | 1h |
| 1.4 | 实现 JWT 验证中间件 | `bff/src/middleware/jwtVerify.js` | 3h |
| 1.5 | 实现认证路由（login/refresh/logout） | `bff/src/routes/auth.js` | 3h |
| 1.6 | 实现请求日志 + 错误处理中间件 | `bff/src/middleware/requestLogger.js`, `bff/src/middleware/errorHandler.js` | 2h |
| 1.7 | 前端 Vite 代理切换 | `frontend/vite.config.js` | 0.5h |
| 1.8 | 前端 request.js + tokenManager.js 适配 | `frontend/src/utils/request.js`, `frontend/src/utils/tokenManager.js` | 3h |
| 1.9 | 前端登录页适配 | `frontend/src/views/Login.vue` | 1h |
| 1.10 | 基础测试编写 | `bff/tests/jwt.test.js`, `bff/tests/proxy.test.js` | 2h |
| **合计** | | | **~20.5h** |

### 2.3 实现详情

#### 2.3.1 `bff/package.json`

```json
{
  "name": "lab-course-bff",
  "version": "1.0.0",
  "description": "实验选课系统 BFF 中间层",
  "type": "module",
  "scripts": {
    "dev": "node --watch src/index.js",
    "start": "node src/index.js",
    "test": "vitest run",
    "test:watch": "vitest"
  },
  "dependencies": {
    "fastify": "^4.28.0",
    "@fastify/cookie": "^9.4.0",
    "@fastify/cors": "^9.0.1",
    "@fastify/formbody": "^7.4.0",
    "jsonwebtoken": "^9.0.2",
    "pino": "^9.4.0",
    "pino-pretty": "^11.3.0",
    "zod": "^3.23.0"
  },
  "devDependencies": {
    "vitest": "^1.6.0"
  }
}
```

#### 2.3.2 `bff/src/config.js` — 配置中心

```javascript
import { readFileSync } from 'node:fs'

const env = process.env

export const config = {
  port: parseInt(env.BFF_PORT || '4000', 10),
  backendUrl: env.BACKEND_URL || 'http://localhost:8080',
  nodeEnv: env.NODE_ENV || 'development',

  jwt: {
    secret: env.JWT_SECRET || 'lab-course-system-secret-key-2024-very-long-secret',
    expiration: parseInt(env.JWT_EXPIRATION || '86400000', 10),
    cookieName: 'bff_token',
    cookieMaxAge: 24 * 60 * 60 * 1000, // 24 小时
  },

  log: {
    level: env.LOG_LEVEL || 'info',
    pretty: env.NODE_ENV !== 'production',
  },
}
```

#### 2.3.3 `bff/src/index.js` — 入口程序

```javascript
import Fastify from 'fastify'
import cookie from '@fastify/cookie'
import cors from '@fastify/cors'
import formbody from '@fastify/formbody'
import { config } from './config.js'
import { setupAuthRoutes } from './routes/auth.js'
import { transparentProxyPlugin } from './proxy/transparentProxy.js'
import { errorHandler } from './middleware/errorHandler.js'
import { requestLogger } from './middleware/requestLogger.js'

async function buildApp() {
  const app = Fastify({
    logger: {
      level: config.log.level,
      transport: config.log.pretty
        ? { target: 'pino-pretty', options: { colorize: true } }
        : undefined,
    },
  })

  // 插件注册
  await app.register(cors, {
    origin: 'http://localhost:3000',
    credentials: true,
  })
  await app.register(cookie, {
    secret: config.jwt.secret,
  })
  await app.register(formbody)

  // 中间件
  app.addHook('onRequest', requestLogger)

  // 路由：认证（优先匹配，不代理）
  await setupAuthRoutes(app)

  // 透明代理：其他所有 /api/* → Spring Boot
  await app.register(transparentProxyPlugin)

  // 全局错误处理
  app.setErrorHandler(errorHandler)

  return app
}

// 启动
const start = async () => {
  const app = await buildApp()
  try {
    await app.listen({ port: config.port, host: '0.0.0.0' })
    app.log.info(`BFF server running at http://localhost:${config.port}`)
  } catch (err) {
    app.log.error(err)
    process.exit(1)
  }
}

start()

export { buildApp }
```

#### 2.3.4 `bff/src/middleware/jwtVerify.js` — JWT 中间件

```javascript
import jwt from 'jsonwebtoken'
import { config } from '../config.js'

/**
 * JWT 验证中间件
 * 优先从 HttpOnly Cookie 读取 Token，fallback 到 Authorization Header
 * 验证通过后将用户信息注入 request.user
 */
export async function jwtVerify(request, reply) {
  // 1. 从 Cookie 获取 Token
  let token = request.cookies?.[config.jwt.cookieName]

  // 2. Fallback：从 Authorization Header 获取（向前兼容）
  if (!token) {
    const authHeader = request.headers.authorization
    if (authHeader?.startsWith('Bearer ')) {
      token = authHeader.slice(7)
    }
  }

  if (!token) {
    reply.code(401)
    throw new Error('未提供认证信息')
  }

  try {
    const decoded = jwt.verify(token, config.jwt.secret, {
      algorithms: ['HS256'],
    })

    // 注入用户信息
    request.user = {
      userId: decoded.userId || decoded.sub,
      username: decoded.username || decoded.sub,
      role: decoded.role,
    }

    // 将原始 Token 附加到请求头，以便透传给后端
    request.headers.authorization = `Bearer ${token}`
  } catch (err) {
    // Token 过期 → 返回 401 让前端重定向登录
    if (err.name === 'TokenExpiredError') {
      reply.clearCookie(config.jwt.cookieName)
      reply.code(401)
      throw new Error('Token 已过期，请重新登录')
    }
    reply.code(401)
    throw new Error('Token 无效')
  }
}

/**
 * 可选 JWT 验证：验证通过则注入 user，失败也继续（用于公开接口兼容）
 */
export async function optionalJwt(request, reply) {
  try {
    await jwtVerify(request, reply)
  } catch {
    // 不阻断请求
  }
}
```

#### 2.3.5 `bff/src/routes/auth.js` — 认证路由

```javascript
import jwt from 'jsonwebtoken'
import { config } from '../config.js'
import { backendClient } from '../services/backendClient.js'

export async function setupAuthRoutes(app) {
  /**
   * POST /api/student/login
   * POST /api/teacher/login
   * POST /api/admin/login
   *
   * 代理登录请求到后端，成功后签发 HttpOnly Cookie
   */
  const loginHandler = async (request, reply) => {
    const targetPath = request.url // /api/student/login, /api/teacher/login, /api/admin/login

    const response = await backendClient.post(targetPath, request.body)

    if (response.success && response.token) {
      // 签发 HttpOnly Cookie
      reply.setCookie(config.jwt.cookieName, response.token, {
        httpOnly: true,
        secure: config.nodeEnv === 'production',
        sameSite: 'lax',
        path: '/',
        maxAge: config.jwt.cookieMaxAge / 1000, // 秒
      })

      // 返回用户信息（不包含 Token，Token 在 Cookie 中）
      return {
        success: true,
        message: response.message || '登录成功',
        data: {
          userId: response.userId,
          username: response.username,
          role: response.role,
          // 前端路由守卫仍需要 role 信息
        },
      }
    }

    return response
  }

  app.post('/api/student/login', loginHandler)
  app.post('/api/teacher/login', loginHandler)
  app.post('/api/admin/login', loginHandler)

  /**
   * POST /api/auth/refresh
   * Token 刷新 —— BFF 端自主处理，前端无感知
   */
  app.post('/api/auth/refresh', {
    preHandler: [app.jwtVerify || ((await import('../middleware/jwtVerify.js')).jwtVerify)],
    handler: async (request, reply) => {
      // BFF 端直接签发新 Token（共享同一密钥）
      const { userId, username, role } = request.user

      const newToken = jwt.sign(
        { userId, username, role },
        config.jwt.secret,
        {
          algorithm: 'HS256',
          expiresIn: config.jwt.expiration / 1000, // 秒
        }
      )

      reply.setCookie(config.jwt.cookieName, newToken, {
        httpOnly: true,
        secure: config.nodeEnv === 'production',
        sameSite: 'lax',
        path: '/',
        maxAge: config.jwt.cookieMaxAge / 1000,
      })

      return {
        success: true,
        message: 'Token 已刷新',
        expiresIn: config.jwt.expiration,
      }
    },
  })

  /**
   * POST /api/auth/logout
   */
  app.post('/api/auth/logout', async (request, reply) => {
    reply.clearCookie(config.jwt.cookieName, { path: '/' })
    return { success: true, message: '已退出登录' }
  })

  /**
   * GET /api/auth/validate — 透传到后端
   */
  app.get('/api/auth/validate', {
    preHandler: [async (request) => {
      const { jwtVerify } = await import('../middleware/jwtVerify.js')
      await jwtVerify(request)
    }],
    handler: async (request, reply) => {
      return backendClient.get('/api/auth/validate', {
        headers: { Authorization: request.headers.authorization },
      })
    },
  })
}
```

> **实际实现注意**: `jwtVerify` 的导入需要用动态 `import()` 或将其预先注册为 Fastify decorator 以避免循环依赖。

#### 2.3.6 `bff/src/services/backendClient.js` — 后端 HTTP 客户端

```javascript
import { config } from '../config.js'

const BACKEND = config.backendUrl

class BackendClient {
  async request(method, path, options = {}) {
    const url = `${BACKEND}${path}`
    const headers = {
      'Content-Type': 'application/json',
      ...(options.headers || {}),
    }

    const fetchOptions = {
      method,
      headers,
      ...(options.body ? { body: JSON.stringify(options.body) } : {}),
    }

    const response = await fetch(url, fetchOptions)

    // 透传非 JSON 响应（文件导出等）
    const contentType = response.headers.get('content-type') || ''
    if (!contentType.includes('application/json')) {
      return response
    }

    return response.json()
  }

  get(path, options)    { return this.request('GET', path, options) }
  post(path, body, options) { return this.request('POST', path, { ...options, body }) }
  put(path, body, options)  { return this.request('PUT', path, { ...options, body }) }
  delete(path, options)     { return this.request('DELETE', path, options) }
}

export const backendClient = new BackendClient()
```

#### 2.3.7 `bff/src/proxy/transparentProxy.js` — 透明代理

```javascript
import { config } from '../config.js'
import { jwtVerify } from '../middleware/jwtVerify.js'
import { proxyMapping } from './proxyMapping.js'

/**
 * 透明代理插件
 * 将未显式注册的路由全部转发到 Spring Boot
 * 根据 proxyMapping 决定是否需要 JWT 认证
 */
export async function transparentProxyPlugin(app) {
  // 通配路由处理所有 /api/* 请求
  app.route({
    method: ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'OPTIONS'],
    url: '/api/*',
    handler: async (request, reply) => {
      const { method, url, body, headers: reqHeaders } = request

      // 构建转发请求头：携带已验证的 Authorization
      const forwardHeaders = { ...reqHeaders }

      // 如果 BFF 已验证过 Token，使用验证后的 Authorization
      if (request.user && request.rawHeaders) {
        forwardHeaders.authorization = `Bearer ${request.cookies?.[config.jwt.cookieName]}`
      }

      // 移除仅 BFF 内部使用的头
      delete forwardHeaders.host
      delete forwardHeaders.connection

      const response = await fetch(`${config.backendUrl}${url}`, {
        method,
        headers: forwardHeaders,
        ...(body && Object.keys(body).length > 0
          ? { body: JSON.stringify(body) }
          : {}),
      })

      // 透传状态码
      reply.code(response.status)

      // 透传响应头
      response.headers.forEach((value, key) => {
        if (!['transfer-encoding', 'connection'].includes(key.toLowerCase())) {
          reply.header(key, value)
        }
      })

      // 特殊处理非 JSON 响应（文件导出）
      const contentType = response.headers.get('content-type') || ''
      if (contentType.includes('application/vnd.openxmlformats')
        || contentType.includes('application/octet-stream')) {
        const buffer = await response.arrayBuffer()
        return reply.send(Buffer.from(buffer))
      }

      return response.json()
    },
  })
}
```

#### 2.3.8 `bff/src/proxy/proxyMapping.js` — 路由映射表

```javascript
/**
 * 路由认证策略映射
 * 基于 Spring Security 安全配置定义哪些路径需要认证
 *
 * 来源: backend/.../config/SecurityConfig.java
 */
export const proxyMapping = {
  // ── 公共路由（不需要 JWT）──
  public: [
    '/api/student/login',
    '/api/teacher/login',
    '/api/admin/login',
    '/api/course/list',
    '/api/course/list/simple',
  ],

  // ── 需要认证的路由 ──
  authenticated: [
    '/api/auth/refresh',
    '/api/auth/validate',
    '/api/selection/',
    '/api/attendance/',
    '/api/student/',
    '/api/teacher/',
    '/api/admin/',
    '/api/score/',
    '/api/lab/',
  ],

  /**
   * 判断路径是否需要 JWT 认证
   */
  static requiresAuth(path) {
    if (this.public.some(p => path.startsWith(p))) return false
    if (this.authenticated.some(p => path.startsWith(p))) return true
    return true // 默认需要认证
  },
}
```

#### 2.3.9 前端改动

##### `frontend/vite.config.js` — 代理切换

```javascript
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const bffEnabled = env.VITE_BFF_ENABLED !== 'false'
  const proxyTarget = bffEnabled
    ? 'http://localhost:4000'
    : 'http://localhost:8080'

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src'),
      },
    },
    server: {
      port: 3000,
      proxy: {
        '/api': {
          target: proxyTarget,
          changeOrigin: true,
          rewrite: (p) => p,
        },
      },
    },
  }
})
```

添加 `.env.development`:

```bash
VITE_BFF_ENABLED=true
```

##### `frontend/src/utils/tokenManager.js` — 简化

```javascript
/**
 * 简化版 TokenManager — BFF 模式下 Token 由 HttpOnly Cookie 管理
 * 保留 localStorage 仅用于存储非敏感用户信息（role, username 等）
 *
 * 降级模式（VITE_BFF_ENABLED=false）下保持原有逻辑
 */

const BFF_ENABLED = import.meta.env.VITE_BFF_ENABLED !== 'false'

export default {
  /**
   * BFF 模式下 Token 由 Cookie 自动携带，前端不管理
   */
  getToken() {
    if (BFF_ENABLED) return null // Cookie 自动携带
    const user = JSON.parse(localStorage.getItem('user') || '{}')
    return user.token || null
  },

  setToken(token, expiresIn) {
    if (BFF_ENABLED) return // Cookie 由 BFF 管理
    const user = JSON.parse(localStorage.getItem('user') || '{}')
    user.token = token
    user.tokenExpireTime = Date.now() + expiresIn * 1000
    localStorage.setItem('user', JSON.stringify(user))
  },

  setUser(userInfo) {
    localStorage.setItem('user', JSON.stringify(userInfo))
  },

  getUser() {
    return JSON.parse(localStorage.getItem('user') || '{}')
  },

  clearToken() {
    localStorage.removeItem('user')
  },

  isLoggedIn() {
    return !!JSON.parse(localStorage.getItem('user') || '{}').role
  },

  isTokenAboutToExpire() {
    if (BFF_ENABLED) return false // BFF 端自动刷新
    const user = JSON.parse(localStorage.getItem('user') || '{}')
    if (!user.tokenExpireTime) return true
    return (user.tokenExpireTime - Date.now()) / 1000 < 600
  },

  async refreshTokenIfNeeded() {
    if (BFF_ENABLED) {
      // BFF 模式下，仅调用 refresh 端点让 BFF 刷新 Cookie
      try {
        await fetch('/api/auth/refresh', { method: 'POST', credentials: 'include' })
      } catch {
        // 静默失败，下次请求会触发 401
      }
      return null
    }
    // 降级模式保持原逻辑...
    return this.getToken()
  },
}
```

##### `frontend/src/utils/request.js` — 简化拦截器

```javascript
import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import tokenManager from './tokenManager'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
  withCredentials: true,  // ← 新增：携带 Cookie
})

// BFF 模式：移除复杂的手动 Token 刷新逻辑
const BFF_ENABLED = import.meta.env.VITE_BFF_ENABLED !== 'false'

request.interceptors.request.use(
  async (config) => {
    if (!BFF_ENABLED) {
      // 降级模式：保留原有逻辑（略）
    }
    // BFF 模式：Cookie 自动携带，无需手动设置 Authorization
    return config
  },
  (error) => Promise.reject(error),
)

request.interceptors.response.use(
  (response) => response.data,
  (error) => {
    if (error.response?.status === 401) {
      ElMessage.error('登录已过期，请重新登录')
      tokenManager.clearToken()
      router.push('/login')
    } else if (error.response?.status === 403) {
      ElMessage.error('没有权限执行此操作')
    } else if (error.response?.status === 423) {
      ElMessage.error(error.response.data?.message || '账号已被锁定')
    } else if (!error.response) {
      ElMessage.error('网络连接失败，请检查网络')
    } else {
      ElMessage.error(error.response?.data?.message || '请求失败')
    }
    return Promise.reject(error)
  },
)

export default request
```

### 2.4 第一阶段验收检查清单

- [ ] `npm run dev` 在 `bff/` 目录下正常启动，端口 4000
- [ ] 前端 `VITE_BFF_ENABLED=true` 时，登录 → Cookie 中有 `bff_token`
- [ ] 登录后访问需认证的 API（如 `/api/selection/my`）正常返回数据
- [ ] 前端刷新页面，Token 由 Cookie 自动携带，无需重新登录
- [ ] `post /api/auth/logout` 清除 Cookie，后续请求返回 401
- [ ] 降级模式 `VITE_BFF_ENABLED=false` 前端直连 8080 正常工作
- [ ] 所有 30+ API 端点透明代理正常
- [ ] `npm test` 在 `bff/` 下全部通过

---

## 3. 第二阶段：API 聚合 + 响应适配（增量，30h）

### 3.1 目标

- 实现 3 个核心聚合端点，前端请求量减少 50%
- 内存缓存常用数据，首屏加载加速
- 前端渐进式切换到聚合端点（向前兼容）

### 3.2 任务分解

| # | 任务 | 工时 |
|---|------|------|
| 2.1 | 实现 `GET /api/bff/student/dashboard` 聚合端点 | 3h |
| 2.2 | 实现 `GET /api/bff/teacher/attendance-page` 聚合端点 | 3h |
| 2.3 | 实现 `GET /api/bff/admin/dashboard` 聚合端点 | 2h |
| 2.4 | 内存缓存服务 | 3h |
| 2.5 | 响应数据转换适配器 | 3h |
| 2.6 | 前端 StudentCourse 组件切换到聚合端点 | 2h |
| 2.7 | 前端 TeacherAttendance 组件切换到聚合端点 | 2h |
| 2.8 | 前端 AdminLayout 组件切换到聚合端点 | 2h |
| 2.9 | 缓存失效机制 | 2h |
| 2.10 | 聚合端点测试 | 3h |
| 2.11 | 性能基准测试与对比 | 2h |
| 2.12 | 端到端回归测试 | 3h |
| **合计** | | **~30h** |

### 3.3 三大聚合端点设计

#### 3.3.1 学生首页聚合 `GET /api/bff/student/dashboard`

**当前**: [StudentCourse.vue](file:///d:/789/frontend/src/views/student/StudentCourse.vue) 需要调用 3 个 API:
- `GET /api/course/list` — 所有可选课程
- `GET /api/selection/my` — 已选课程
- 课表数据（从已选课程中解析）

**BFF 聚合后**: 1 次请求

```javascript
// bff/src/routes/aggregate/studentDashboard.js

import { backendClient } from '../../services/backendClient.js'
import { cacheService } from '../../services/cacheService.js'
import { jwtVerify } from '../../middleware/jwtVerify.js'

export async function setupStudentDashboard(app) {
  app.get('/api/bff/student/dashboard', {
    preHandler: [jwtVerify],
    handler: async (request) => {
      const userId = request.user.userId

      // 并行请求后端
      const [allCourses, mySelections] = await Promise.all([
        backendClient.get('/api/course/list'),
        backendClient.get(`/api/selection/my?studentId=${userId}`),
      ])

      // 响应适配：构建前端所需结构
      const myCourseIds = new Set(
        mySelections.data?.map(s => s.courseId) || []
      )

      return {
        success: true,
        data: {
          // 所有课程 + 是否已选标记
          courses: (allCourses.data || []).map(course => ({
            ...course,
            isSelected: myCourseIds.has(course.id),
          })),
          // 已选课程（含课表数据）
          myCourses: mySelections.data || [],
          // 统计数据
          stats: {
            totalCourses: (allCourses.data || []).length,
            selectedCount: myCourseIds.size,
          },
        },
      }
    },
  })
}
```

#### 3.3.2 教师考勤页聚合 `GET /api/bff/teacher/attendance-page`

**当前**: [TeacherAttendance.vue](file:///d:/789/frontend/src/views/teacher/TeacherAttendance.vue) 需要调用:
- `GET /api/course/list/simple` — 课程列表
- `GET /api/selection/studentList/{courseId}` — 学生名单
- `GET /api/attendance/course?courseId=&date=` — 考勤记录

```javascript
// bff/src/routes/aggregate/teacherAttendance.js

export async function setupTeacherAttendance(app) {
  app.get('/api/bff/teacher/attendance-page', {
    preHandler: [jwtVerify],
    handler: async (request) => {
      const { courseId, date } = request.query
      const teacherId = request.user.userId

      const [courses, students, attendance] = await Promise.all([
        backendClient.get('/api/course/list/simple'),
        courseId
          ? backendClient.get(`/api/selection/studentList/${courseId}`)
          : Promise.resolve({ data: [] }),
        courseId && date
          ? backendClient.get(`/api/attendance/course?courseId=${courseId}&date=${date}`)
          : Promise.resolve({ data: [] }),
      ])

      // 合并学生信息 + 考勤状态
      const studentList = (students.data || []).map(student => {
        const record = (attendance.data || []).find(
          a => a.studentId === student.studentId
        )
        return {
          ...student,
          attendanceStatus: record?.status || 'none',
          checkInTime: record?.checkInTime || null,
        }
      })

      return {
        success: true,
        data: {
          courses: courses.data || [],
          students: studentList,
          currentCourseId: courseId || null,
          currentDate: date || null,
        },
      }
    },
  })
}
```

#### 3.3.3 管理员仪表盘聚合 `GET /api/bff/admin/dashboard`

**当前**: 管理页面无仪表盘，需分别数 4 个列表

```javascript
// bff/src/routes/aggregate/adminDashboard.js

export async function setupAdminDashboard(app) {
  app.get('/api/bff/admin/dashboard', {
    preHandler: [jwtVerify],
    handler: async () => {
      const [students, teachers, courses, labs] = await Promise.all([
        backendClient.get('/api/student/list'),
        backendClient.get('/api/teacher/list'),
        backendClient.get('/api/course/list'),
        backendClient.get('/api/lab/list'),
      ])

      return {
        success: true,
        data: {
          stats: {
            studentCount: (students.data || []).length,
            teacherCount: (teachers.data || []).length,
            courseCount: (courses.data || []).length,
            labCount: (labs.data || []).length,
          },
          recentStudents: (students.data || []).slice(0, 5),
          recentTeachers: (teachers.data || []).slice(0, 5),
        },
      }
    },
  })
}
```

### 3.4 内存缓存服务

```javascript
// bff/src/services/cacheService.js

class MemoryCacheService {
  constructor() {
    this.store = new Map()
  }

  /**
   * 缓存数据，设置 TTL（毫秒）
   */
  set(key, value, ttlMs = 5 * 60 * 1000) {
    this.store.set(key, {
      value,
      expiresAt: Date.now() + ttlMs,
    })
  }

  get(key) {
    const entry = this.store.get(key)
    if (!entry) return null
    if (Date.now() > entry.expiresAt) {
      this.store.delete(key)
      return null
    }
    return entry.value
  }

  invalidate(key) {
    if (key) {
      this.store.delete(key)
    } else {
      this.store.clear()
    }
  }

  /**
   * 缓存包装器：先查缓存，未命中则执行 fetcher
   */
  async wrap(key, ttlMs, fetcher) {
    const cached = this.get(key)
    if (cached !== null) return cached

    const result = await fetcher()
    this.set(key, result, ttlMs)
    return result
  }
}

export const cacheService = new MemoryCacheService()
```

#### 缓存策略

| 数据 | TTL | 失效触发 |
|------|-----|---------|
| 课程列表 | 5 分钟 | 增/删/改课程时失效 |
| 实验室列表 | 10 分钟 | 增/删/改实验室时失效 |
| 学生列表 | 5 分钟 | 增/删/改学生时失效 |
| 教师列表 | 5 分钟 | 增/删/改教师时失效 |

### 3.5 前端渐进式切换

前端组件修改策略：
- 在第二阶段，保留原有的单 API 调用路径
- 优先使用聚合端点 `GET /api/bff/student/dashboard` 等
- 若聚合端点返回 404（BFF 未部署），fallback 到原有多次 API 调用
- 这确保第二阶段可独立部署，不影响第一阶段稳定性

### 3.6 第二阶段验收检查清单

- [ ] `GET /api/bff/student/dashboard` 返回聚合数据，与原 3 次调用数据一致
- [ ] `GET /api/bff/teacher/attendance-page` 返回合并后的学生+考勤数据
- [ ] `GET /api/bff/admin/dashboard` 返回四项统计数据
- [ ] 课程列表缓存 5 分钟，缓存期内不请求后端
- [ ] 课程数据变更后缓存自动失效
- [ ] 前端聚合端点不可用时自动降级到原多次调用
- [ ] 首屏加载请求数量减少（学生端 3→1，教师端 3→1，管理员 4→1）

---

## 4. 第三阶段：高级特性（增强，27h）

### 4.1 目标

- Redis 缓存层（替代内存缓存）
- 离线签到队列 BFF 端持久化
- WebSocket 实时推送
- 请求限流与监控

### 4.2 任务分解

| # | 任务 | 工时 |
|---|------|------|
| 3.1 | Redis 集成（替换内存缓存） | 4h |
| 3.2 | 离线签到队列 BFF 端 API | 4h |
| 3.3 | 前端 offlineCheckin.js 适配 BFF API | 2h |
| 3.4 | WebSocket 实时推送框架 | 3h |
| 3.5 | 考勤状态变更实时通知 | 3h |
| 3.6 | 选课名额变化推送 | 2h |
| 3.7 | 请求限流中间件 | 2h |
| 3.8 | 监控指标端点（/health, /metrics） | 2h |
| 3.9 | PM2 生产部署配置 | 1h |
| 3.10 | 端到端测试 + 压力测试 | 4h |
| **合计** | | **~27h** |

### 4.3 Redis 缓存集成

在 `bff/package.json` 中新增依赖:

```json
{
  "dependencies": {
    "ioredis": "^5.4.0",
    "@fastify/rate-limit": "^9.1.0",
    "@fastify/websocket": "^10.0.0"
  }
}
```

```javascript
// bff/src/services/cacheService.js → Phase 3 升级版

import Redis from 'ioredis'
import { config } from '../config.js'

class RedisCacheService {
  constructor() {
    this.redis = config.redisUrl
      ? new Redis(config.redisUrl)
      : null
    // fallback 内存缓存
    this.memoryFallback = new Map()
  }

  async set(key, value, ttlMs = 5 * 60 * 1000) {
    const serialized = JSON.stringify(value)
    if (this.redis) {
      await this.redis.set(key, serialized, 'PX', ttlMs)
    } else {
      this.memoryFallback.set(key, {
        value: serialized,
        expiresAt: Date.now() + ttlMs,
      })
    }
  }

  async get(key) {
    if (this.redis) {
      const raw = await this.redis.get(key)
      return raw ? JSON.parse(raw) : null
    }
    const entry = this.memoryFallback.get(key)
    if (!entry) return null
    if (Date.now() > entry.expiresAt) {
      this.memoryFallback.delete(key)
      return null
    }
    return JSON.parse(entry.value)
  }

  async invalidate(pattern) {
    if (this.redis) {
      const keys = await this.redis.keys(pattern || '*')
      if (keys.length > 0) await this.redis.del(keys)
    } else {
      this.memoryFallback.clear()
    }
  }

  async wrap(key, ttlMs, fetcher) {
    const cached = await this.get(key)
    if (cached !== null) return cached
    const result = await fetcher()
    await this.set(key, result, ttlMs)
    return result
  }
}

export const cacheService = new RedisCacheService()
```

### 4.4 离线签到队列 BFF 端实现

```javascript
// bff/src/services/queueService.js

import { writeFileSync, readFileSync, existsSync } from 'node:fs'
import { join } from 'node:path'

const QUEUE_FILE = join(process.cwd(), 'data', 'offline_checkin_queue.json')

class QueueService {
  constructor() {
    this.queue = this._load()
  }

  _load() {
    try {
      if (existsSync(QUEUE_FILE)) {
        return JSON.parse(readFileSync(QUEUE_FILE, 'utf-8'))
      }
    } catch { /* ignore */ }
    return []
  }

  _save() {
    const dir = join(process.cwd(), 'data')
    if (!existsSync(dir)) {
      const { mkdirSync } = require('node:fs')
      mkdirSync(dir, { recursive: true })
    }
    writeFileSync(QUEUE_FILE, JSON.stringify(this.queue, null, 2))
  }

  enqueue(entry) {
    const exists = this.queue.some(
      item => item.studentId === entry.studentId && item.courseId === entry.courseId
    )
    if (!exists) {
      this.queue.push({ ...entry, timestamp: Date.now() })
      this._save()
    }
    return this.queue.length
  }

  getAll() {
    return [...this.queue]
  }

  remove(studentId, courseId) {
    this.queue = this.queue.filter(
      item => !(item.studentId === studentId && item.courseId === courseId)
    )
    this._save()
    return this.queue.length
  }

  clear() {
    this.queue = []
    this._save()
  }

  get size() {
    return this.queue.length
  }
}

export const queueService = new QueueService()
```

```javascript
// bff/src/routes/offlineCheckin.js

import { queueService } from '../services/queueService.js'
import { backendClient } from '../services/backendClient.js'
import { jwtVerify } from '../middleware/jwtVerify.js'

export async function setupOfflineRoutes(app) {
  /**
   * POST /api/offline/checkin/enqueue — 加入离线队列
   */
  app.post('/api/offline/checkin/enqueue', {
    preHandler: [jwtVerify],
    handler: async (request) => {
      const { studentId, courseId } = request.body
      const size = queueService.enqueue({ studentId, courseId })
      return { success: true, queueSize: size }
    },
  })

  /**
   * GET /api/offline/checkin/queue — 查看离线队列
   */
  app.get('/api/offline/checkin/queue', {
    preHandler: [jwtVerify],
    handler: async () => {
      return { success: true, data: queueService.getAll() }
    },
  })

  /**
   * POST /api/offline/checkin/retry — 手动重放队列
   */
  app.post('/api/offline/checkin/retry', {
    preHandler: [jwtVerify],
    handler: async () => {
      const results = []
      for (const item of queueService.getAll()) {
        try {
          const res = await backendClient.post('/api/attendance/check-in', {
            studentId: item.studentId,
            courseId: item.courseId,
          })
          if (res.success) {
            queueService.remove(item.studentId, item.courseId)
          }
          results.push({ ...item, success: res.success })
        } catch (err) {
          results.push({ ...item, success: false, error: err.message })
        }
      }
      return { success: true, results, remaining: queueService.size }
    },
  })
}
```

前端 `offlineCheckin.js` 适配：将 localStorage 操作改为调用 BFF API。

### 4.5 WebSocket 实时推送

```javascript
// bff/src/routes/ws/realtime.js

export async function setupWebSocket(app) {
  // 注册 WebSocket 插件（需提前在 index.js 中注册 @fastify/websocket）

  app.get('/ws/realtime', { websocket: true }, (socket, request) => {
    app.log.info('WebSocket client connected')

    // 心跳保活
    const heartbeat = setInterval(() => {
      socket.send(JSON.stringify({ type: 'ping', timestamp: Date.now() }))
    }, 30000)

    socket.on('message', (raw) => {
      try {
        const msg = JSON.parse(raw.toString())
        // 客户端可订阅特定课程/事件类型
        if (msg.type === 'subscribe') {
          socket.channel = msg.channel
          app.log.info(`Client subscribed to: ${msg.channel}`)
        }
      } catch { /* ignore */ }
    })

    socket.on('close', () => {
      clearInterval(heartbeat)
      app.log.info('WebSocket client disconnected')
    })
  })
}
```

**推送事件类型**:

| 事件 | 触发时机 | 频道 |
|------|---------|------|
| `attendance_update` | 考勤状态变更 | `course:{courseId}` |
| `course_capacity` | 选课名额变化 | `course:capacity` |
| `selection_open` | 选课开始提醒 | `broadcast` |

### 4.6 请求限流

```javascript
// 在 bff/src/index.js 中注册
import rateLimit from '@fastify/rate-limit'

await app.register(rateLimit, {
  max: 100,           // 每个 IP 每分钟最多 100 次请求
  timeWindow: '1 minute',
  keyGenerator: (request) => {
    return request.ip
  },
  errorResponseBuilder: () => ({
    success: false,
    message: '请求过于频繁，请稍后再试',
  }),
})
```

**登录接口单独限流**（防暴力破解）:

```javascript
// 在 auth.js 中对登录路由单独设置
{
  config: {
    rateLimit: {
      max: 5,
      timeWindow: '1 minute',
    },
  },
}
```

### 4.7 PM2 生产部署配置

```javascript
// bff/ecosystem.config.cjs
module.exports = {
  apps: [{
    name: 'lab-course-bff',
    script: './src/index.js',
    instances: 2,          // cluster 模式 2 实例
    exec_mode: 'cluster',
    env: {
      NODE_ENV: 'production',
      BFF_PORT: 4000,
      BACKEND_URL: 'http://localhost:8080',
    },
    max_memory_restart: '200M',
    error_file: './logs/error.log',
    out_file: './logs/out.log',
    log_date_format: 'YYYY-MM-DD HH:mm:ss',
  }],
}
```

### 4.8 CI/CD 更新

在现有 [attendance-ci.yml](file:///d:/789/.github/workflows/attendance-ci.yml) 中新增 BFF 测试 Job:

```yaml
  bff-tests:
    name: BFF Unit Tests
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: bff/package-lock.json
      - run: npm ci
        working-directory: bff
      - run: npm test
        working-directory: bff
```

### 4.9 第三阶段验收检查清单

- [ ] Redis 连接正常，缓存命中率 > 80%
- [ ] 离线队列持久化到文件，服务重启后队列不丢失
- [ ] 离线队列自动重放成功
- [ ] WebSocket 连接正常，考勤状态实时推送
- [ ] 限流中间件生效（每分钟 >100 请求返回 429）
- [ ] `/health` 端点返回 `{ status: "ok", uptime: ... }`
- [ ] PM2 双实例运行正常
- [ ] 压力测试：500 并发连接无异常

---

## 5. 附录：关键代码清单

### 5.1 向后兼容的降级模式架构

```
┌──────────────────────────────────────────────────────────────────┐
│                      前端 request.js                              │
│                                                                   │
│  if (VITE_BFF_ENABLED) {                                         │
│    withCredentials: true  → Cookie 自动携带                       │
│    tokenManager 不管理 Token  → BFF 端处理                         │
│  } else {                                                         │
│    Authorization Header  → 原来的 localStorage Token               │
│    tokenManager 原逻辑 → 前端自主刷新                              │
│  }                                                                │
└──────────────────────────────────────────────────────────────────┘
```

### 5.2 文件改动总览

| 阶段 | 文件 | 操作 | 行数 |
|------|------|------|------|
| Phase 1 | `bff/` (15+ 文件) | 新增 | ~600 行 |
| Phase 1 | `frontend/vite.config.js` | 修改 | +5 行 |
| Phase 1 | `frontend/.env.development` | 新增 | +1 行 |
| Phase 1 | `frontend/src/utils/tokenManager.js` | 修改 | ±30 行 |
| Phase 1 | `frontend/src/utils/request.js` | 修改 | +5 行 |
| Phase 2 | `bff/src/routes/aggregate/` (3 文件) | 新增 | ~200 行 |
| Phase 2 | `bff/src/services/cacheService.js` | 新增 | ~60 行 |
| Phase 2 | `frontend/src/views/student/StudentCourse.vue` | 修改 | ±20 行 |
| Phase 2 | `frontend/src/views/teacher/TeacherAttendance.vue` | 修改 | ±20 行 |
| Phase 3 | `bff/` 升级 + 新增 | 修改+新增 | ~400 行 |
| Phase 3 | `frontend/src/utils/offlineCheckin.js` | 修改 | ±40 行 |
| Phase 3 | `.github/workflows/attendance-ci.yml` | 修改 | +15 行 |
| **总计** | | | **~1400 行新增/修改** |

### 5.3 启动命令速查

```bash
# ── BFF ──
cd bff
npm install
npm run dev          # 开发模式（node --watch，热重载）

# ── 前端（BFF 模式）──
cd frontend
# .env.development 已设置 VITE_BFF_ENABLED=true
npm run dev

# ── 前端（降级直连）──
VITE_BFF_ENABLED=false npm run dev

# ── 生产部署 ──
cd bff
npm run start        # 单进程
pm2 start ecosystem.config.cjs   # PM2 集群
```

### 5.4 依赖版本锁定的关键建议

- Node.js: **20.x LTS**（20.18+），使用 `.nvmrc` 或 `package.json` 的 `engines` 字段
- 使用 `package-lock.json` 提交到仓库
- 生产部署前运行 `npm audit` 检查安全漏洞
- Fastify 4.x 与 Node.js 20 完全兼容，已验证

---

> **文档维护**: 本方案与 [nodejs-bff-feasibility-report.md](file:///d:/789/nodejs-bff-feasibility-report.md) 配套使用。
> **实施原则**: 渐进式迁移，每阶段独立可部署，保留降级通道，零后端改动。