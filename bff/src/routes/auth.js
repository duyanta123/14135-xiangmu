import { config } from '../config.js'
import { backendClient } from '../services/backendClient.js'
import { jwtVerify } from '../middleware/jwtVerify.js'
import { createLogger, maskSensitive } from '../utils/logger.js'

const log = createLogger('AUTH')

export async function setupAuthRoutes(app) {
  /**
   * 登录处理工厂函数
   * 代理登录请求到后端，成功后签发双Token HttpOnly Cookie
   * Security fix (HIGH-001): Access Token (30min) + Refresh Token (7d) 分开存储
   * @param {string} targetPath - 后端目标路径
   * @param {string} [usernameField='username'] - 请求体中用户名字段名
   * @param {string} [roleName='unknown'] - 角色标识
   */
  const createLoginHandler = (targetPath, usernameField = 'username', roleName = 'unknown') => {
    return async (request, reply) => {
      const body = request.body || {}
      const username = body[usernameField]
      const { password } = body

      log.info('登录请求', {
        requestId: request.id,
        path: targetPath,
        usernameField,
        username: username || '(empty)',
        ip: request.ip,
      })

      if (!username || !password) {
        log.warn('登录请求：缺少参数', {
          requestId: request.id,
          path: targetPath,
          usernameField,
          hasUsername: !!username,
          hasPassword: !!password,
        })
        reply.code(400)
        return { success: false, message: '请提供用户名和密码' }
      }

      try {
        const response = await backendClient.post(targetPath, request.body)

        if (response.success && response.accessToken && response.refreshToken) {
          // 签发 Access Token Cookie（短期，30分钟）
          const isSecure = config.nodeEnv === 'production'
          reply.setCookie(config.jwt.accessTokenCookieName, response.accessToken, {
            httpOnly: true,
            secure: isSecure,
            sameSite: 'lax',
            path: '/',
            maxAge: config.jwt.accessTokenMaxAge / 1000,
          })

          // 签发 Refresh Token Cookie（长期，7天）
          reply.setCookie(config.jwt.refreshTokenCookieName, response.refreshToken, {
            httpOnly: true,
            secure: isSecure,
            sameSite: 'lax',
            path: '/api/auth', // 仅刷新端点可访问
            maxAge: config.jwt.refreshTokenMaxAge / 1000,
          })

          const userData = response.data || {}
          const userId = userData.id || 'unknown'
          const displayName = userData.name || username || 'unknown'

          log.info('登录成功（双Token模式）', {
            requestId: request.id,
            path: targetPath,
            username: username || '(unknown)',
            userId,
            role: roleName,
            displayName,
          })

          return {
            success: true,
            message: response.message || '登录成功',
            data: {
              ...userData,
              userId,
              username: username || userData.username || userData.studentNo || userData.teacherNo || 'unknown',
              role: roleName,
              tokenExpireTime: Date.now() + config.jwt.accessTokenMaxAge,
            },
          }
        }

        // 兼容旧版单Token响应
        if (response.success && response.token) {
          const isSecure = config.nodeEnv === 'production'
          reply.setCookie(config.jwt.cookieName, response.token, {
            httpOnly: true,
            secure: isSecure,
            sameSite: 'lax',
            path: '/',
            maxAge: config.jwt.cookieMaxAge / 1000,
          })

          const userData = response.data || {}
          return {
            success: true,
            message: response.message || '登录成功',
            data: {
              ...userData,
              role: roleName,
              tokenExpireTime: Date.now() + config.jwt.expiration,
            },
          }
        }

        log.warn('登录失败', {
          requestId: request.id,
          path: targetPath,
          username: username || '(unknown)',
          reason: response.message || '认证失败',
          ip: request.ip,
        })

        reply.code(401)
        return response
      } catch (err) {
        log.error('登录请求异常', {
          requestId: request.id,
          path: targetPath,
          error: err.message,
          ip: request.ip,
        })
        reply.code(502)
        return { success: false, message: '后端服务不可达，请稍后重试' }
      }
    }
  }

  // 注册三个登录路由
  app.post('/api/student/login', createLoginHandler('/api/student/login', 'studentNo', 'student'))
  app.post('/api/teacher/login', createLoginHandler('/api/teacher/login', 'teacherNo', 'teacher'))
  app.post('/api/admin/login', createLoginHandler('/api/admin/login', 'username', 'admin'))

  /**
   * POST /api/auth/refresh
   * Token 刷新 — 读取 refreshToken Cookie，透传到后端进行轮转刷新
   * Security fix (HIGH-001): 代理刷新到后端实现Token轮转
   */
  app.post('/api/auth/refresh', async (request, reply) => {
    const refreshToken = request.cookies?.[config.jwt.refreshTokenCookieName]

    if (!refreshToken) {
      log.warn('Token刷新请求：缺少RefreshToken Cookie', { requestId: request.id })
      reply.code(401)
      return { success: false, message: '未找到RefreshToken，请重新登录' }
    }

    log.info('Token刷新请求', { requestId: request.id })

    try {
      // 透传到后端执行Token轮转刷新
      const response = await backendClient.post('/api/auth/refresh', { refreshToken })

      if (response.success && response.accessToken && response.refreshToken) {
        const isSecure = config.nodeEnv === 'production'

        // 更新 Access Token Cookie
        reply.setCookie(config.jwt.accessTokenCookieName, response.accessToken, {
          httpOnly: true,
          secure: isSecure,
          sameSite: 'lax',
          path: '/',
          maxAge: config.jwt.accessTokenMaxAge / 1000,
        })

        // 更新 Refresh Token Cookie（轮转）
        reply.setCookie(config.jwt.refreshTokenCookieName, response.refreshToken, {
          httpOnly: true,
          secure: isSecure,
          sameSite: 'lax',
          path: '/api/auth',
          maxAge: config.jwt.refreshTokenMaxAge / 1000,
        })

        log.info('Token刷新成功（轮转）', { requestId: request.id })

        return {
          success: true,
          message: 'Token已刷新',
          expiresIn: Math.floor(config.jwt.accessTokenMaxAge / 1000),
        }
      }

      log.warn('Token刷新失败：后端拒绝', {
        requestId: request.id,
        reason: response.message || '未知',
      })
      reply.code(401)
      return { success: false, message: response.message || 'Token刷新失败，请重新登录' }
    } catch (err) {
      log.error('Token刷新异常', {
        requestId: request.id,
        error: err.message,
      })
      reply.code(502)
      return { success: false, message: 'Token刷新服务不可用' }
    }
  })

  /**
   * POST /api/auth/logout
   * 清除双Token Cookie，调用后端登出
   */
  app.post('/api/auth/logout', async (request, reply) => {
    log.info('用户登出', { requestId: request.id })

    // 清除两个Cookie
    reply.clearCookie(config.jwt.accessTokenCookieName, { path: '/' })
    reply.clearCookie(config.jwt.refreshTokenCookieName, { path: '/api/auth' })
    reply.clearCookie(config.jwt.cookieName, { path: '/' }) // 兼容旧版

    // 通知后端清除refreshToken
    try {
      const authHeader = request.headers.authorization
      await backendClient.post('/api/auth/logout', {}, authHeader ? { Authorization: authHeader } : {})
    } catch (e) {
      // 后端登出失败不影响前端清除Cookie
      log.warn('后端登出通知失败', { requestId: request.id, error: e.message })
    }

    return { success: true, message: '已退出登录' }
  })
}