<template>
  <div class="page-container">
    <div class="page-header">
      <h2>学生管理</h2>
      <el-button type="primary" @click="handleAdd">添加学生</el-button>
    </div>

    <div class="table-responsive">
      <el-table :data="studentList" stripe>
        <el-table-column prop="studentNo" label="学号" width="150" />
        <el-table-column prop="name" label="姓名" width="120" />
        <el-table-column prop="gender" label="性别" width="80" />
        <el-table-column prop="major" label="专业" min-width="200" />
        <el-table-column label="操作" width="260" align="center">
          <template #default="scope">
            <el-button type="primary" size="small" text @click="handleEdit(scope.row)">编辑</el-button>
            <el-button type="warning" size="small" text @click="handleResetPassword(scope.row)">重置密码</el-button>
            <el-button type="danger" size="small" text @click="handleDelete(scope.row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="480px" :close-on-click-modal="false">
      <el-form ref="studentFormRef" :model="studentForm" :rules="studentRules" label-width="72px" status-icon>
        <el-form-item label="学号" prop="studentNo">
          <el-input v-model="studentForm.studentNo" placeholder="请输入学号" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="studentForm.name" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="性别" prop="gender">
          <el-select v-model="studentForm.gender" style="width:100%" placeholder="请选择性别">
            <el-option label="男" value="男" />
            <el-option label="女" value="女" />
          </el-select>
        </el-form-item>
        <el-form-item label="专业" prop="major">
          <el-input v-model="studentForm.major" placeholder="请输入专业" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="studentForm.password" type="password" placeholder="留空则不修改密码" show-password />
          <span class="form-hint">留空则保持原密码不变</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码弹窗 -->
    <el-dialog v-model="passwordDialogVisible" title="重置密码" width="420px" :close-on-click-modal="false">
      <div class="reset-password-info">
        <span class="reset-label">学号：</span>
        <strong>{{ resetTarget.studentNo }}</strong>
      </div>
      <div class="reset-password-info">
        <span class="reset-label">姓名：</span>
        <strong>{{ resetTarget.name }}</strong>
      </div>
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="80px" class="reset-form" status-icon>
        <el-form-item label="新密码" prop="password">
          <el-input v-model="passwordForm.password" type="password" placeholder="请输入新密码" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" placeholder="请再次输入新密码" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handlePasswordSave">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getStudentList, addStudent, updateStudent, deleteStudent } from '../../api/student'
import {
  validatePassword,
  validateConfirmPassword,
  logPasswordValidation,
  getPasswordValidationError
} from '../../utils/passwordValidator'

const studentList = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('添加学生')
const studentFormRef = ref(null)
const studentForm = ref({ id: null, studentNo: '', name: '', gender: '', major: '', password: '' })

const studentRules = {
  studentNo: [
    { required: true, message: '请输入学号', trigger: 'blur' },
    { pattern: /^\S+$/, message: '学号不能包含空格', trigger: 'blur' }
  ],
  name: [
    { required: true, message: '请输入姓名', trigger: 'blur' }
  ],
  gender: [
    { required: true, message: '请选择性别', trigger: 'change' }
  ],
  major: [
    { required: true, message: '请输入专业', trigger: 'blur' }
  ],
  password: [
    {
      validator: (_rule, value, callback) => {
        if (!value || value === '') {
          callback()
          return
        }
        validatePassword(_rule, value, callback)
      },
      trigger: 'blur'
    }
  ]
}

const passwordDialogVisible = ref(false)
const passwordFormRef = ref(null)
const resetTarget = ref({ id: null, studentNo: '', name: '' })
const passwordForm = reactive({ password: '', confirmPassword: '' })

const passwordRules = {
  password: [
    { validator: validatePassword, trigger: ['blur', 'change'] }
  ],
  confirmPassword: [
    {
      validator: (_rule, value, callback) => {
        validateConfirmPassword(_rule, value, callback, passwordForm.password)
      },
      trigger: ['blur', 'change']
    }
  ]
}

const loadStudents = async () => {
  try {
    const result = await getStudentList()
    if (result.success) studentList.value = result.data
  } catch (error) { ElMessage.error('加载学生列表失败') }
}

