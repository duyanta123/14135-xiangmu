<template>
  <div class="layout-root">
    <div v-if="sidebarOpen" class="sidebar-overlay" @click="sidebarOpen = false"></div>

    <aside :class="['sidebar', { open: sidebarOpen }]">
      <div class="sidebar-brand">
        <svg width="28" height="28" viewBox="0 0 36 36" fill="none">
          <rect width="36" height="36" rx="8" fill="#c88d2c"/>
          <path d="M10 14h16M10 20h12M10 26h8" stroke="#fff" stroke-width="2" stroke-linecap="round"/>
        </svg>
        <div>
          <span class="brand-title">实验选课</span>
          <span class="brand-sub">教师端</span>
        </div>
      </div>

      <div class="user-info">
        <div class="avatar">{{ user.name?.charAt(0) }}</div>
        <div class="user-detail">
          <p class="user-name">{{ user.name }}</p>
          <p class="user-role">{{ user.title }}</p>
        </div>
      </div>

      <nav class="sidebar-nav">
        <router-link to="/teacher/course" class="nav-item" active-class="active" @click="sidebarOpen = false">
          <span class="nav-icon">📖</span>
          <span>我的课程</span>
        </router-link>
        <router-link to="/teacher/score" class="nav-item" active-class="active" @click="sidebarOpen = false">
          <span class="nav-icon">📝</span>
          <span>成绩录入</span>
        </router-link>
        <router-link to="/teacher/attendance" class="nav-item" active-class="active" @click="sidebarOpen = false">
          <span class="nav-icon">✅</span>
          <span>考勤录入</span>
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <button class="logout-btn" @click="handleLogout">
          <span>退出登录</span>
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
            <path d="M6 2H3v12h3M11 11l3-3-3-3M14 8H6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>
      </div>
    </aside>

    <main class="main-area">
      <div class="mobile-topbar">
        <button class="hamburger" @click="sidebarOpen = !sidebarOpen" aria-label="菜单">
          <svg width="22" height="22" viewBox="0 0 22 22" fill="none">
            <path d="M3 6h16M3 11h16M3 16h16" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </button>
        <span class="topbar-title">教师端</span>
      </div>

      <router-view />
    </main>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const sidebarOpen = ref(false)
const user = computed(() => {
  try { return JSON.parse(localStorage.getItem('user') || '{}') } catch { return {} }
})

const handleLogout = async () => {
  const BFF_ENABLED = import.meta.env.VITE_BFF_ENABLED !== 'false'
  if (BFF_ENABLED) {
    try { await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' }) } catch { /* 静默 */ }
  }
  localStorage.removeItem('user')
  router.push('/login')
}
</script>

<style scoped>
.layout-root {
  display: flex;
  min-height: 100vh;
  background: var(--color-bg);
}

.sidebar {
  width: 240px;
  background: var(--color-primary);
  display: flex;
  flex-direction: column;
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  z-index: 200;
  overflow-y: auto;
  transition: transform var(--duration-normal) var(--ease-out);
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px var(--space-lg);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.brand-title {
  display: block;
  font-size: 0.95rem;
  font-weight: 700;
  color: #fff;
  line-height: 1.2;
}

.brand-sub {
  display: block;
  font-size: 0.7rem;
  color: rgba(255, 255, 255, 0.45);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: var(--space-lg);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.avatar {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.12);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1rem;
  font-weight: 700;
  color: #fff;
  flex-shrink: 0;
}

.user-detail { min-width: 0; }

.user-name {
  font-size: 0.9rem;
  font-weight: 600;
  color: #fff;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-role {
  font-size: 0.72rem;
  color: rgba(255, 255, 255, 0.45);
  margin-top: 2px;
}

.sidebar-nav {
  flex: 1;
  padding: var(--space-sm) 0;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 20px;
  margin: 2px 8px;
  border-radius: var(--radius-sm);
  color: rgba(255, 255, 255, 0.65);
  text-decoration: none;
  font-size: 0.88rem;
  font-weight: 500;
  transition: all var(--duration-fast) var(--ease-out);
}

.nav-item:hover {
  color: #fff;
  background: rgba(255, 255, 255, 0.06);
}

.nav-item.active {
  color: #fff;
  background: rgba(255, 255, 255, 0.1);
  font-weight: 600;
}

.nav-icon {
  font-size: 1.1rem;
  width: 24px;
  text-align: center;
}

.sidebar-footer {
  padding: var(--space-md);
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.logout-btn {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 10px 16px;
  border: none;
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.06);
  color: rgba(255, 255, 255, 0.6);
  font-size: 0.84rem;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
}

.logout-btn:hover {
  background: rgba(166, 27, 46, 0.25);
  color: #f87171;
}

.main-area {
  flex: 1;
  margin-left: 240px;
  padding: var(--space-lg);
  min-height: 100vh;
}

.mobile-topbar {
  display: none;
  align-items: center;
  gap: var(--space-sm);
  padding: 10px 0;
  margin-bottom: var(--space-md);
}

.hamburger {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text);
  cursor: pointer;
  box-shadow: var(--shadow-sm);
}

.topbar-title {
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--color-text);
}

.sidebar-overlay {
  display: none;
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
  z-index: 150;
  animation: fadeIn var(--duration-fast) var(--ease-out);
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@media (max-width: 768px) {
  .sidebar {
    transform: translateX(-100%);
  }

  .sidebar.open {
    transform: translateX(0);
  }

  .sidebar-overlay {
    display: block;
  }

  .main-area {
    margin-left: 0;
    padding: var(--space-md);
  }

  .mobile-topbar {
    display: flex;
  }
}
</style>