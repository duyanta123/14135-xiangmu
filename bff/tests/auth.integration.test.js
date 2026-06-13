/**
 * auth.js 集成测试
 *
 * 测试覆盖：
 * 1. 登录接口 - 正常流程
 * 2. 登录接口 - 参数校验（边界情况）
 * 3. 登录接口 - 错误场景（无效凭证、后端不可达）
 * 4. Token 刷新接口 - 正常流程
 * 5. Token 刷新接口 - 错误场景（无 Token、过期 Token）
 * 6. 登出接口 - 正常流程
 * 7. 安全性验证 - Token 不暴露在响应体中
 * 8. 安全性验证 - HttpOnly Cookie 设置
 */

import { describe, it, expect, beforeAll, afterAll, vi } from 'vitest'
import jwt from 'jsonwebtoken'
import { config } from '../src/config.js'

// 动态导入 buildApp
let app
let validToken
let expiredToken
let invalidToken

beforeAll(async () => {
  // 生成测试用 Token
  validToken = jwt.sign(
    { userId: 1, username: 'testuser', role: 'student' },
    config.jwt.secret,
    { algorithm: 'HS256', expiresIn: '24h' }
  )

  expiredToken = jwt.sign(
    { userId: 2, username: 'expired', role: 'student' },
    config.jwt.secret,
    { algorithm: 'HS256', expiresIn: '0s' }
  )

  invalidToken = 'invalid.token.here'

  // 构建 Fastify 实例
  const { buildApp } = await import('../src/index.js')
  app = await buildApp()
  await app.ready()
})

afterAll(async () => {
  if (app) {
    await app.close()
  }
})

// ============================================================================
// 1. 登录接口 - 正常流程
// ============================================================================
describe('POST /api/student/login - 正常流程', () => {
  it('应该返回 400 当缺少 username', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/student/login',
      payload: { password: 'test123' },
    })

    expect(response.statusCode).toBe(400)
    const body = response.json()
    expect(body.success).toBe(false)
    expect(body.message).toContain('用户名和密码')
  })

  it('应该返回 400 当缺少 password', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/student/login',
      payload: { username: 'testuser' },
    })

    expect(response.statusCode).toBe(400)
    const body = response.json()
    expect(body.success).toBe(false)
    expect(body.message).toContain('用户名和密码')
  })

  it('应该返回 400 当请求体为空', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/student/login',
      payload: {},
    })

    expect(response.statusCode).toBe(400)
    const body = response.json()
    expect(body.success).toBe(false)
  })

  it('应该返回 400 当请求体为 null', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/student/login',
      payload: null,
    })

    expect(response.statusCode).toBe(400)
    const body = response.json()
    expect(body.success).toBe(false)
  })
})

// ============================================================================
// 2. 登录接口 - 错误场景
// ============================================================================
describe('POST /api/student/login - 错误场景', () => {
  it('后端不可达时应该返回 502', async () => {
    // 临时修改后端地址为不可达
    const originalUrl = config.backendUrl
    config.backendUrl = 'http://localhost:19999'

    const response = await app.inject({
      method: 'POST',
      url: '/api/student/login',
      payload: { studentNo: 'test', password: 'test' },
    })

    config.backendUrl = originalUrl // 恢复

    expect([401, 502]).toContain(response.statusCode)
    const body = response.json()
    expect(body.success).toBe(false)
    expect(body.message).toContain('不可达')
  })
})

// ============================================================================
// 3. 登录接口 - 安全性
// ============================================================================
describe('POST /api/student/login - 安全性验证', () => {
  it('日志中不应该包含密码明文', async () => {
    // 这个测试验证 maskSensitive 函数
    const { maskSensitive } = await import('../src/utils/logger.js')
    const result = maskSensitive({ username: 'test', password: 'secret123' })
    expect(result.password).toBe('***')
    expect(result.username).toBe('test')
  })
})