const handleAdd = () => {
  dialogTitle.value = '添加学生'
  studentForm.value = { id: null, studentNo: '', name: '', gender: '', major: '', password: '' }
  studentFormRef.value?.resetFields()
  dialogVisible.value = true
}

const handleEdit = (row) => {
  dialogTitle.value = '编辑学生'
  studentForm.value = { ...row, password: '' }
  studentFormRef.value?.resetFields()
  dialogVisible.value = true
}

const handleSave = async () => {
  try {
    await studentFormRef.value.validate()
  } catch {
    return
  }
  try {
    const isUpdate = !!studentForm.value.id
    const result = isUpdate
      ? await updateStudent(studentForm.value)
      : await addStudent(studentForm.value)
    if (result.success) {
      ElMessage.success('保存成功')
      dialogVisible.value = false
      loadStudents()
    } else {
      ElMessage.error(result.message)
    }
  } catch (error) { ElMessage.error('保存失败') }
}

const handleResetPassword = (row) => {
  resetTarget.value = { id: row.id, studentNo: row.studentNo, name: row.name }
  passwordForm.password = ''
  passwordForm.confirmPassword = ''
  passwordFormRef.value?.resetFields()
  passwordDialogVisible.value = true
}

const handlePasswordSave = async () => {
  try {
    await passwordFormRef.value.validate()
  } catch {
    const error = getPasswordValidationError(passwordForm.password)
    logPasswordValidation('student', resetTarget.value.studentNo, resetTarget.value.name, passwordForm.password, false, error)
    return
  }
  logPasswordValidation('student', resetTarget.value.studentNo, resetTarget.value.name, passwordForm.password, true, null)
  try {
    await ElMessageBox.confirm(
      `确定要重置学生 ${resetTarget.value.name}（${resetTarget.value.studentNo}）的密码吗？`,
      '确认重置密码',
      { confirmButtonText: '确定重置', cancelButtonText: '取消', type: 'warning' }
    )
    const result = await updateStudent({
      id: resetTarget.value.id,
      studentNo: resetTarget.value.studentNo,
      name: resetTarget.value.name,
      password: passwordForm.password
    })
    if (result.success) {
      ElMessage.success('密码重置成功')
      passwordDialogVisible.value = false
    } else {
      ElMessage.error(result.message || '密码重置失败')
    }
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('密码重置失败')
  }
}

const handleDelete = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除吗？', '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    const result = await deleteStudent(id)
    if (result.success) { ElMessage.success('删除成功'); loadStudents() }
    else { ElMessage.error(result.message) }
  } catch (error) { if (error !== 'cancel') ElMessage.error('删除失败') }
}

onMounted(() => { loadStudents() })
</script>

<style scoped>
.form-hint {
  display: block;
  font-size: 0.75rem;
  color: var(--color-text-muted);
  margin-top: 4px;
}

.reset-password-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  margin-bottom: 16px;
  background: var(--color-border-soft);
  border-radius: var(--radius-sm);
  font-size: 0.9rem;
}

.reset-label {
  color: var(--color-text-muted);
  font-size: 0.85rem;
}

.reset-form {
  margin-top: 4px;
}

.reset-form :deep(.el-form-item__error) {
  font-size: 0.78rem;
  color: #f56c6c;
  line-height: 1.4;
  padding-top: 2px;
}

.reset-form :deep(.el-form-item.is-error .el-input__wrapper) {
  box-shadow: 0 0 0 1px #f56c6c inset;
}

.reset-form :deep(.el-form-item.is-success .el-input__wrapper) {
  box-shadow: 0 0 0 1px #67c23a inset;
}

/* 编辑表单统一校验样式 */
.el-form :deep(.el-form-item__error) {
  font-size: 0.78rem;
  color: #f56c6c;
  line-height: 1.4;
  padding-top: 2px;
}

.el-form :deep(.el-form-item.is-error .el-input__wrapper),
.el-form :deep(.el-form-item.is-error .el-select .el-input__wrapper) {
  box-shadow: 0 0 0 1px #f56c6c inset;
}

.el-form :deep(.el-form-item.is-success .el-input__wrapper),
.el-form :deep(.el-form-item.is-success .el-select .el-input__wrapper) {
  box-shadow: 0 0 0 1px #67c23a inset;
}
</style>