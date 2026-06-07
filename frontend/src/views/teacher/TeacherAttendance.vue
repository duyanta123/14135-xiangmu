<template>
  <div class="page-container">
    <div class="page-header">
      <h2>考勤管理</h2>
      <div class="header-actions">
        <el-button size="small" @click="exportExcel" :disabled="!selectedCourse || studentList.length === 0">
          导出 Excel
        </el-button>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-select v-model="selectedCourse" placeholder="选择课程" size="large" @change="onCourseChange" clearable>
        <el-option v-for="course in courseList" :key="course.id" :label="course.course_name" :value="course.id" />
      </el-select>
      <el-date-picker
        v-model="selectedDate"
        type="date"
        placeholder="选择日期"
        size="large"
        :disabled="!selectedCourse"
        @change="onDateChange"
        value-format="YYYY-MM-DD"
      />
      <div v-if="attendanceDates.length > 0" class="date-chips">
        <span class="chips-label">近期考勤：</span>
        <button
          v-for="date in attendanceDates.slice(0, 7)"
          :key="date"
          :class="['chip', { active: selectedDate === date }]"
          @click="selectedDate = date; onDateChange()"
        >
          {{ date }}
        </button>
      </div>
    </div>

    <!-- 统计概览 -->
    <div v-if="selectedCourse && studentList.length > 0" class="stats-row">
      <div class="stat-card stat-success">
        <span class="stat-num">{{ stats.attend }}</span>
        <span class="stat-label">出勤</span>
      </div>
      <div class="stat-card stat-warning">
        <span class="stat-num">{{ stats.late }}</span>
        <span class="stat-label">迟到</span>
      </div>
      <div class="stat-card stat-danger">
        <span class="stat-num">{{ stats.absent }}</span>
        <span class="stat-label">缺勤</span>
      </div>
      <div class="stat-card stat-info">
        <span class="stat-num">{{ stats.leave }}</span>
        <span class="stat-label">请假</span>
      </div>
      <div class="stat-card stat-total">
        <span class="stat-num">{{ stats.total }}</span>
        <span class="stat-label">总人数</span>
      </div>
    </div>

    <div v-if="!selectedCourse" class="empty-state">
      <span class="empty-icon">✅</span>
      <p>请选择课程和日期查看考勤</p>
    </div>

    <template v-else>
      <div v-if="loading" class="loading-state">
        <div class="loading-spinner"></div>
        <p>加载中...</p>
      </div>

      <div v-else-if="studentList.length === 0" class="empty-state">
        <span class="empty-icon">🎓</span>
        <p>该课程暂无学生或考勤记录</p>
      </div>

      <div v-else class="table-responsive">
        <el-table :data="studentList" stripe>
          <el-table-column prop="studentNo" label="学号" width="120" />
          <el-table-column prop="studentName" label="姓名" width="100" />
          <el-table-column prop="major" label="专业" min-width="160" />
          <el-table-column label="签到状态" width="110">
            <template #default="scope">
              <span :class="['status-badge', statusClass(scope.row.status)]">
                {{ scope.row.status }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="签到时间" width="180">
            <template #default="scope">
              {{ formatTime(scope.row.checkInTime) }}
            </template>
          </el-table-column>
          <el-table-column label="修改记录" min-width="180">
            <template #default="scope">
              <div v-if="scope.row.modifyTime" class="modify-info">
                <span class="modify-time">{{ formatTime(scope.row.modifyTime) }}</span>
                <span v-if="scope.row.modifyReason" class="modify-reason">{{ scope.row.modifyReason }}</span>
              </div>
              <span v-else class="no-modify">-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="scope">
              <el-button
                v-if="scope.row.status === '缺勤'"
                type="warning"
                size="small"
                plain
                @click="handleModify(scope.row)"
              >
                改为请假
              </el-button>
              <span v-else class="no-action">-</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>

    <!-- 修改请假弹窗 -->
    <el-dialog v-model="modifyVisible" title="修改考勤状态" width="420px" :close-on-click-modal="false">
      <div class="modify-dialog">
        <div class="modify-student">
          <span class="modify-label">学生</span>
          <span class="modify-value">{{ modifyTarget?.studentName }}（{{ modifyTarget?.studentNo }}）</span>
        </div>
        <div class="modify-change">
          <span class="modify-label">状态变更</span>
          <span class="status-badge s-danger">缺勤</span>
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
            <path d="M5 8h6" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            <path d="M8 5l3 3-3 3" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          <span class="status-badge s-info">请假</span>
        </div>
        <div class="modify-field">
          <span class="modify-label">修改原因</span>
          <el-input v-model="modifyReason" placeholder="请输入修改原因" maxlength="200" show-word-limit />
        </div>
      </div>
      <template #footer>
        <el-button @click="modifyVisible = false">取消</el-button>
        <el-button type="primary" :loading="modifying" @click="confirmModify">确认修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getCourseList } from '../../api/course'
