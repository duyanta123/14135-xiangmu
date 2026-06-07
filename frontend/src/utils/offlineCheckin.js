// 离线签到队列 — 网络异常时暂存签到请求，恢复后自动同步

const STORAGE_KEY = 'offline_checkin_queue'

function loadQueue() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]')
  } catch {
    return []
  }
}

function saveQueue(queue) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(queue))
}

export function enqueueCheckIn(studentId, courseId) {
  const queue = loadQueue()
  // 去重
  if (!queue.some(item => item.studentId === studentId && item.courseId === courseId)) {
    queue.push({ studentId, courseId, timestamp: Date.now() })
    saveQueue(queue)
  }
}

export function getQueue() {
  return loadQueue()
}

export function removeFromQueue(studentId, courseId) {
  const queue = loadQueue().filter(
    item => !(item.studentId === studentId && item.courseId === courseId)
  )
  saveQueue(queue)
  return queue
}

export function clearQueue() {
  saveQueue([])
}

export function getQueueSize() {
  return loadQueue().length
}