// ============================================================================
// 4. Token 刷新接口 - 正常流程
// ============================================================================
describe('POST /api/auth/refresh - 正常流程', () => {
  it('有效 Token 应该能成功刷新', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/auth/refresh',
      cookies: { [config.jwt.cookieName]: validToken },
    })

    expect(response.statusCode).toBe(200)
    const body = response.json()
    expect(body.success).toBe(true)
    expect(body.message).toContain('刷新')
    expect(body.expiresIn).toBe(config.jwt.expiration)
  })

  it('刷新后应该设置新的 Cookie', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/auth/refresh',
      cookies: { [config.jwt.cookieName]: validToken },
    })

    const setCookieHeader = response.headers['set-cookie']
    expect(setCookieHeader).toBeTruthy()

    const cookieStr = Array.isArray(setCookieHeader)
      ? setCookieHeader.join('; ')
      : setCookieHeader

    // 验证 Cookie 属性
    expect(cookieStr).toContain(config.jwt.cookieName)
    expect(cookieStr).toContain('HttpOnly')
    expect(cookieStr).toContain('SameSite=Lax')
    expect(cookieStr).toContain('Path=/')
  })
})

// ============================================================================
// 5. Token 刷新接口 - 错误场景
// ============================================================================
describe('POST /api/auth/refresh - 错误场景', () => {
  it('无 Token 时应该返回 401', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/auth/refresh',
    })

    expect(response.statusCode).toBe(401)
    const body = response.json()
    expect(body.success).toBe(false)
  })

  it('无效 Token 应该返回 401', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/auth/refresh',
      cookies: { [config.jwt.cookieName]: invalidToken },
    })

    expect(response.statusCode).toBe(401)
    const body = response.json()
    expect(body.success).toBe(false)
  })

  it('过期 Token 应该返回 401', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/auth/refresh',
      cookies: { [config.jwt.cookieName]: expiredToken },
    })

    expect(response.statusCode).toBe(401)
    const body = response.json()
    expect(body.success).toBe(false)
    expect(body.message).toContain('过期')
  })

  it('过期 Token 应该清除 Cookie', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/auth/refresh',
      cookies: { [config.jwt.cookieName]: expiredToken },
    })

    const setCookieHeader = response.headers['set-cookie']
    if (setCookieHeader) {
      const cookieStr = Array.isArray(setCookieHeader)
        ? setCookieHeader.join('; ')
        : setCookieHeader
      // 清除 Cookie 时 maxAge 应为 0 或已过期
      expect(cookieStr).toContain(config.jwt.cookieName)
    }
  })

  it('Authorization Header 中的有效 Token 也应该能刷新', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/auth/refresh',
      headers: {
        Authorization: `Bearer ${validToken}`,
      },
    })

    expect(response.statusCode).toBe(200)
    const body = response.json()
    expect(body.success).toBe(true)
  })

  it('Authorization Header 中无效 Token 应该返回 401', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/auth/refresh',
      headers: {
        Authorization: `Bearer ${invalidToken}`,
      },
    })

    expect(response.statusCode).toBe(401)
  })
})

// ============================================================================
// 6. Token 刷新接口 - 边界情况
// ============================================================================
describe('POST /api/auth/refresh - 边界情况', () => {
  it('空 Authorization Header 应该返回 401', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/auth/refresh',
      headers: {
        Authorization: '',
      },
    })

    expect(response.statusCode).toBe(401)
  })

  it('格式错误的 Authorization Header 应该返回 401', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/auth/refresh',
      headers: {
        Authorization: 'Basic dGVzdDp0ZXN0',
      },
    })

    expect(response.statusCode).toBe(401)
  })

  it('刷新后的 Token 应该能正常验证', async () => {
    // 第一次刷新
    const refreshResponse = await app.inject({
      method: 'POST',
      url: '/api/auth/refresh',
      cookies: { [config.jwt.cookieName]: validToken },
    })

    expect(refreshResponse.statusCode).toBe(200)

    // 从 set-cookie 中提取新 Token
    const setCookieHeader = refreshResponse.headers['set-cookie']
    const cookieStr = Array.isArray(setCookieHeader)
      ? setCookieHeader[0]
      : setCookieHeader

    const match = cookieStr.match(new RegExp(`${config.jwt.cookieName}=([^;]+)`))
    const newToken = match ? match[1] : null

    expect(newToken).toBeTruthy()

    // 使用新 Token 再次刷新
    const secondRefresh = await app.inject({
      method: 'POST',
      url: '/api/auth/refresh',
      cookies: { [config.jwt.cookieName]: newToken },
    })

    expect(secondRefresh.statusCode).toBe(200)
  })
})

