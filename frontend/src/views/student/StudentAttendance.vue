<template>
  <div class="page-container">
    <div class="page-header">
      <h2>课堂签到</h2>
      <div class="header-actions">
        <span class="server-time">
          <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
            <circle cx="8" cy="8" r="6.5" stroke="currentColor" stroke-width="1.5"/>
            <path d="M8 5v3l2 2" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
          服务器时间：{{ serverTime }}
        </span>
        <el-button size="small" :loading="loading" @click="refreshAll">刷新</el-button>
      </div>
    </div>

    <!-- 离线队列提示 -->
    <div v-if="offlineCount > 0" class="offline-banner">
      <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
        <path d="M8 1v6M8 11h.01" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
      </svg>
      <span>有 {{ offlineCount }} 条离线签到待同步</span>
      <button class="sync-btn" @click="syncOffline">立即同步</button>
    </div>

    <div v-if="loading && courseList.length === 0" class="loading-state">
      <div class="loading-spinner"></div>
      <p>加载中...</p>
    </div>

    <div v-else-if="courseList.length === 0" class="empty-state">
      <span class="empty-icon">📋</span>
      <p>暂无课程可签到</p>
      <span class="empty-hint">去选课后再来签到吧</span>
    </div>

    <div v-else class="checkin-grid">
      <div v-for="course in courseList" :key="course.courseId" class="checkin-card" :class="cardClass(course)">
        <div class="card-top">
          <div class="course-name">{{ course.courseName }}</div>
          <span :class="['status-tag', statusTagClass(course)]">{{ statusLabel(course) }}</span>
        </div>

        <div class="card-body">
          <div class="info-row">
            <span class="info-label">教师</span>
            <span class="info-value">{{ course.teacherName }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">时间</span>
            <span class="info-value">{{ course.courseTime }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">地点</span>
            <span class="info-value">{{ course.location }}</span>
          </div>
          <div v-if="course.checkInTime" class="info-row">
            <span class="info-label">签到时间</span>
            <span class="info-value">{{ formatTime(course.checkInTime) }}</span>
          </div>
        </div>

        <div class="card-footer">
          <button
            v-if="canCheckIn(course)"
            class="checkin-btn"
            :class="{ checking: course.checking }"
            :disabled="course.checking"
            @click="handleCheckIn(course)"
          >
            <svg v-if="!course.checking" width="16" height="16" viewBox="0 0 16 16" fill="none">
              <path d="M3 8l3.5 3.5L13 5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            <span v-else class="btn-spinner"></span>
            {{ course.checking ? '签到中...' : '立即签到' }}
          </button>
          <div v-else-if="course.status === '出勤'" class="success-msg">
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
              <circle cx="8" cy="8" r="7" stroke="#2d6a4f" stroke-width="1.5"/>
              <path d="M5 8l2 2 4-4" stroke="#2d6a4f" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            签到成功
          </div>
          <div v-else-if="course.status === '迟到'" class="late-msg">
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
              <circle cx="8" cy="8" r="7" stroke="#b85c1a" stroke-width="1.5"/>
              <path d="M8 5v3M8 11h.01" stroke="#b85c1a" stroke-width="2" stroke-linecap="round"/>
            </svg>
            已签到（迟到）
          </div>
          <div v-else-if="course.status === '缺勤'" class="absent-msg">
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
              <circle cx="8" cy="8" r="7" stroke="#a61b2e" stroke-width="1.5"/>
              <path d="M5.5 5.5l5 5M10.5 5.5l-5 5" stroke="#a61b2e" stroke-width="1.5" stroke-linecap="round"/>
            </svg>
            缺勤
          </div>
          <div v-else-if="course.status === '请假'" class="leave-msg">
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
              <rect x="2" y="3" width="12" height="10" rx="2" stroke="#2d5a7a" stroke-width="1.5"/>
              <path d="M6 7v2M10 7v2" stroke="#2d5a7a" stroke-width="1.5" stroke-linecap="round"/>
            </svg>
            请假
          </div>
          <div v-else-if="!isTodayCourse(course)" class="no-class-msg">
            <span>今日无课</span>
          </div>
          <div v-else class="pending-msg">
            <span>签到窗口未开启</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 签到结果弹窗 -->
    <el-dialog v-model="resultVisible" title="签到结果" width="400px" :close-on-click-modal="false" center>
      <div class="result-content" :class="resultClass">
        <div class="result-icon">
          <svg v-if="lastResult.success" width="48" height="48" viewBox="0 0 48 48" fill="none">
            <circle cx="24" cy="24" r="22" fill="#edf7f0" stroke="#2d6a4f" stroke-width="2"/>
            <path d="M14 24l7 7 13-14" stroke="#2d6a4f" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          <svg v-else width="48" height="48" viewBox="0 0 48 48" fill="none">
            <circle cx="24" cy="24" r="22" fill="#fdf0ed" stroke="#a61b2e" stroke-width="2"/>
            <path d="M16 16l16 16M32 16L16 32" stroke="#a61b2e" stroke-width="3" stroke-linecap="round"/>
          </svg>
        </div>
        <h3 class="result-title">{{ lastResult.success ? '签到成功' : '签到失败' }}</h3>
        <p class="result-desc">{{ lastResult.message }}</p>
        <div v-if="lastResult.success" class="result-info">
          <div class="result-row">
            <span>课程</span>
            <span>{{ lastResult.courseName }}</span>
          </div>
          <div class="result-row">
            <span>状态</span>
            <span :class="resultStatusClass">{{ lastResult.status }}</span>
          </div>
          <div class="result-row">
            <span>时间</span>
            <span>{{ formatTime(lastResult.checkInTime) }}</span>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getMyCourses } from '../../api/selection'
import { checkIn, getServerTime, getAttendanceHistory } from '../../api/attendance'
import { getCourseList } from '../../api/course'
import { enqueueCheckIn, getQueue, removeFromQueue, getQueueSize, clearQueue } from '../../utils/offlineCheckin'

const loading = ref(false)
const courseList = ref([])
const serverTime = ref('')
const resultVisible = ref(false)
const lastResult = ref({})
const offlineCount = ref(0)
let timeTimer = null
let syncTimer = null

const user = computed(() => JSON.parse(localStorage.getItem('user') || '{}'))

const dayOfWeekNames = ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日']

function formatTime(str) {
  if (!str) return ''
  return str.replace('T', ' ').substring(0, 19)
}

function isTodayCourse(course) {
  const today = new Date().getDay() || 7
  if (!course.courseTime) return false
  const slots = course.courseTime.split(/[,，]/).map(s => s.trim())
  return slots.some(slot => {
    for (let i = 1; i <= 7; i++) {
      if (slot.includes(dayOfWeekNames[i]) && i === today) return true
    }
    return false
  })
}

function canCheckIn(course) {
  // 已经签到或有状态的不能重复签到
  if (course.status && course.status !== '缺勤') return false
  // 课程时间不匹配今天
  if (!isTodayCourse(course)) return false
  return true
}

function cardClass(course) {
  if (course.status === '出勤') return 'card-success'
  if (course.status === '迟到') return 'card-late'
  if (course.status === '缺勤') return 'card-absent'
  if (course.status === '请假') return 'card-leave'
  return ''
}

function statusTagClass(course) {
  if (course.status === '出勤') return 'tag-success'
  if (course.status === '迟到') return 'tag-warning'
  if (course.status === '缺勤') return 'tag-danger'
  if (course.status === '请假') return 'tag-info'
  return 'tag-default'
}

function statusLabel(course) {
  const map = { '出勤': '出勤', '迟到': '迟到', '缺勤': '缺勤', '请假': '请假' }
  return map[course.status] || '未签到'
}

const resultClass = computed(() => lastResult.value.success ? 'result-success' : 'result-error')
const resultStatusClass = computed(() => {
  if (lastResult.value.status === '出勤') return 'text-success'
  if (lastResult.value.status === '迟到') return 'text-warning'
  return ''
})

async function loadServerTime() {
  try {
    const res = await getServerTime()
    if (res && res.time) {
      const date = new Date()
      serverTime.value = `${res.date} ${res.time.substring(0, 8)}`
    }
  } catch {
    const now = new Date()
    serverTime.value = now.toLocaleString('zh-CN')
  }
}

async function loadCourses() {
  loading.value = true
  try {
    const result = await getMyCourses()
    if (result.success) {
      const courses = result.data
      // 获取今天的签到状态
      const today = new Date().toISOString().split('T')[0]
      const historyRes = await getAttendanceHistory(user.value.id).catch(() => ({ data: [] }))
      const todayRecords = (historyRes.data || []).filter(r => {
        if (!r.attendanceDate) return false
        return r.attendanceDate === today
      })

      courseList.value = courses.map(course => {
        const record = todayRecords.find(r => r.courseId === course.course_id)
        return {
          courseId: course.course_id,
          courseName: course.course_name,
          teacherName: course.teacher_name,
          courseTime: course.course_time,
          location: course.location,
          labName: course.lab_name,
          status: record ? record.attendanceStatus : null,
          checkInTime: record ? record.createdAt : null,
          checking: false
        }
      })
    }
  } catch {
    // 离线时仍显示缓存的课程列表
  } finally {
    loading.value = false
  }
}

async function handleCheckIn(course) {
  course.checking = true
  try {
    const result = await checkIn({
      studentId: user.value.id,
      courseId: course.courseId
    })
    lastResult.value = result
    resultVisible.value = true

    if (result.success) {
      course.status = result.status
      course.checkInTime = result.checkInTime
    } else if (result.status) {
      course.status = result.status
    }
  } catch {
    // 网络异常，加入离线队列
    enqueueCheckIn(user.value.id, course.courseId)
    offlineCount.value = getQueueSize()
    ElMessage.warning('网络异常，签到已暂存，网络恢复后自动同步')
    course.checking = false
    return
  } finally {
    course.checking = false
  }
}

async function syncOffline() {
  let queue = getQueue()
  // 过滤掉无效数据（修复前遗留的 undefined courseId 等）
  const validItems = queue.filter(item => item.studentId != null && item.courseId != null)
  if (validItems.length < queue.length) {
    // 清理无效条目，只保留有效数据
    clearQueue()
    validItems.forEach(item => enqueueCheckIn(item.studentId, item.courseId))
  }
  queue = getQueue()
  if (queue.length === 0) return

  let synced = 0
  let failed = 0
  for (const item of [...queue]) {
    try {
      await checkIn({ studentId: item.studentId, courseId: item.courseId })
      removeFromQueue(item.studentId, item.courseId)
      synced++
    } catch {
      // 同步失败（如参数无效或业务拒绝），移除该条目避免反复重试
      removeFromQueue(item.studentId, item.courseId)
      failed++
    }
  }
  offlineCount.value = getQueueSize()
  if (synced > 0) {
    ElMessage.success(`已同步 ${synced} 条签到记录`)
    await loadCourses()
  }
  if (failed > 0) {
    ElMessage.warning(`${failed} 条签到记录同步失败，已从队列移除`)
  }
}

async function refreshAll() {
  await loadServerTime()
  await loadCourses()
  offlineCount.value = getQueueSize()
}

onMounted(() => {
  refreshAll()
  timeTimer = setInterval(loadServerTime, 30000)
  // 定期尝试同步离线队列
  syncTimer = setInterval(syncOffline, 60000)
})

onUnmounted(() => {
  if (timeTimer) clearInterval(timeTimer)
  if (syncTimer) clearInterval(syncTimer)
})
</script>

<style scoped>
.header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  flex-wrap: wrap;
}

