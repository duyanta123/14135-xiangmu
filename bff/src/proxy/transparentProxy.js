import { config } from '../config.js'
import { jwtVerify } from '../middleware/jwtVerify.js'
import { proxyMapping } from './proxyMapping.js'
import { createLogger } from '../utils/logger.js'

const log = createLogger('PROXY')

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
    preHandler: async (request, reply) => {
      const path = request.url.split('?')[0]

      // 跳过已显式注册的路由（登录、refresh、logout 等）
      if (proxyMapping.public.some(p => path === p)) {
        log.debug('公开路由，跳过认证', {
          requestId: request.id,
          method: request.method,
          path,
        })
        return
      }

      if (proxyMapping.authenticated.some(p => path.startsWith(p))) {
        log.debug('需要认证路由，开始 JWT 验证', {
          requestId: request.id,
          method: request.method,
          path,
        })
        await jwtVerify(request, reply)
      }
    },
    handler: async (request, reply) => {
      const { method, url, body, headers: reqHeaders } = request
      const path = url.split('?')[0]
      const query = url.includes('?') ? url.split('?')[1] : ''

      // 构建转发请求头
      const forwardHeaders = { ...reqHeaders }

      // Security fix (MED-003): 清理客户端X-Forwarded-*头，防止IP欺骗
      delete forwardHeaders['x-forwarded-for']
      delete forwardHeaders['x-forwarded-proto']
      delete forwardHeaders['x-forwarded-host']
      delete forwardHeaders['x-forwarded-port']
      // 设置正确的代理头
      forwardHeaders['x-forwarded-for'] = request.ip
      forwardHeaders['x-forwarded-proto'] = request.protocol

      // 如果 BFF 已验证过 Token，确保 Authorization 头正确传递
      if (request.user) {
        forwardHeaders.authorization = `Bearer ${request.cookies?.[config.jwt.cookieName] || request.headers.authorization?.replace('Bearer ', '')}`
      }

      // 移除仅 BFF 内部使用的头
      delete forwardHeaders.host
      delete forwardHeaders.connection

      const proxyStart = Date.now()

      // 构建请求体日志（脱敏 + 截断）
      const bodyStr = body ? JSON.stringify(body) : null
      const bodyPreview = bodyStr
        ? (bodyStr.length > 500 ? bodyStr.substring(0, 500) + '...(truncated)' : bodyStr)
        : '(empty)'

      log.info('代理转发开始', {
        requestId: request.id,
        method,
        path,
        query: query || '(none)',
        authenticated: !!request.user,
        userId: request.user?.userId || 'N/A',
        contentType: reqHeaders['content-type'] || 'N/A',
        bodySize: bodyStr ? bodyStr.length : 0,
        bodyPreview,
      })

      try {
        const response = await fetch(`${config.backendUrl}${url}`, {
          method,
          headers: forwardHeaders,
          ...(body && Object.keys(body).length > 0
            ? { body: JSON.stringify(body) }
            : {}),
        })

        const proxyDuration = Date.now() - proxyStart

        // 透传状态码
        reply.code(response.status)

        // 透传响应头
        response.headers.forEach((value, key) => {
          if (!['transfer-encoding', 'connection'].includes(key.toLowerCase())) {
            reply.header(key, value)
          }
        })

        log.info('代理转发完成', {
          requestId: request.id,
          method,
          path,
          backendStatus: response.status,
          duration: `${proxyDuration}ms`,
        })

        // 特殊处理非 JSON 响应（文件导出）
        const contentType = response.headers.get('content-type') || ''
        if (contentType.includes('application/vnd.openxmlformats')
          || contentType.includes('application/octet-stream')) {
          log.debug('代理转发：二进制响应', {
            requestId: request.id,
            contentType,
          })
          const buffer = await response.arrayBuffer()
          return reply.send(Buffer.from(buffer))
        }

        const jsonResponse = await response.json()

        // 记录后端返回的非成功响应（含响应体摘要）
        if (!jsonResponse.success && response.status >= 400) {
          const respPreview = JSON.stringify(jsonResponse).substring(0, 300)
          log.warn('代理转发：后端返回错误', {
            requestId: request.id,
            path,
            backendStatus: response.status,
            message: jsonResponse.message || 'unknown',
            responsePreview: respPreview,
          })
        }

        return jsonResponse
      } catch (err) {
        const proxyDuration = Date.now() - proxyStart
        log.error('代理转发失败', {
          requestId: request.id,
          method,
          path,
          backendUrl: config.backendUrl,
          error: err.message,
          duration: `${proxyDuration}ms`,
        })
        reply.code(502)
        return { success: false, message: '后端服务不可达' }
      }
    },
  })
}