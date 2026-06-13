/**
 * 课表事件总线
 * 用于在选课操作后通知课表页面刷新，解决同一Tab内 localStorage 变化不触发 storage 事件的问题
 */
const SCHEDULE_CHANNEL = 'schedule_update'

let channel = null

function getChannel() {
  if (!channel) {
    try {
      channel = new BroadcastChannel(SCHEDULE_CHANNEL)
    } catch {
      // BroadcastChannel 不支持时降级
      channel = null
    }
  }
  return channel
}

/**
 * 通知课表刷新（选课/退课后调用）
 */
export function notifyScheduleUpdate() {
  const ch = getChannel()
  if (ch) {
    ch.postMessage({ type: 'schedule_update', timestamp: Date.now() })
  }
  // 同时写入 localStorage 以支持跨 Tab 通知
  localStorage.setItem('schedule_bust', String(Date.now()))
}

/**
 * 监听课表更新事件
 * @param {Function} callback
 * @returns {Function} 取消监听的函数
 */
export function onScheduleUpdate(callback) {
  const ch = getChannel()
  if (ch) {
    const handler = (event) => {
      if (event.data && event.data.type === 'schedule_update') {
        callback()
      }
    }
    ch.addEventListener('message', handler)
    return () => ch.removeEventListener('message', handler)
  }
  // BroadcastChannel 不可用时，降级为 localStorage storage 事件监听
  const storageHandler = (event) => {
    if (event.key === 'schedule_bust') {
      callback()
    }
  }
  window.addEventListener('storage', storageHandler)
  return () => window.removeEventListener('storage', storageHandler)
}