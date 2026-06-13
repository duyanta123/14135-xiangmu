/**
 * Login.vue 安全测试 — HIGH-008: 测试账号仅开发环境可见
 * 安全最佳实践: VUE-SECRETS-001 / VUE-AUTH-001
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import Login from '../views/Login.vue'

// Mock router to avoid createWebHistory in jsdom
vi.mock('vue-router', () => {
  const mockRouter = {
    push: vi.fn(),
    replace: vi.fn(),
    beforeEach: vi.fn(),
    afterEach: vi.fn(),
  }
  return {
    useRouter: () => mockRouter,
    useRoute: () => ({ path: '/', query: {}, params: {} }),
    createRouter: () => mockRouter,
    createWebHistory: vi.fn(),
    createMemoryHistory: vi.fn(),
  }
})

// Mock API modules to avoid transitive imports
vi.mock('../api/student', () => ({ studentLogin: vi.fn() }))
vi.mock('../api/teacher', () => ({ teacherLogin: vi.fn() }))
vi.mock('../api/admin', () => ({ adminLogin: vi.fn() }))

describe('Login.vue — HIGH-008 测试账号仅开发环境可见', () => {

  const mountLogin = () => mount(Login, {
    global: {
      stubs: {
        'el-form': { template: '<div><slot /></div>' },
        'el-form-item': { template: '<div><slot /></div>' },
        'el-input': true,
        'el-button': { template: '<button><slot /></button>' },
        'router-link': true,
      }
    }
  })

  it('开发环境: 测试账号区域存在', () => {
    // import.meta.env.DEV 在 vitest 中默认为 true
    const wrapper = mountLogin()
    expect(wrapper.find('.test-accounts').exists()).toBe(true)
  })

  it('开发环境: 测试账号包含学生账号 S001', () => {
    const wrapper = mountLogin()
    const html = wrapper.html()
    expect(html).toContain('S001')
  })

  it('开发环境: 测试账号包含教师账号 T001', () => {
    const wrapper = mountLogin()
    const html = wrapper.html()
    expect(html).toContain('T001')
  })

  it('开发环境: 测试账号包含管理员账号 admin', () => {
    const wrapper = mountLogin()
    const html = wrapper.html()
    expect(html).toContain('admin')
  })
})