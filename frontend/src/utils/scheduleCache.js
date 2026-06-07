/**
 * 课表本地缓存工具
 * 支持 localStorage 缓存、定期同步、过期管理
 */

const CACHE_KEY = 'schedule_cache'
const CACHE_TIMESTAMP_KEY = 'schedule_cache_timestamp'
const CACHE_TTL = 30 * 60 * 1000 // 30 分钟缓存有效期

/**
 * 获取缓存的课表数据
 * @returns {{data: Array|null, timestamp: number, isExpired: boolean}}
 */
export function getScheduleCache() {
  try {
    const raw = localStorage.getItem(CACHE_KEY)
    const timestamp = parseInt(localStorage.getItem(CACHE_TIMESTAMP_KEY) || '0', 10)
    const data = raw ? JSON.parse(raw) : null
    const isExpired = Date.now() - timestamp > CACHE_TTL

    return { data, timestamp, isExpired: !data || isExpired }
  } catch {
    return { data: null, timestamp: 0, isExpired: true }
  }
}

/**
 * 保存课表数据到缓存
 * @param {Array} courses - 课程列表
 */
export function setScheduleCache(courses) {
  try {
    localStorage.setItem(CACHE_KEY, JSON.stringify(courses))
    localStorage.setItem(CACHE_TIMESTAMP_KEY, String(Date.now()))
  } catch (e) {
    console.warn('[课表缓存] 缓存写入失败:', e.message)
  }
}

/**
 * 清除课表缓存
 */
export function clearScheduleCache() {
  localStorage.removeItem(CACHE_KEY)
  localStorage.removeItem(CACHE_TIMESTAMP_KEY)
}

/**
 * 获取缓存最后更新时间
 * @returns {string}
 */
export function getCacheAge() {
  const timestamp = parseInt(localStorage.getItem(CACHE_TIMESTAMP_KEY) || '0', 10)
  if (!timestamp) return '无缓存'

  const diff = Date.now() - timestamp
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`
  return `${Math.floor(diff / 3600000)} 小时前`
}