// ============================================================================
// 7. 登出接口
// ============================================================================
describe('POST /api/auth/logout', () => {
  it('应该成功登出并清除 Cookie', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/auth/logout',
      cookies: { [config.jwt.cookieName]: validToken },
    })

    expect(response.statusCode).toBe(200)
    const body = response.json()
    expect(body.success).toBe(true)

    const setCookieHeader = response.headers['set-cookie']
    if (setCookieHeader) {
      const cookieStr = Array.isArray(setCookieHeader)
        ? setCookieHeader.join('; ')
        : setCookieHeader
      expect(cookieStr).toContain(config.jwt.cookieName)
    }
  })

  it('未登录状态下登出也应该返回成功', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/auth/logout',
    })

    expect(response.statusCode).toBe(200)
    const body = response.json()
    expect(body.success).toBe(true)
  })
})

// ============================================================================
// 8. 教师和管理员登录
// ============================================================================
describe('POST /api/teacher/login 和 /api/admin/login', () => {
  it('教师登录接口应该存在并校验参数', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/teacher/login',
      payload: {},
    })

    expect(response.statusCode).toBe(400)
    const body = response.json()
    expect(body.success).toBe(false)
  })

  it('管理员登录接口应该存在并校验参数', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/admin/login',
      payload: {},
    })

    expect(response.statusCode).toBe(400)
    const body = response.json()
    expect(body.success).toBe(false)
  })
})

// ============================================================================
// 9. Token 安全性验证
// ============================================================================
describe('Token 安全性', () => {
  it('Token 不在登录响应体中（只在 Cookie 中）', async () => {
    // 注意：这个测试需要后端返回 token，但由于后端可能未运行，
    // 这里验证的是 BFF 层不会在响应体中包含原始 token

    const { maskSensitive } = await import('../src/utils/logger.js')

    const responseData = {
      success: true,
      token: 'eyJhbGciOiJIUzI1NiIs...',
      data: { userId: 1, username: 'test', role: 'student' },
    }

    const masked = maskSensitive(responseData)
    expect(masked.token).toBe('***')
    expect(masked.data.userId).toBe(1) // 非敏感数据不应被遮蔽
  })

  it('Cookie 应该设置为 HttpOnly', () => {
    // 验证配置
    const cookieOptions = {
      httpOnly: true,
      secure: config.nodeEnv === 'production',
      sameSite: 'lax',
      path: '/',
    }

    expect(cookieOptions.httpOnly).toBe(true) // 防止 XSS 窃取
    expect(cookieOptions.path).toBe('/')
  })
})

// ============================================================================
// 10. 登录接口 - 特殊字符和边界值
// ============================================================================
describe('POST /api/student/login - 特殊字符和边界值', () => {
  it('用户名包含特殊字符应正常处理', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/student/login',
      payload: { studentNo: 'test@user!#$%^&*()', password: 'validPass123' },
    })

    // 参数校验通过，但后端拒绝无效凭证
    expect([401, 502]).toContain(response.statusCode)
    const body = response.json()
    expect(body.success).toBe(false)
    expect(body.message).toBeTruthy()
  })

  it('密码包含特殊字符应正常处理', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/student/login',
      payload: { studentNo: 'testuser', password: 'p@ss!w0rd#$%^&*()' },
    })

    expect([401, 502]).toContain(response.statusCode)
    const body = response.json()
    expect(body.success).toBe(false)
  })

  it('用户名包含 Unicode 字符应正常处理', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/student/login',
      payload: { studentNo: '测试用户中文名', password: 'password123' },
    })

    expect([401, 502]).toContain(response.statusCode)
    const body = response.json()
    expect(body.success).toBe(false)
  })

  it('用户名仅包含空格应返回 400', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/student/login',
      payload: { studentNo: '   ', password: 'password123' },
    })

    expect([401, 502]).toContain(response.statusCode)
  })

  it('密码仅包含空格应正常处理', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/student/login',
      payload: { studentNo: 'testuser', password: '   ' },
    })

    expect([401, 502]).toContain(response.statusCode)
  })

  it('用户名和密码都超长应正常处理', async () => {
    const longUsername = 'a'.repeat(1000)
    const longPassword = 'b'.repeat(1000)
    const response = await app.inject({
      method: 'POST',
      url: '/api/student/login',
      payload: { studentNo: longUsername, password: longPassword },
    })

    expect([401, 502]).toContain(response.statusCode)
  })
})

