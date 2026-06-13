import { describe, it, expect } from 'vitest'
import { proxyMapping } from '../src/proxy/proxyMapping.js'

describe('代理路由映射', () => {
  it('登录接口应该是公开的', () => {
    expect(proxyMapping.requiresAuth('/api/student/login')).toBe(false)
    expect(proxyMapping.requiresAuth('/api/teacher/login')).toBe(false)
    expect(proxyMapping.requiresAuth('/api/admin/login')).toBe(false)
  })

  it('课程列表应该是公开的', () => {
    expect(proxyMapping.requiresAuth('/api/course/list')).toBe(false)
    expect(proxyMapping.requiresAuth('/api/course/list/simple')).toBe(false)
  })

  it('选课接口需要认证', () => {
    expect(proxyMapping.requiresAuth('/api/selection/my')).toBe(true)
    expect(proxyMapping.requiresAuth('/api/selection/add')).toBe(true)
  })

  it('考勤接口需要认证', () => {
    expect(proxyMapping.requiresAuth('/api/attendance/check-in')).toBe(true)
    expect(proxyMapping.requiresAuth('/api/attendance/history')).toBe(true)
  })

  it('学生管理接口需要认证', () => {
    expect(proxyMapping.requiresAuth('/api/student/list')).toBe(true)
  })

  it('教师管理接口需要认证', () => {
    expect(proxyMapping.requiresAuth('/api/teacher/list')).toBe(true)
  })

  it('管理员接口需要认证', () => {
    expect(proxyMapping.requiresAuth('/api/admin/login')).toBe(false)
    // admin/login 是公开的，但其他 admin 接口需要认证
    expect(proxyMapping.requiresAuth('/api/admin/dashboard')).toBe(true)
  })

  it('未知路径默认需要认证', () => {
    expect(proxyMapping.requiresAuth('/api/unknown/endpoint')).toBe(true)
  })
})