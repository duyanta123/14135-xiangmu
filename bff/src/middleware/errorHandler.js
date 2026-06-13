import { config } from '../config.js'

/**
 * 全局错误处理中间件
 */
export function errorHandler(error, request, reply) {
  const statusCode = error.statusCode || reply.statusCode || 500

  request.log.error(
    {
      err: error,
      method: request.method,
      url: request.url,
    },
    `Error: ${error.message}`
  )

  // 如果响应已经发送，不重复发送
  if (reply.sent) return

  reply.code(statusCode).send({
    success: false,
    message: statusCode === 500 && config.nodeEnv === 'production'
      ? '服务器内部错误'
      : error.message,
  })
}