<template>
  <div class="login-page">
    <div class="login-bg">
      <div class="bg-shape shape-1"></div>
      <div class="bg-shape shape-2"></div>
      <div class="bg-shape shape-3"></div>
    </div>

    <div class="login-card">
      <div class="card-header">
        <div class="logo-mark">
          <svg width="36" height="36" viewBox="0 0 36 36" fill="none">
            <rect width="36" height="36" rx="8" fill="#1a2744"/>
            <path d="M10 14h16M10 20h12M10 26h8" stroke="#c88d2c" stroke-width="2.5" stroke-linecap="round"/>
          </svg>
        </div>
        <h1>实验选课系统</h1>
        <p class="subtitle">Laboratory Course Selection</p>
      </div>

      <div class="role-tabs">
        <button
          v-for="role in roles"
          :key="role.value"
          :class="['role-tab', { active: loginForm.role === role.value }]"
          @click="loginForm.role = role.value"
        >
          <span class="tab-icon">{{ role.icon }}</span>
          <span class="tab-label">{{ role.label }}</span>
        </button>
      </div>

      <el-form :model="loginForm" class="login-form" @keyup.enter="handleLogin">
        <el-form-item>
          <el-input
            v-model="loginForm.account"
            :placeholder="accountPlaceholder"
            size="large"
            prefix-icon="User"
          />
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="login-btn"
            :loading="loading"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="card-footer">
        <details v-if="isDev" class="test-accounts">
          <summary>测试账号</summary>
          <div class="accounts-grid">
            <div class="account-item">
              <span class="role-badge student">学生</span>
              <code>S001</code>
              <span class="divider">/</span>
              <code>123456</code>
            </div>
            <div class="account-item">
              <span class="role-badge teacher">教师</span>
              <code>T001</code>
              <span class="divider">/</span>
              <code>123456</code>
            </div>
            <div class="account-item">
              <span class="role-badge admin">管理员</span>
              <code>admin</code>
              <span class="divider">/</span>
              <code>123456</code>
            </div>
          </div>
        </details>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { studentLogin } from '../api/student'
import { teacherLogin } from '../api/teacher'
import { adminLogin } from '../api/admin'

const router = useRouter()
const loading = ref(false)

const roles = [
  { value: 'student', label: '学生', icon: '🎓' },
  { value: 'teacher', label: '教师', icon: '📖' },
  { value: 'admin',  label: '管理员', icon: '⚙️' },
]

const loginForm = reactive({
  role: 'student',
  account: '',
  password: ''
})

const accountPlaceholder = computed(() => {
  const map = { student: '请输入学号', teacher: '请输入工号', admin: '请输入用户名' }
  return map[loginForm.role] || '请输入账号'
})

// Security fix: 测试账号仅在开发环境可见 (HIGH-008)
const isDev = import.meta.env.DEV

const handleLogin = async () => {
  if (!loginForm.account || !loginForm.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }

  loading.value = true
  try {
    let result
    if (loginForm.role === 'student') {
      result = await studentLogin({ studentNo: loginForm.account, password: loginForm.password })
    } else if (loginForm.role === 'teacher') {
      result = await teacherLogin({ teacherNo: loginForm.account, password: loginForm.password })
    } else {
      result = await adminLogin({ username: loginForm.account, password: loginForm.password })
    }

    if (result.success) {
      const BFF_ENABLED = import.meta.env.VITE_BFF_ENABLED !== 'false'
      // Security fix (HIGH-003): BFF模式下不存储身份信息到localStorage
      // Token由HttpOnly Cookie管理，身份信息通过API获取
      const userData = BFF_ENABLED
        ? { _bffMode: true, token: 'bff-cookie' }
        : {
            ...result.data,
            role: loginForm.role,
            token: result.token,
            tokenExpireTime: result.data?.tokenExpireTime || (Date.now() + 86400 * 1000),
          }
      localStorage.setItem('user', JSON.stringify(userData))
      ElMessage.success(`欢迎，${result.data.name || result.data.username}`)
      router.push(`/${loginForm.role}`)
    } else {
      ElMessage.error(result.message || '登录失败')
    }
  } catch (error) {
    const msg = error?.response?.data?.message || '登录失败，请检查网络连接'
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  position: relative;
  width: 100%;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg);
  overflow: hidden;
}

