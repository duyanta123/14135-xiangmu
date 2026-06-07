<template>
  <div class="page-container">
    <div class="page-header">
      <h2>我的课程</h2>
      <span class="course-count">共 {{ myCourseList.length }} 门课程</span>
    </div>

    <div v-if="myCourseList.length === 0" class="empty-state">
      <span class="empty-icon">📚</span>
      <p>尚未选课，去课程列表看看吧</p>
    </div>

    <div v-else class="course-grid">
      <div v-for="course in myCourseList" :key="course.selection_id" class="course-card">
        <div class="card-top">
          <div class="course-id">{{ course.course_name }}</div>
          <el-button type="danger" size="small" plain @click="handleDropCourse(course)">退课</el-button>
        </div>
        <div class="card-body">
          <div class="info-row">
            <span class="info-label">教师</span>
            <span class="info-value">{{ course.teacher_name }}</span>
          </div>
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
          <div class="info-row">
            <span class="info-label">选课时间</span>
            <span class="info-value">{{ course.select_time }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyCourses, deleteSelection } from '../../api/selection'
import { clearScheduleCache } from '../../utils/scheduleCache'
import { notifyScheduleUpdate } from '../../utils/scheduleEventBus'

const myCourseList = ref([])

const bustScheduleCache = () => {
  clearScheduleCache()
  notifyScheduleUpdate()
}

const loadMyCourses = async () => {
  try {
    const result = await getMyCourses()
    if (result.success) {
      myCourseList.value = result.data
    }
  } catch (error) {
    ElMessage.error('加载我的课程失败')
  }
}

const handleDropCourse = async (course) => {
  try {
    await ElMessageBox.confirm('确定要退课吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const result = await deleteSelection(course.selection_id)
    if (result.success) {
      ElMessage.success('退课成功')
      bustScheduleCache()
      loadMyCourses()
    } else {
      ElMessage.error(result.message)
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('退课失败')
    }
  }
}

onMounted(() => { loadMyCourses() })
</script>

<style scoped>
.course-count {
  font-size: 0.82rem;
  color: var(--color-text-muted);
}

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

.course-id {
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--color-text);
}

.card-body {
  display: flex;
  flex-direction: column;
}

.info-row {
  display: flex;
  padding: 4px 0;
  font-size: 0.85rem;
}

.info-label {
  width: 64px;
  color: var(--color-text-muted);
  flex-shrink: 0;
}

.info-value {
  color: var(--color-text-soft);
}
</style>