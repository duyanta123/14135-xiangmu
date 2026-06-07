<template>
  <div class="page-container">
    <div class="page-header">
      <div class="header-left">
        <el-button text @click="goBack" class="back-btn">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M10 3L5 8l5 5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>
          返回
        </el-button>
        <h2>学生名单</h2>
      </div>
      <span class="count">{{ studentList.length }} 名学生</span>
    </div>

    <div v-if="studentList.length === 0" class="empty-state">
      <span class="empty-icon">🎓</span>
      <p>暂无学生选课</p>
    </div>

    <div v-else class="table-responsive">
      <el-table :data="studentList" stripe>
        <el-table-column prop="student_no" label="学号" width="150" />
        <el-table-column prop="name" label="姓名" width="120" />
        <el-table-column prop="gender" label="性别" width="80" />
        <el-table-column prop="major" label="专业" width="200" />
        <el-table-column prop="select_time" label="选课时间" min-width="180" />
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getStudentList } from '../../api/selection'

const route = useRoute()
const router = useRouter()
const studentList = ref([])

const loadStudentList = async () => {
  try {
    const result = await getStudentList(route.params.courseId)
    if (result.success) {
      studentList.value = result.data
    }
  } catch (error) {
    ElMessage.error('加载学生名单失败')
  }
}

const goBack = () => { router.back() }

onMounted(() => { loadStudentList() })
</script>

<style scoped>
.header-left {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 0.88rem;
  padding: 4px 8px;
}

.count { font-size: 0.82rem; color: var(--color-text-muted); }
</style>