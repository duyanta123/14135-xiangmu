/**
 * 统一日志工具
 * 提供带时间戳和模块标识的结构化日志输出
 */

const MODULE_PREFIX = 'BFF'

/**
 * 生成 ISO 格式时间戳
 */
function timestamp() {
  return new Date().toISOString()
}

/**
 * 格式化日志消息
 * @param {string} module - 模块标识（如 JWT, PROXY, AUTH）
 * @param {string} level - 日志级别
 * @param {string} message - 日志消息
 * @param {object} [data] - 附加数据
 */
function format(module, level, message, data) {
  const ts = timestamp()
  const prefix = `[${ts}] [${MODULE_PREFIX}:${module}] [${level}]`

  if (data) {
    const dataStr = Object.entries(data)
      .map(([k, v]) => {
        if (typeof v === 'object') {
          return `${k}=${JSON.stringify(v)}`
        }
        return `${k}=${v}`
      })
      .join(' | ')
    return `${prefix} ${message} | ${dataStr}`
  }

  return `${prefix} ${message}`
}

/**
 * 创建模块日志器
 * @param {string} module - 模块标识
 */
export function createLogger(module) {
  return {
    info(message, data) {
      console.log(format(module, 'INFO', message, data))
    },
    warn(message, data) {
      console.warn(format(module, 'WARN', message, data))
    },
    error(message, data) {
      console.error(format(module, 'ERROR', message, data))
    },
    debug(message, data) {
      if (process.env.LOG_LEVEL === 'debug') {
        console.debug(format(module, 'DEBUG', message, data))
      }
    },
  }
}

/**
 * 脱敏处理：隐藏敏感信息
 */
export function maskSensitive(obj, keys = ['password', 'token', 'secret']) {
  if (!obj || typeof obj !== 'object') return obj
  const masked = { ...obj }
  for (const key of keys) {
    if (masked[key]) {
      masked[key] = '***'
    }
  }
  return masked
}

export default createLogger