<template>
  <div class="page-container">
    <div class="page-header">
      <h2>课程列表</h2>
      <span class="course-count">共 {{ courseList.length }} 门课程</span>
    </div>

    <div v-if="courseList.length === 0" class="empty-state">
      <span class="empty-icon">📋</span>
      <p>暂无可用课程</p>
    </div>

    <div v-else class="course-grid">
      <div v-for="course in courseList" :key="course.id" class="course-card">
        <div class="card-top">
          <div class="course-id">{{ course.course_name }}</div>
          <el-tag :type="course.selected_count >= course.max_count ? 'danger' : 'success'" size="small" effect="plain">
            {{ course.selected_count >= course.max_count ? '已满' : '可选' }}
          </el-tag>
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
        </div>
        <div class="card-footer">
          <div class="capacity-bar">
            <div class="bar-fill" :style="{ width: (course.selected_count / course.max_count * 100) + '%' }"></div>
          </div>
          <span class="capacity-text">{{ course.selected_count }} / {{ course.max_count }}</span>
          <el-button
            type="primary"
            size="small"
            :disabled="course.selected_count >= course.max_count"
            @click="handleSelectCourse(course)"
          >
            选课
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCourseList } from '../../api/course'
import { addSelection, getMyCourses } from '../../api/selection'
import { detectConflictWithList } from '../../utils/scheduleParser'
import { clearScheduleCache } from '../../utils/scheduleCache'
import { notifyScheduleUpdate } from '../../utils/scheduleEventBus'

const courseList = ref([])

const bustScheduleCache = () => {
  clearScheduleCache()
  notifyScheduleUpdate()
}

const loadCourses = async () => {
  try {
    const result = await getCourseList()
    if (result.success) {
      courseList.value = result.data
    }
  } catch (error) {
    ElMessage.error('加载课程列表失败')
  }
}

const handleSelectCourse = async (course) => {
  try {
    // 冲突检测：获取已选课程，检查时间冲突
    const myResult = await getMyCourses()
    if (myResult.success && myResult.data.length > 0) {
      const conflict = detectConflictWithList(course.course_time, myResult.data)
      if (conflict.hasConflict) {
        const conflictNames = conflict.conflictDetails.map(c => `「${c.courseName}」(${c.detail})`).join('\n')
        await ElMessageBox.confirm(
          `该课程与以下已选课程时间冲突：\n\n${conflictNames}\n\n确定仍要选课吗？`,
          '时间冲突提示',
          {
            confirmButtonText: '仍要选课',
            cancelButtonText: '取消',
            type: 'warning',
            dangerouslyUseHTMLString: false
          }
        )
      }
    }

    const result = await addSelection({ courseId: course.id })
    if (result.success) {
      ElMessage.success('选课成功')
      bustScheduleCache()
      loadCourses()
    } else {
      ElMessage.error(result.message)
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('选课失败')
    }
  }
}

onMounted(() => { loadCourses() })
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