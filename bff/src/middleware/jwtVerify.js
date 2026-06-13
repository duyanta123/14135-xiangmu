import jwt from 'jsonwebtoken'
import { config } from '../config.js'
import { createLogger } from '../utils/logger.js'

const log = createLogger('JWT')

/**
 * JWT 验证中间件
 * 优先从 HttpOnly Cookie 读取 Token，fallback 到 Authorization Header
 * 验证通过后将用户信息注入 request.user
 *
 * 注意：验证失败时直接调用 reply.send() 终止请求链
 */
export async function jwtVerify(request, reply) {
  const requestId = request.id

  // 1. 从 Cookie 获取 Token
  let token = request.cookies?.[config.jwt.cookieName]
  const tokenSource = token ? 'Cookie' : null

  // 2. Fallback：从 Authorization Header 获取（向前兼容）
  if (!token) {
    const authHeader = request.headers.authorization
    if (authHeader?.startsWith('Bearer ')) {
      token = authHeader.slice(7)
    }
  }

  const finalTokenSource = tokenSource || (token ? 'Authorization Header' : 'none')

  log.debug('JWT 验证开始', {
    requestId,
    method: request.method,
    url: request.url,
    tokenSource: finalTokenSource,
    hasToken: !!token,
  })

  if (!token) {
    log.warn('JWT 验证失败：未提供认证信息', {
      requestId,
      method: request.method,
      url: request.url,
      ip: request.ip,
    })
    reply.code(401).send({
      success: false,
      message: '未提供认证信息',
    })
    return
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

    const remainingTtl = decoded.exp
      ? Math.floor(decoded.exp - Date.now() / 1000)
      : 'unknown'

    log.info('JWT 验证成功', {
      requestId,
      userId: request.user.userId,
      username: request.user.username,
      role: request.user.role,
      url: request.url,
      tokenSource: finalTokenSource,
      tokenIssuedAt: decoded.iat ? new Date(decoded.iat * 1000).toISOString() : 'unknown',
      tokenExpiresAt: decoded.exp ? new Date(decoded.exp * 1000).toISOString() : 'unknown',
      remainingTtl: typeof remainingTtl === 'number' ? `${remainingTtl}s` : remainingTtl,
    })
  } catch (err) {
    if (err.name === 'TokenExpiredError') {
      const now = new Date().toISOString()
      log.warn('JWT 验证失败：Token 已过期', {
        requestId,
        error: err.name,
        expiredAt: err.expiredAt,
        currentTime: now,
        url: request.url,
        ip: request.ip,
      })
      reply.clearCookie(config.jwt.cookieName)
      reply.code(401).send({
        success: false,
        message: 'Token 已过期，请重新登录',
      })
      return
    }

    if (err.name === 'JsonWebTokenError') {
      log.error('JWT 验证失败：Token 无效', {
        requestId,
        error: err.name,
        message: err.message,
        url: request.url,
        ip: request.ip,
        tokenSource: finalTokenSource,
        tokenPreview: `${token.substring(0, 10)}...${token.substring(token.length - 5)}`,
      })
    } else {
      log.error('JWT 验证失败：未知错误', {
        requestId,
        error: err.name,
        message: err.message,
        url: request.url,
        ip: request.ip,
        stack: err.stack,
      })
    }

    reply.code(401).send({
      success: false,
      message: 'Token 无效',
    })
  }
}

