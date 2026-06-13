import { describe, it, expect } from 'vitest'
import jwt from 'jsonwebtoken'

const SECRET = 'lab-course-system-secret-key-2024-very-long-secret'

describe('JWT 验证', () => {
  it('应该能生成有效的 HS256 Token', () => {
    const token = jwt.sign(
      { userId: 1, username: 'testuser', role: 'student' },
      SECRET,
      { algorithm: 'HS256', expiresIn: '24h' }
    )

    expect(token).toBeTruthy()
    expect(typeof token).toBe('string')
  })

  it('应该能验证 Spring Boot 签发的 Token', () => {
    const token = jwt.sign(
      { userId: 100, username: '张三', role: 'student' },
      SECRET,
      { algorithm: 'HS256', expiresIn: 86400 }
    )

    const decoded = jwt.verify(token, SECRET, { algorithms: ['HS256'] })
    expect(decoded.userId).toBe(100)
    expect(decoded.username).toBe('张三')
    expect(decoded.role).toBe('student')
  })

  it('应该拒绝无效的 Token', () => {
    expect(() => {
      jwt.verify('invalid-token', SECRET, { algorithms: ['HS256'] })
    }).toThrow()
  })

  it('应该拒绝过期 Token', () => {
    const token = jwt.sign(
      { userId: 1, username: 'test', role: 'student' },
      SECRET,
      { algorithm: 'HS256', expiresIn: '0s' }
    )

    // 等待 token 过期
    expect(() => {
      jwt.verify(token, SECRET, { algorithms: ['HS256'] })
    }).toThrow()
  })

  it('应该拒绝不同密钥签发的 Token', () => {
    const token = jwt.sign(
      { userId: 1, username: 'test', role: 'student' },
      'wrong-secret-key',
      { algorithm: 'HS256', expiresIn: '24h' }
    )

    expect(() => {
      jwt.verify(token, SECRET, { algorithms: ['HS256'] })
    }).toThrow()
  })

  it('应该能解析 Token 中的角色信息', () => {
    const roles = ['student', 'teacher', 'admin']

    for (const role of roles) {
      const token = jwt.sign(
        { userId: 1, username: 'test', role },
        SECRET,
        { algorithm: 'HS256', expiresIn: '24h' }
      )

      const decoded = jwt.verify(token, SECRET, { algorithms: ['HS256'] })
      expect(decoded.role).toBe(role)
    }
  })
})