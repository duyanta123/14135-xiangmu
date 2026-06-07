<template>
  <div class="page-container">
    <div class="page-header">
      <h2>考勤记录</h2>
      <div class="header-actions">
        <span class="record-count">共 {{ filteredRecords.length }} 条记录</span>
      </div>
    </div>

    <!-- 统计概览 -->
    <div v-if="records.length > 0" class="stats-row">
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
    </div>

    <div v-if="loading" class="loading-state">
      <div class="loading-spinner"></div>
      <p>加载中...</p>
    </div>

    <div v-else-if="records.length === 0" class="empty-state">
      <span class="empty-icon">📋</span>
      <p>暂无考勤记录</p>
    </div>

    <div v-else class="table-responsive">
      <el-table :data="filteredRecords" stripe>
        <el-table-column prop="courseName" label="课程" min-width="160" />
        <el-table-column prop="courseTime" label="上课时间" width="140" />
        <el-table-column prop="attendanceDate" label="日期" width="130">
          <template #default="scope">
            {{ scope.row.attendanceDate || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="签到状态" width="110">
          <template #default="scope">
            <span :class="['status-badge', statusClass(scope.row.attendanceStatus)]">
              {{ scope.row.attendanceStatus || '未知' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="签到时间" width="180">
          <template #default="scope">
            {{ formatTime(scope.row.createdAt) }}
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getAttendanceHistory } from '../../api/attendance'

const loading = ref(false)
const records = ref([])

const user = computed(() => JSON.parse(localStorage.getItem('user') || '{}'))

const filteredRecords = computed(() => records.value)

const stats = computed(() => {
  const result = { attend: 0, late: 0, absent: 0, leave: 0 }
  records.value.forEach(r => {
    if (r.attendanceStatus === '出勤') result.attend++
    else if (r.attendanceStatus === '迟到') result.late++
    else if (r.attendanceStatus === '缺勤') result.absent++
    else if (r.attendanceStatus === '请假') result.leave++
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

async function loadHistory() {
  loading.value = true
  try {
    const result = await getAttendanceHistory(user.value.id)
    if (result.success) {
      records.value = result.data || []
    }
  } catch {
    records.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => { loadHistory() })
</script>

<style scoped>
.record-count {
  font-size: 0.82rem;
  color: var(--color-text-muted);
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-md);
  margin-bottom: var(--space-lg);
}

@media (max-width: 520px) {
  .stats-row {
    grid-template-columns: repeat(2, 1fr);
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