import { getCourseAttendance, getAttendanceDates, updateAttendanceStatus, exportAttendance } from '../../api/attendance'
import * as XLSX from 'xlsx'

const loading = ref(false)
const courseList = ref([])
const studentList = ref([])
const attendanceDates = ref([])
const selectedCourse = ref('')
const selectedDate = ref('')
const modifyVisible = ref(false)
const modifyTarget = ref(null)
const modifyReason = ref('')
const modifying = ref(false)

const user = computed(() => JSON.parse(localStorage.getItem('user') || '{}'))

const stats = computed(() => {
  const result = { attend: 0, late: 0, absent: 0, leave: 0, total: studentList.value.length }
  studentList.value.forEach(s => {
    if (s.status === '出勤') result.attend++
    else if (s.status === '迟到') result.late++
    else if (s.status === '缺勤') result.absent++
    else if (s.status === '请假') result.leave++
  })
  return result
})

function formatTime(str) {
  if (!str) return '-'
  return str.replace('T', ' ').substring(0, 19)
}

function statusClass(status) {
  const map = { '出勤': 's-success', '迟到': 's-warning', '缺勤': 's-danger', '请假': 's-info' }
  return map[status] || 's-default'
}

async function loadCourses() {
  try {
    const result = await getCourseList()
    if (result.success) {
      courseList.value = result.data.filter(c => c.teacher_name === user.value.name)
    }
  } catch {
    ElMessage.error('加载课程列表失败')
  }
}

async function onCourseChange() {
  selectedDate.value = ''
  studentList.value = []
  if (!selectedCourse.value) return

  try {
    const result = await getAttendanceDates(selectedCourse.value)
    if (result.success) {
      attendanceDates.value = result.data || []
      if (attendanceDates.value.length > 0) {
        selectedDate.value = attendanceDates.value[0]
        await loadAttendance()
      }
    }
  } catch {
    ElMessage.error('加载考勤日期失败')
  }
}

async function onDateChange() {
  if (!selectedDate.value) {
    studentList.value = []
    return
  }
  await loadAttendance()
}

async function loadAttendance() {
  if (!selectedCourse.value || !selectedDate.value) return
  loading.value = true
  try {
    const result = await getCourseAttendance(selectedCourse.value, selectedDate.value)
    if (result.success) {
      studentList.value = result.data || []
    }
  } catch {
    ElMessage.error('加载考勤列表失败')
  } finally {
    loading.value = false
  }
}

function handleModify(row) {
  modifyTarget.value = row
  modifyReason.value = ''
  modifyVisible.value = true
}

