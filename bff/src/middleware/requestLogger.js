import { createLogger } from '../utils/logger.js'

const log = createLogger('REQ')

/**
 * 请求日志中间件
 * 记录每个请求的方法、路径、耗时、状态码
 */
export async function requestLogger(request, reply) {
  const start = Date.now()

  log.info('请求进入', {
    requestId: request.id,
    method: request.method,
    url: request.url,
    ip: request.ip,
  })

  reply.raw.on('finish', () => {
    const duration = Date.now() - start
    const level = reply.statusCode >= 400 ? 'WARN' : 'INFO'

    const logData = {
      requestId: request.id,
      method: request.method,
      url: request.url,
      statusCode: reply.statusCode,
      duration: `${duration}ms`,
    }

    if (level === 'WARN') {
      log.warn(`请求完成 ${reply.statusCode}`, logData)
    } else {
      log.info(`请求完成 ${reply.statusCode}`, logData)
    }
  })
}