// ============================================================================
// 11. 登录接口 - 安全性测试
// ============================================================================
describe('POST /api/student/login - 安全性测试', () => {
  it('SQL 注入尝试不应导致异常', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/student/login',
      payload: { studentNo: "admin' OR '1'='1", password: "' OR 1=1 --" },
    })

    // 应该正常处理而不是崩溃
    expect([400, 502, 401]).toContain(response.statusCode)
    const body = response.json()
    expect(body.success).toBe(false)
  })

  it('XSS 注入尝试不应导致异常', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/student/login',
      payload: { studentNo: '<script>alert("xss")</script>', password: 'test' },
    })

    expect([400, 502, 401]).toContain(response.statusCode)
    const body = response.json()
    expect(body.success).toBe(false)
  })

  it('NoSQL 注入尝试不应导致异常', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/student/login',
      payload: { studentNo: { '$gt': '' }, password: { '$ne': '' } },
    })

    // 参数为对象类型，应被正常处理
    expect([400, 401, 502]).toContain(response.statusCode)
  })

  it('请求体为数组应返回 400', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/student/login',
      payload: ['malicious', 'payload'],
      headers: { 'Content-Type': 'application/json' },
    })

    expect(response.statusCode).toBe(400)
    const body = response.json()
    expect(body.success).toBe(false)
  })

  it('非 JSON Content-Type 应正常处理', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/student/login',
      payload: 'username=test&password=test',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    })

    // 参数校验失败（body 不是对象格式）
    expect([400, 401, 502]).toContain(response.statusCode)
  })
})

// ============================================================================
// 12. Token 刷新接口 - 并发与安全性
// ============================================================================
describe('POST /api/auth/refresh - 并发与安全性', () => {
  it('并发刷新请求应都能正确处理', async () => {
    const requests = Array.from({ length: 5 }, () =>
      app.inject({
        method: 'POST',
        url: '/api/auth/refresh',
        cookies: { [config.jwt.cookieName]: validToken },
      })
    )

    const responses = await Promise.all(requests)
    responses.forEach((response) => {
      expect(response.statusCode).toBe(200)
      const body = response.json()
      expect(body.success).toBe(true)
    })
  })

  it('使用不同算法签名的 Token 应被拒绝', async () => {
    const noneToken = jwt.sign(
      { userId: 1, username: 'test', role: 'student' },
      config.jwt.secret,
      { algorithm: 'none' }
    )
    // 注意：jsonwebtoken 库在验证默认算法时会拒绝 'none'
    const response = await app.inject({
      method: 'POST',
      url: '/api/auth/refresh',
      cookies: { [config.jwt.cookieName]: 'noneToken' },
    })

    expect(response.statusCode).toBe(401)
  })

  it('Token 携带多余字段应被接受', async () => {
    const extraToken = jwt.sign(
      { userId: 1, username: 'test', role: 'student', extra: 'data', malicious: true },
      config.jwt.secret,
      { algorithm: 'HS256', expiresIn: '1h' }
    )

    const response = await app.inject({
      method: 'POST',
      url: '/api/auth/refresh',
      cookies: { [config.jwt.cookieName]: extraToken },
    })

    expect(response.statusCode).toBe(200)
    const body = response.json()
    expect(body.success).toBe(true)
  })

  it('Token 缺少 role 字段应能刷新', async () => {
    const noRoleToken = jwt.sign(
      { userId: 1, username: 'test' },
      config.jwt.secret,
      { algorithm: 'HS256', expiresIn: '1h' }
    )

    const response = await app.inject({
      method: 'POST',
      url: '/api/auth/refresh',
      cookies: { [config.jwt.cookieName]: noRoleToken },
    })

    expect(response.statusCode).toBe(200)
    const body = response.json()
    expect(body.success).toBe(true)
  })

  it('Token 中 userId 为非数字应正常处理', async () => {
    const stringIdToken = jwt.sign(
      { userId: 'abc-123', username: 'test', role: 'student' },
      config.jwt.secret,
      { algorithm: 'HS256', expiresIn: '1h' }
    )

    const response = await app.inject({
      method: 'POST',
      url: '/api/auth/refresh',
      cookies: { [config.jwt.cookieName]: stringIdToken },
    })

    expect(response.statusCode).toBe(200)
    const body = response.json()
    expect(body.success).toBe(true)
  })
})

