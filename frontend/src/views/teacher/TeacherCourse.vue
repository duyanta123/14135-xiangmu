<template>
  <div class="page-container">
    <div class="page-header">
      <h2>我的课程</h2>
      <span class="count">{{ courseList.length }} 门课程</span>
    </div>

    <div v-if="courseList.length === 0" class="empty-state">
      <span class="empty-icon">📖</span>
      <p>暂无分配的课程</p>
    </div>

    <div v-else class="course-grid">
      <div v-for="course in courseList" :key="course.id" class="course-card">
        <div class="card-top">
          <div class="course-name">{{ course.course_name }}</div>
          <el-button type="primary" size="small" @click="viewStudentList(course)">查看学生</el-button>
        </div>
        <div class="card-body">
          <div class="info-row">
            <span class="info-label">实验室</span>
            <span class="info-value">{{ course.lab_name }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">地点</span>
            <span class="info-value">{{ course.location }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">时间</span>
            <span class="info-value">{{ course.course_time }}</span>
          </div>
        </div>
        <div class="card-footer">
          <div class="capacity-bar">
            <div class="bar-fill" :style="{ width: (course.selected_count / course.max_count * 100) + '%' }"></div>
          </div>
          <span class="capacity-text">{{ course.selected_count }} / {{ course.max_count }} 人</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCourseList } from '../../api/course'

const router = useRouter()
const courseList = ref([])

const user = computed(() => JSON.parse(localStorage.getItem('user') || '{}'))

const loadCourses = async () => {
  try {
    const result = await getCourseList()
    if (result.success) {
      courseList.value = result.data.filter(c => c.teacher_name === user.value.name)
    }
  } catch (error) {
    ElMessage.error('加载课程列表失败')
  }
}

const viewStudentList = (course) => {
  router.push(`/teacher/student-list/${course.id}`)
}

onMounted(() => { loadCourses() })
</script>

<style scoped>
.count { font-size: 0.82rem; color: var(--color-text-muted); }

.course-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--space-md);
}

@media (max-width: 640px) {
  .course-grid {
    grid-template-columns: 1fr;
  }
}

.course-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border-soft);
  border-radius: var(--radius-md);
  padding: var(--space-lg);
  transition: all var(--duration-fast) var(--ease-out);
}

.course-card:hover {
  border-color: var(--color-border);
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.card-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: var(--space-md);
}

.course-name {
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--color-text);
}

.card-body {
  display: flex;
  flex-direction: column;
  margin-bottom: var(--space-md);
}

.info-row {
  display: flex;
  padding: 4px 0;
  font-size: 0.85rem;
}

.info-label {
  width: 56px;
  color: var(--color-text-muted);
  flex-shrink: 0;
}

.info-value {
  color: var(--color-text-soft);
}

.card-footer {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding-top: var(--space-sm);
  border-top: 1px solid var(--color-border-soft);
}

.capacity-bar {
  flex: 1;
  height: 4px;
  background: var(--color-border-soft);
  border-radius: 2px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  background: var(--color-accent);
  border-radius: 2px;
  transition: width var(--duration-slow) var(--ease-out);
}

.capacity-text {
  font-size: 0.78rem;
  color: var(--color-text-muted);
  white-space: nowrap;
}
</style>