async function confirmModify() {
  if (!modifyReason.value.trim()) {
    ElMessage.warning('请输入修改原因')
    return
  }
  modifying.value = true
  try {
    const result = await updateAttendanceStatus({
      attendanceId: modifyTarget.value.attendanceId,
      newStatus: '请假',
      teacherId: user.value.id,
      reason: modifyReason.value.trim()
    })
    if (result.success) {
      ElMessage.success('修改成功')
      modifyVisible.value = false
      await loadAttendance()
    } else {
      ElMessage.error(result.message || '修改失败')
    }
  } catch {
    ElMessage.error('修改失败')
  } finally {
    modifying.value = false
  }
}

async function exportExcel() {
  try {
    const result = await exportAttendance(selectedCourse.value)
    if (!result.success || !result.data || result.data.length === 0) {
      ElMessage.warning('没有可导出的数据')
      return
    }

    const exportData = result.data.map(item => ({
      '学号': item.studentNo,
      '姓名': item.studentName,
      '专业': item.major,
      '课程': item.courseName,
      '日期': item.attendanceDate,
      '签到状态': item.status,
      '签到时间': item.checkInTime ? formatTime(item.checkInTime) : ''
    }))

    const courseName = courseList.value.find(c => c.id === selectedCourse.value)?.course_name || '课程'
    const ws = XLSX.utils.json_to_sheet(exportData)
    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, ws, '考勤记录')
    XLSX.writeFile(wb, `${courseName}_考勤记录.xlsx`)
    ElMessage.success('导出成功')
  } catch {
    ElMessage.error('导出失败')
  }
}

onMounted(() => { loadCourses() })
</script>

<style scoped>
.filter-bar {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  margin-bottom: var(--space-lg);
  flex-wrap: wrap;
}

.filter-bar .el-select {
  width: 220px;
}

.date-chips {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.chips-label {
  font-size: 0.78rem;
  color: var(--color-text-muted);
}

.chip {
  padding: 4px 12px;
  border: 1px solid var(--color-border);
  border-radius: 16px;
  background: var(--color-surface);
  color: var(--color-text-soft);
  font-size: 0.78rem;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
  white-space: nowrap;
}

.chip:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
}

.chip.active {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: #fff;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: var(--space-md);
  margin-bottom: var(--space-lg);
}

@media (max-width: 640px) {
  .stats-row {
    grid-template-columns: repeat(3, 1fr);
  }
}

.stat-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: var(--space-md);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  border: 1px solid var(--color-border-soft);
}

.stat-num {
  font-size: 1.5rem;
  font-weight: 700;
  line-height: 1.2;
}

.stat-label {
  font-size: 0.78rem;
  color: var(--color-text-muted);
  margin-top: 4px;
}

.stat-success .stat-num { color: var(--color-success); }
.stat-warning .stat-num { color: var(--color-warning); }
.stat-danger .stat-num { color: var(--color-danger); }
.stat-info .stat-num { color: var(--color-info); }
.stat-total .stat-num { color: var(--color-text); }

.status-badge {
  font-size: 0.78rem;
  font-weight: 600;
  padding: 2px 10px;
  border-radius: 12px;
}

.s-success { background: #edf7f0; color: #2d6a4f; }
.s-warning { background: #fef3e0; color: #b85c1a; }
.s-danger { background: #fdf0ed; color: #a61b2e; }
.s-info { background: #e8f0f8; color: #2d5a7a; }
.s-default { background: var(--color-border-soft); color: var(--color-text-muted); }

.modify-info {
  display: flex;
  flex-direction: column;
  font-size: 0.78rem;
  gap: 2px;
}

.modify-time {
  color: var(--color-text-soft);
}

.modify-reason {
  color: var(--color-text-muted);
  font-size: 0.72rem;
}

.no-modify, .no-action {
  color: var(--color-text-muted);
  font-size: 0.82rem;
}

/* Modify Dialog */
.modify-dialog {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.modify-student, .modify-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.modify-change {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.modify-label {
  font-size: 0.8rem;
  color: var(--color-text-muted);
  font-weight: 500;
}

.modify-value {
  font-size: 0.92rem;
  font-weight: 600;
  color: var(--color-text);
}

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
</style>