/* --- Background shapes --- */
.login-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.bg-shape {
  position: absolute;
  border-radius: 50%;
  opacity: 0.06;
}

.shape-1 {
  width: 600px;
  height: 600px;
  background: var(--color-primary);
  top: -200px;
  right: -150px;
  animation: floatShape 20s ease-in-out infinite;
}

.shape-2 {
  width: 400px;
  height: 400px;
  background: var(--color-accent);
  bottom: -100px;
  left: -100px;
  animation: floatShape 25s ease-in-out infinite reverse;
}

.shape-3 {
  width: 200px;
  height: 200px;
  background: var(--color-primary-soft);
  top: 50%;
  left: 60%;
  animation: floatShape 18s ease-in-out infinite 5s;
}

@keyframes floatShape {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(30px, -30px) scale(1.05); }
  66% { transform: translate(-20px, 20px) scale(0.95); }
}

/* --- Card --- */
.login-card {
  position: relative;
  width: 420px;
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  padding: var(--space-2xl);
  animation: cardEnter 0.5s var(--ease-out);
}

@keyframes cardEnter {
  from {
    opacity: 0;
    transform: translateY(12px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.card-header {
  text-align: center;
  margin-bottom: var(--space-xl);
}

.logo-mark {
  display: inline-flex;
  margin-bottom: var(--space-md);
}

.card-header h1 {
  font-size: 1.5rem;
  font-weight: 700;
  margin-bottom: 4px;
}

.subtitle {
  font-size: 0.8rem;
  color: var(--color-text-muted);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

/* --- Role Tabs --- */
.role-tabs {
  display: flex;
  gap: var(--space-sm);
  margin-bottom: var(--space-lg);
  padding: 4px;
  background: var(--color-border-soft);
  border-radius: var(--radius-md);
}

.role-tab {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 0;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  cursor: pointer;
  font-size: 0.9rem;
  color: var(--color-text-soft);
  transition: all var(--duration-fast) var(--ease-out);
}

.role-tab:hover {
  color: var(--color-text);
}

.role-tab.active {
  background: var(--color-surface);
  color: var(--color-primary);
  font-weight: 600;
  box-shadow: var(--shadow-sm);
}

.tab-icon {
  font-size: 1rem;
}

/* --- Form --- */
.login-form {
  margin-top: var(--space-sm);
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 1rem;
  letter-spacing: 0.2em;
  border-radius: var(--radius-sm);
}

/* --- Footer --- */
.card-footer {
  margin-top: var(--space-lg);
  text-align: center;
}

.test-accounts {
  font-size: 0.8rem;
  color: var(--color-text-muted);
}

.test-accounts summary {
  cursor: pointer;
  padding: var(--space-sm) 0;
  user-select: none;
}

.test-accounts summary:hover {
  color: var(--color-text-soft);
}

.accounts-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: var(--space-sm);
  padding: var(--space-md);
  background: var(--color-border-soft);
  border-radius: var(--radius-sm);
}

.account-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.8rem;
  color: var(--color-text-soft);
}

.role-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 0.7rem;
  font-weight: 600;
  letter-spacing: 0.03em;
}

.role-badge.student {
  background: #e8f5e9;
  color: var(--color-success);
}

.role-badge.teacher {
  background: #e3f2fd;
  color: var(--color-info);
}

.role-badge.admin {
  background: #fff3e0;
  color: var(--color-warning);
}

.account-item code {
  font-family: 'SF Mono', 'Cascadia Code', monospace;
  color: var(--color-text);
}

.divider {
  color: var(--color-border);
}

/* --- Responsive --- */
@media (max-width: 480px) {
  .login-card {
    width: calc(100vw - 32px);
    padding: var(--space-lg);
    border-radius: var(--radius-md);
  }

  .shape-1 {
    width: 300px;
    height: 300px;
    top: -100px;
    right: -80px;
  }

  .shape-2 {
    width: 200px;
    height: 200px;
    bottom: -60px;
    left: -60px;
  }

  .shape-3 {
    display: none;
  }

  .card-header h1 {
    font-size: 1.25rem;
  }

  .role-tab {
    padding: 8px 0;
    font-size: 0.82rem;
  }

  .tab-icon {
    font-size: 0.9rem;
  }
}
</style>