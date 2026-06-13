import { describe, it, expect } from 'vitest'
import { config } from '../src/config.js'

describe('配置模块', () => {
  it('应该有正确的默认端口', () => {
    expect(config.port).toBe(4000)
  })

  it('应该有正确的后端 URL', () => {
    expect(config.backendUrl).toBe('http://localhost:8080')
  })

  it('应该有正确的 JWT Cookie 名称', () => {
    expect(config.jwt.cookieName).toBe('bff_token')
  })

  it('JWT Cookie 最大年龄应该是 24 小时', () => {
    expect(config.jwt.cookieMaxAge).toBe(24 * 60 * 60 * 1000)
  })

  it('JWT 密钥应该与 Spring Boot 一致', () => {
    expect(config.jwt.secret).toBe('lab-course-system-secret-key-2024-very-long-secret')
  })

  it('JWT 过期时间应该是 86400000ms', () => {
    expect(config.jwt.expiration).toBe(86400000)
  })
})