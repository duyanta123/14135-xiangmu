<template>
  <div class="page-container">
    <div class="page-header">
      <h2>我的课表</h2>
      <div class="header-actions">
        <div v-if="cacheAge" class="cache-status" :title="'缓存时间：' + cacheAge">
          <span class="cache-dot" :class="{ expired: cacheExpired }"></span>
          <span class="cache-text">{{ cacheExpired ? '缓存已过期' : '已缓存' }}</span>
        </div>
        <div class="view-toggle">
          <button
            v-for="mode in viewModes"
            :key="mode.value"
            :class="['toggle-btn', { active: viewMode === mode.value }]"
            @click="viewMode = mode.value"
          >
            {{ mode.label }}
          </button>
        </div>
        <el-button size="small" :loading="loading" @click="refreshSchedule">
          <template #icon>
            <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
              <path d="M2 8a6 6 0 0 1 10.47-4M14 8a6 6 0 0 1-10.47 4M2 2v4h4M14 14v-4h-4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </template>
          刷新
        </el-button>
      </div>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading && scheduleCourses.length === 0" class="loading-state">
      <div class="loading-spinner"></div>
      <p>加载课表中...</p>
    </div>

    <!-- 空状态 -->
    <div v-else-if="scheduleCourses.length === 0" class="empty-state">
      <span class="empty-icon">📅</span>
      <p>暂无课程安排</p>
      <span class="empty-hint">去课程列表选课吧，选课后课表将自动更新</span>
    </div>

    <!-- 课表视图 -->
    <div v-else class="schedule-wrapper">
      <!-- 周视图 -->
      <div v-if="viewMode === 'week'" class="schedule-week">
        <div class="schedule-table">
          <div class="schedule-header">
            <div class="header-cell time-label">时间</div>
            <div
              v-for="day in visibleDays"
              :key="day.index"
              :class="['header-cell day-label', { today: day.isToday }]"
            >
              <span class="day-name">{{ day.name }}</span>
              <span v-if="day.isToday" class="today-badge">今天</span>
            </div>
          </div>
          <div class="schedule-body">
            <div
              v-for="period in periodConfig"
              :key="period.label"
              class="schedule-row"
            >
              <div class="time-cell">
                <span class="period-label">{{ period.label }}</span>
                <span class="period-time">{{ period.time }}</span>
              </div>
              <div
                v-for="day in visibleDays"
                :key="day.index"
                :class="['course-cell', { 'has-course': getCellCourses(day.index, period.start).length > 0 }]"
                @click="getCellCourses(day.index, period.start).length && openDetail(getCellCourses(day.index, period.start))"
              >
                <div
                  v-for="course in getCellCourses(day.index, period.start)"
                  :key="course.id"
                  class="course-block"
                  :style="{ backgroundColor: getCourseColor(course.course_name) }"
                  @click.stop="openDetail([course])"
                >
                  <span class="block-name">{{ course.course_name }}</span>
                  <span class="block-info">{{ course.teacher_name }}</span>
                  <span class="block-info">{{ course.location }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 日视图 -->
      <div v-else class="schedule-day">
        <div class="day-selector">
          <button
            v-for="day in visibleDays"
            :key="day.index"
            :class="['day-btn', { active: selectedDay === day.index }]"
            @click="selectedDay = day.index"
          >
            {{ day.name }}
          </button>
        </div>
        <div class="day-schedule">
          <div
            v-for="period in periodConfig"
            :key="period.label"
            class="day-row"
          >
            <div class="day-time">
              <span class="period-label">{{ period.label }}</span>
              <span class="period-time">{{ period.time }}</span>
            </div>
            <div class="day-courses">
              <div
                v-for="course in getCellCourses(selectedDay, period.start)"
                :key="course.id"
                class="day-course-card"
                :style="{ borderLeftColor: getCourseColor(course.course_name) }"
                @click="openDetail([course])"
              >
                <div class="day-course-header">
                  <h4>{{ course.course_name }}</h4>
                  <span class="badge">{{ period.label }}</span>
                </div>
                <div class="day-course-body">
                  <div class="info-row">
                    <span class="info-label">教师</span>
                    <span class="info-value">{{ course.teacher_name }}</span>
                  </div>
                  <div class="info-row">
                    <span class="info-label">地点</span>
                    <span class="info-value">{{ course.location }}</span>
                  </div>
                  <div class="info-row">
                    <span class="info-label">实验室</span>
                    <span class="info-value">{{ course.lab_name }}</span>
                  </div>
                </div>
              </div>
              <div v-if="getCellCourses(selectedDay, period.start).length === 0" class="day-empty">
                无课程安排
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 课程详情弹窗 -->
    <el-dialog v-model="detailVisible" title="课程详情" width="480px" :close-on-click-modal="false">
      <template v-if="detailCourse">
        <div class="detail-section">
          <h3 class="detail-name">{{ detailCourse.course_name }}</h3>
        </div>
        <div class="detail-grid">
          <div class="detail-item">
            <span class="detail-label">授课教师</span>
            <span class="detail-value">{{ detailCourse.teacher_name }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">上课时间</span>
            <span class="detail-value">{{ detailCourse.course_time }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">实验室</span>
            <span class="detail-value">{{ detailCourse.lab_name }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">上课地点</span>
            <span class="detail-value">{{ detailCourse.location }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">课程容量</span>
            <span class="detail-value">{{ detailCourse.selected_count }} / {{ detailCourse.max_count }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">选课时间</span>
            <span class="detail-value">{{ detailCourse.select_time }}</span>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getMyCourses } from '../../api/selection'
import { PERIOD_CONFIG, DAY_NAMES } from '../../utils/scheduleParser'
import { getScheduleCache, setScheduleCache, getCacheAge } from '../../utils/scheduleCache'
import { onScheduleUpdate } from '../../utils/scheduleEventBus'

const loading = ref(false)
const scheduleCourses = ref([])
const viewMode = ref('week')
const selectedDay = ref(1)
const detailVisible = ref(false)
const detailCourse = ref(null)
const cacheAge = ref('')
const cacheExpired = ref(false)
let syncTimer = null

const viewModes = [
  { label: '周', value: 'week' },
  { label: '日', value: 'day' }
]

const periodConfig = PERIOD_CONFIG

const visibleDays = computed(() => {
  const today = new Date().getDay() || 7
  return DAY_NAMES.slice(1, 8).map((name, i) => ({
    index: i + 1,
    name,
    isToday: i + 1 === today
  }))
})

const courseColors = [
  '#e8d5b7', '#c8d9e8', '#d4c9e0', '#c8e0d4', '#e0d0c0',
  '#c0d4e0', '#d8d0c8', '#c8d0d8', '#e0d8c8', '#d0c8e0'
]

function getCourseColor(courseName) {
  let hash = 0
  for (let i = 0; i < courseName.length; i++) {
    hash = courseName.charCodeAt(i) + ((hash << 5) - hash)
  }
  return courseColors[Math.abs(hash) % courseColors.length]
}

function getCellCourses(day, startPeriod) {
  return scheduleCourses.value.filter(course => {
    if (!course.course_time) return false
    const slots = course.course_time.split(/[,，]/).map(s => s.trim())
    return slots.some(slot => {
      const dayMatch = slot.match(/(周[一二三四五六日]|星期[一二三四五六日])/)
      const periodMatch = slot.match(/(\d+)-(\d+)节/)
      if (!dayMatch || !periodMatch) return false
      const dayMap = { '周一': 1, '周二': 2, '周三': 3, '周四': 4, '周五': 5, '周六': 6, '周日': 7,
                        '星期一': 1, '星期二': 2, '星期三': 3, '星期四': 4, '星期五': 5, '星期六': 6, '星期日': 7 }
      const d = dayMap[dayMatch[0]] || 0
      const start = parseInt(periodMatch[1])
      return d === day && start === startPeriod
    })
  })
}

function openDetail(courses) {
  if (courses.length === 0) return
  detailCourse.value = courses[0]
  detailVisible.value = true
}

async function loadSchedule(forceRefresh = false) {
  if (!forceRefresh) {
    const cached = getScheduleCache()
    if (cached.data && !cached.isExpired) {
      scheduleCourses.value = cached.data
      cacheAge.value = getCacheAge()
      cacheExpired.value = false
      return
    }
    if (cached.data && cached.isExpired) {
      scheduleCourses.value = cached.data
      cacheAge.value = getCacheAge()
      cacheExpired.value = true
    }
  }

  loading.value = true
  try {
    const result = await getMyCourses()
    if (result.success) {
      scheduleCourses.value = result.data
      setScheduleCache(result.data)
      cacheAge.value = getCacheAge()
      cacheExpired.value = false
    } else {
      ElMessage.error(result.message || '加载课表失败')
    }
  } catch {
    const cached = getScheduleCache()
    if (cached.data) {
      scheduleCourses.value = cached.data
      cacheAge.value = getCacheAge()
      cacheExpired.value = true
    } else {
      ElMessage.error('加载课表失败，请检查网络连接')
    }
  } finally {
    loading.value = false
  }
}

function refreshSchedule() {
  loadSchedule(true)
}

// 选课后自动刷新：监听 localStorage 变化
function onStorageChange(e) {
  if (e.key === 'schedule_bust') {
    loadSchedule(true)
  }
}

onMounted(() => {
  loadSchedule()
  // 使用 BroadcastChannel 监听课表更新（同 Tab + 跨 Tab）
  const unsub = onScheduleUpdate(() => loadSchedule(true))
  // 跨 Tab 兼容：监听 localStorage 变化
  window.addEventListener('storage', onStorageChange)
  // 定期同步：每 5 分钟检查一次
  syncTimer = setInterval(() => {
    const cached = getScheduleCache()
    if (cached.isExpired) {
      loadSchedule(true)
    }
  }, 5 * 60 * 1000)
  // 存储清理函数
  window._scheduleUnsub = unsub
})

onUnmounted(() => {
  window.removeEventListener('storage', onStorageChange)
  if (window._scheduleUnsub) {
    window._scheduleUnsub()
    delete window._scheduleUnsub
  }
  if (syncTimer) clearInterval(syncTimer)
})


</script>

<style scoped>
.header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-wrap: wrap;
}

.cache-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.75rem;
  color: var(--color-text-muted);
  cursor: default;
}

.cache-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #67c23a;
}

.cache-dot.expired {
  background: #e6a23c;
}

.view-toggle {
  display: flex;
  background: var(--color-border-soft);
  border-radius: var(--radius-sm);
  padding: 2px;
}

.toggle-btn {
  padding: 4px 14px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-muted);
  font-size: 0.82rem;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
}

.toggle-btn.active {
  background: var(--color-surface);
  color: var(--color-text);
  box-shadow: var(--shadow-sm);
}

/* --- Loading --- */
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

@keyframes spin {
  to { transform: rotate(360deg); }
}

.empty-hint {
  font-size: 0.85rem;
  color: var(--color-text-muted);
  opacity: 0.7;
}

/* --- Week View --- */
.schedule-wrapper {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.schedule-table {
  min-width: 700px;
  border: 1px solid var(--color-border-soft);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.schedule-header {
  display: grid;
  grid-template-columns: 100px repeat(7, 1fr);
  background: var(--color-primary);
  color: #fff;
}

.header-cell {
  padding: 12px 8px;
  text-align: center;
  font-size: 0.85rem;
  font-weight: 600;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.header-cell.time-label {
  font-size: 0.78rem;
  opacity: 0.7;
}

.day-label.today {
  background: var(--color-accent);
}

.today-badge {
  font-size: 0.65rem;
  padding: 1px 6px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.25);
  font-weight: 500;
}

.schedule-body {
  background: var(--color-surface);
}

.schedule-row {
  display: grid;
  grid-template-columns: 100px repeat(7, 1fr);
  border-bottom: 1px solid var(--color-border-soft);
  min-height: 80px;
}

.schedule-row:last-child {
  border-bottom: none;
}

.time-cell {
  padding: 10px 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #faf9f7;
  border-right: 1px solid var(--color-border-soft);
}

.period-label {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--color-text);
}

.period-time {
  font-size: 0.68rem;
  color: var(--color-text-muted);
  margin-top: 2px;
}

.course-cell {
  padding: 4px;
  border-right: 1px solid var(--color-border-soft);
  cursor: default;
  transition: background var(--duration-fast) var(--ease-out);
}

.course-cell:last-child {
  border-right: none;
}

.course-cell.has-course {
  cursor: pointer;
}

.course-cell.has-course:hover {
  background: rgba(200, 141, 44, 0.04);
}

.course-block {
  padding: 6px 8px;
  border-radius: 4px;
  margin-bottom: 3px;
  display: flex;
  flex-direction: column;
  gap: 1px;
  transition: transform var(--duration-fast) var(--ease-out);
}

.course-block:hover {
  transform: scale(1.02);
}

.block-name {
  font-size: 0.76rem;
  font-weight: 600;
  color: var(--color-text);
  line-height: 1.3;
}

.block-info {
  font-size: 0.68rem;
  color: var(--color-text-soft);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* --- Day View --- */
.day-selector {
  display: flex;
  gap: 4px;
  margin-bottom: var(--space-md);
  overflow-x: auto;
  padding-bottom: 4px;
}

.day-btn {
  padding: 6px 16px;
  border: none;
  border-radius: var(--radius-sm);
  background: var(--color-border-soft);
  color: var(--color-text-soft);
  font-size: 0.84rem;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: all var(--duration-fast) var(--ease-out);
}

.day-btn.active {
  background: var(--color-primary);
  color: #fff;
}

.day-row {
  display: flex;
  border-bottom: 1px solid var(--color-border-soft);
  min-height: 80px;
}

.day-row:last-child {
  border-bottom: none;
}

.day-time {
  width: 100px;
  padding: 12px 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #faf9f7;
  border-right: 1px solid var(--color-border-soft);
  flex-shrink: 0;
}

.day-courses {
  flex: 1;
  padding: 8px;
  min-height: 80px;
}

.day-course-card {
  padding: 14px 16px;
  border-left: 3px solid var(--color-accent);
  background: var(--color-surface);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
  box-shadow: var(--shadow-sm);
}

.day-course-card:hover {
  box-shadow: var(--shadow-md);
  transform: translateX(2px);
}

.day-course-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.day-course-header h4 {
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--color-text);
}

.badge {
  font-size: 0.7rem;
  padding: 2px 8px;
  border-radius: 10px;
  background: var(--color-border-soft);
  color: var(--color-text-muted);
  font-weight: 500;
}

.day-course-body .info-row {
  display: flex;
  padding: 2px 0;
  font-size: 0.82rem;
}

.day-course-body .info-label {
  width: 52px;
  color: var(--color-text-muted);
  flex-shrink: 0;
}

.day-course-body .info-value {
  color: var(--color-text-soft);
}

.day-empty {
  padding: 24px;
  text-align: center;
  color: var(--color-text-muted);
  font-size: 0.85rem;
}

/* --- Detail Dialog --- */
.detail-section {
  margin-bottom: var(--space-md);
}

.detail-name {
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--color-text);
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.detail-label {
  font-size: 0.78rem;
  color: var(--color-text-muted);
  letter-spacing: 0.02em;
}

.detail-value {
  font-size: 0.9rem;
  color: var(--color-text);
  font-weight: 500;
}

/* --- Responsive --- */
@media (max-width: 768px) {
  .schedule-table {
    min-width: 600px;
  }

  .schedule-header {
    grid-template-columns: 70px repeat(7, 1fr);
  }

  .schedule-row {
    grid-template-columns: 70px repeat(7, 1fr);
    min-height: 64px;
  }

  .header-cell {
    padding: 8px 4px;
    font-size: 0.72rem;
  }

  .time-cell {
    padding: 6px 4px;
  }

  .period-label {
    font-size: 0.72rem;
  }

  .period-time {
    font-size: 0.62rem;
  }

  .block-name {
    font-size: 0.7rem;
  }

  .block-info {
    font-size: 0.62rem;
  }

  .day-time {
    width: 70px;
  }

  .header-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 520px) {
  .schedule-table {
    min-width: 520px;
  }

  .schedule-header {
    grid-template-columns: 56px repeat(7, 1fr);
  }

  .schedule-row {
    grid-template-columns: 56px repeat(7, 1fr);
    min-height: 56px;
  }

  .header-cell {
    padding: 6px 2px;
    font-size: 0.65rem;
  }

  .day-time {
    width: 56px;
  }

  .day-btn {
    padding: 5px 10px;
    font-size: 0.76rem;
  }
}
</style>