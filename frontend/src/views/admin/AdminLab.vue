<template>
  <div class="page-container">
    <div class="page-header">
      <h2>实验室管理</h2>
      <el-button type="primary" @click="handleAdd">添加实验室</el-button>
    </div>

    <div class="table-responsive">
      <el-table :data="labList" stripe>
        <el-table-column prop="labName" label="实验室名称" min-width="200" />
        <el-table-column prop="location" label="地点" min-width="200" />
        <el-table-column prop="capacity" label="容量" width="120" />
        <el-table-column label="操作" width="180" align="center">
          <template #default="scope">
            <el-button type="primary" size="small" text @click="handleEdit(scope.row)">编辑</el-button>
            <el-button type="danger" size="small" text @click="handleDelete(scope.row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="460px" :close-on-click-modal="false">
      <el-form ref="labFormRef" :model="labForm" :rules="labRules" label-width="88px" status-icon>
        <el-form-item label="实验室名称" prop="labName">
          <el-input v-model="labForm.labName" placeholder="请输入名称" />
        </el-form-item>
        <el-form-item label="地点" prop="location">
          <el-input v-model="labForm.location" placeholder="请输入地点" />
        </el-form-item>
        <el-form-item label="容量" prop="capacity">
          <el-input-number v-model="labForm.capacity" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getLabList, addLab, updateLab, deleteLab } from '../../api/lab'

const labList = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('添加实验室')
const labFormRef = ref(null)
const labForm = ref({ id: null, labName: '', location: '', capacity: 30 })

const labRules = {
  labName: [
    { required: true, message: '请输入实验室名称', trigger: 'blur' }
  ],
  location: [
    { required: true, message: '请输入地点', trigger: 'blur' }
  ],
  capacity: [
    { required: true, message: '请输入容量', trigger: 'blur' },
    { type: 'number', min: 1, max: 200, message: '容量范围为1-200', trigger: 'blur' }
  ]
}

const logFormOperation = (action, entity, data) => {
  const timestamp = new Date().toISOString()
  console.log(
    `[表单操作] ${timestamp} | 操作: ${action} | 实体: ${entity} | ` +
    `数据: ${JSON.stringify(data)}`
  )
}

const loadLabs = async () => {
  try {
    const result = await getLabList()
    if (result.success) labList.value = result.data
  } catch (error) { ElMessage.error('加载实验室列表失败') }
}

const handleAdd = () => {
  dialogTitle.value = '添加实验室'
  labForm.value = { id: null, labName: '', location: '', capacity: 30 }
  labFormRef.value?.resetFields()
  dialogVisible.value = true
}

const handleEdit = (row) => {
  dialogTitle.value = '编辑实验室'
  labForm.value = { ...row }
  labFormRef.value?.resetFields()
  dialogVisible.value = true
}

const handleSave = async () => {
  try {
    await labFormRef.value.validate()
  } catch {
    return
  }
  try {
    const isUpdate = !!labForm.value.id
    const result = isUpdate
      ? await updateLab(labForm.value)
      : await addLab(labForm.value)
    if (result.success) {
      logFormOperation(isUpdate ? '编辑' : '添加', '实验室', {
        id: labForm.value.id,
        labName: labForm.value.labName,
        location: labForm.value.location,
        capacity: labForm.value.capacity
      })
      ElMessage.success('保存成功')
      dialogVisible.value = false
      loadLabs()
    } else {
      ElMessage.error(result.message)
    }
  } catch (error) { ElMessage.error('保存失败') }
}

const handleDelete = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除吗？', '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    const result = await deleteLab(id)
    if (result.success) { ElMessage.success('删除成功'); loadLabs() }
    else { ElMessage.error(result.message) }
  } catch (error) { if (error !== 'cancel') ElMessage.error('删除失败') }
}

onMounted(() => { loadLabs() })
</script>

<style scoped>
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