.server-time {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.8rem;
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

.offline-banner {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 10px 16px;
  margin-bottom: var(--space-md);
  background: #fef7e0;
  border: 1px solid #f5d87a;
  border-radius: var(--radius-sm);
  color: #8a6d14;
  font-size: 0.84rem;
}

.sync-btn {
  margin-left: auto;
  padding: 4px 12px;
  border: 1px solid #d4b63c;
  border-radius: var(--radius-sm);
  background: #fff;
  color: #8a6d14;
  font-size: 0.78rem;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
}

.sync-btn:hover {
  background: #fef3c7;
}

/* Grid */
.checkin-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--space-md);
}

@media (max-width: 640px) {
  .checkin-grid {
    grid-template-columns: 1fr;
  }
}

/* Card */
.checkin-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border-soft);
  border-radius: var(--radius-md);
  padding: var(--space-lg);
  transition: all var(--duration-fast) var(--ease-out);
  display: flex;
  flex-direction: column;
}

.checkin-card:hover {
  border-color: var(--color-border);
  box-shadow: var(--shadow-md);
}

.checkin-card.card-success {
  border-left: 3px solid var(--color-success);
}

.checkin-card.card-late {
  border-left: 3px solid var(--color-warning);
}

.checkin-card.card-absent {
  border-left: 3px solid var(--color-danger);
  opacity: 0.8;
}

