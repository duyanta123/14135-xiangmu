import { config } from '../config.js'
import { createLogger, maskSensitive } from '../utils/logger.js'

const log = createLogger('BACKEND')

class BackendClient {
  async request(method, path, options = {}) {
    const url = `${config.backendUrl}${path}`
    const headers = {
      'Content-Type': 'application/json',
      ...(options.headers || {}),
    }

    const fetchOptions = {
      method,
      headers,
      ...(options.body ? { body: JSON.stringify(options.body) } : {}),
    }

    const start = Date.now()

    log.debug('后端请求发送', {
      method,
      path,
      url,
      bodySize: options.body ? JSON.stringify(options.body).length : 0,
      hasAuth: !!headers.authorization,
    })

    const response = await fetch(url, fetchOptions)
    const duration = Date.now() - start

    // 透传非 JSON 响应（文件导出等）
    const contentType = response.headers.get('content-type') || ''
    if (!contentType.includes('application/json')) {
      log.debug('后端返回非 JSON 响应', {
        method,
        path,
        status: response.status,
        contentType,
        duration: `${duration}ms`,
      })
      return response
    }

    const jsonResponse = await response.json()

    log.debug('后端响应接收', {
      method,
      path,
      status: response.status,
      duration: `${duration}ms`,
      success: jsonResponse.success,
      message: jsonResponse.message || '(none)',
    })

    if (!jsonResponse.success && response.status >= 400) {
      log.warn('后端返回错误响应', {
        method,
        path,
        status: response.status,
        message: jsonResponse.message || 'unknown',
        duration: `${duration}ms`,
      })
    }

    return jsonResponse
  }

  get(path, options) { return this.request('GET', path, options) }
  post(path, body, options) { return this.request('POST', path, { ...options, body }) }
  put(path, body, options) { return this.request('PUT', path, { ...options, body }) }
  delete(path, options) { return this.request('DELETE', path, options) }
}

export const backendClient = new BackendClient()