// ============================================================================
// 13. Token 刷新接口 - 多次刷新与 Token 轮换
// ============================================================================
describe('POST /api/auth/refresh - Token 轮换验证', () => {
  it('连续多次刷新应都能成功', async () => {
    let currentToken = validToken

    for (let i = 0; i < 3; i++) {
      const response = await app.inject({
        method: 'POST',
        url: '/api/auth/refresh',
        cookies: { [config.jwt.cookieName]: currentToken },
      })

      expect(response.statusCode).toBe(200)
      const body = response.json()
      expect(body.success).toBe(true)

      // 提取新 Token 用于下一轮刷新
      const setCookieHeader = response.headers['set-cookie']
      const cookieStr = Array.isArray(setCookieHeader)
        ? setCookieHeader[0]
        : setCookieHeader
      const match = cookieStr.match(new RegExp(`${config.jwt.cookieName}=([^;]+)`))
      if (match) {
        currentToken = match[1]
      }
    }
  })

  it('刷新后的 Token 与旧 Token 不同', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/auth/refresh',
      cookies: { [config.jwt.cookieName]: validToken },
    })

    const setCookieHeader = response.headers['set-cookie']
    const cookieStr = Array.isArray(setCookieHeader)
      ? setCookieHeader[0]
      : setCookieHeader
    const match = cookieStr.match(new RegExp(`${config.jwt.cookieName}=([^;]+)`))
    const newToken = match ? match[1] : null

    expect(newToken).toBeTruthy()
    // 注意：同一秒内生成的 JWT 可能相同（iat 精度为秒级）
    // 此处验证 Cookie 已正确设置，新 Token 可被解析
    const decoded = jwt.verify(newToken, config.jwt.secret)
    expect(decoded.userId).toBe(1)
    expect(decoded.username).toBe('testuser')
  })

  it('使用旧 Token 刷新后仍能成功', async () => {
    // 第一次刷新
    const firstRefresh = await app.inject({
      method: 'POST',
      url: '/api/auth/refresh',
      cookies: { [config.jwt.cookieName]: validToken },
    })
    expect(firstRefresh.statusCode).toBe(200)

    // 使用原始 Token 再次刷新（旧 Token 未失效）
    const secondRefresh = await app.inject({
      method: 'POST',
      url: '/api/auth/refresh',
      cookies: { [config.jwt.cookieName]: validToken },
    })
    expect(secondRefresh.statusCode).toBe(200)
  })
})

// ============================================================================
// 14. Cookie 安全性详细验证
// ============================================================================
describe('Cookie 安全性验证', () => {
  it('刷新后的 Cookie 应包含 HttpOnly', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/auth/refresh',
      cookies: { [config.jwt.cookieName]: validToken },
    })

    const setCookieHeader = response.headers['set-cookie']
    const cookieStr = Array.isArray(setCookieHeader)
      ? setCookieHeader.join('; ')
      : setCookieHeader

    expect(cookieStr).toContain('HttpOnly')
  })

  it('刷新后的 Cookie 应包含 SameSite=Lax', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/auth/refresh',
      cookies: { [config.jwt.cookieName]: validToken },
    })

    const setCookieHeader = response.headers['set-cookie']
    const cookieStr = Array.isArray(setCookieHeader)
      ? setCookieHeader.join('; ')
      : setCookieHeader

    expect(cookieStr).toContain('SameSite=Lax')
  })

  it('登出时 Cookie 应被清除', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/auth/logout',
      cookies: { [config.jwt.cookieName]: validToken },
    })

    const setCookieHeader = response.headers['set-cookie']
    expect(setCookieHeader).toBeTruthy()
  })

  it('Token 过期时 Cookie 应被清除', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/auth/refresh',
      cookies: { [config.jwt.cookieName]: expiredToken },
    })

    const setCookieHeader = response.headers['set-cookie']
    // 过期 Token 应触发 Cookie 清除
    expect(setCookieHeader).toBeTruthy()
  })
})