.checkin-card.card-leave {
  border-left: 3px solid var(--color-info);
}

.card-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: var(--space-md);
  gap: var(--space-sm);
}

.course-name {
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--color-text);
}

.status-tag {
  font-size: 0.72rem;
  font-weight: 600;
  padding: 2px 10px;
  border-radius: 12px;
  white-space: nowrap;
  flex-shrink: 0;
}

.tag-success { background: #edf7f0; color: #2d6a4f; }
.tag-warning { background: #fef3e0; color: #b85c1a; }
.tag-danger { background: #fdf0ed; color: #a61b2e; }
.tag-info { background: #e8f0f8; color: #2d5a7a; }
.tag-default { background: var(--color-border-soft); color: var(--color-text-muted); }

.card-body {
  flex: 1;
  margin-bottom: var(--space-md);
}

.info-row {
  display: flex;
  padding: 3px 0;
  font-size: 0.84rem;
}

.info-label {
  width: 64px;
  color: var(--color-text-muted);
  flex-shrink: 0;
}

.info-value {
  color: var(--color-text-soft);
}

.card-footer {
  padding-top: var(--space-sm);
  border-top: 1px solid var(--color-border-soft);
}

.checkin-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 10px;
  border: none;
  border-radius: var(--radius-sm);
  background: var(--color-primary);
  color: #fff;
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
}

.checkin-btn:hover:not(:disabled) {
  background: var(--color-primary-soft);
}

.checkin-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.checkin-btn.checking {
  background: var(--color-primary-soft);
}

.btn-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.success-msg, .late-msg, .absent-msg, .leave-msg, .no-class-msg, .pending-msg {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.84rem;
  font-weight: 500;
  padding: 6px 0;
}

.success-msg { color: var(--color-success); }
.late-msg { color: var(--color-warning); }
.absent-msg { color: var(--color-danger); }
.leave-msg { color: var(--color-info); }
.no-class-msg { color: var(--color-text-muted); }
.pending-msg { color: var(--color-text-muted); }

/* Result Dialog */
.result-content {
  text-align: center;
  padding: var(--space-md) 0;
}

.result-icon {
  margin-bottom: var(--space-md);
}

.result-title {
  font-size: 1.15rem;
  font-weight: 700;
  margin-bottom: var(--space-sm);
}

.result-desc {
  font-size: 0.88rem;
  color: var(--color-text-muted);
  margin-bottom: var(--space-md);
}

.result-info {
  display: inline-flex;
  flex-direction: column;
  gap: 8px;
  padding: var(--space-md);
  background: var(--color-bg);
  border-radius: var(--radius-sm);
  text-align: left;
  min-width: 200px;
}

.result-row {
  display: flex;
  justify-content: space-between;
  gap: var(--space-md);
  font-size: 0.84rem;
}

.result-row span:first-child {
  color: var(--color-text-muted);
}

.result-row span:last-child {
  font-weight: 500;
  color: var(--color-text);
}

.text-success { color: var(--color-success) !important; }
.text-warning { color: var(--color-warning) !important; }

/* Loading */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-2xl);
  color: var(--color-text-muted);
  gap: var(--space-md);
}

.loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid var(--color-border-soft);
  border-top-color: var(--color-accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.empty-hint {
  font-size: 0.85rem;
  color: var(--color-text-muted);
  opacity: 0.7;
}
</style>