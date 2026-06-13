/**
 * 简化版 TokenManager
 * BFF 模式下 Token 由 HttpOnly Cookie 管理，前端不持有 Token
 * 降级模式（VITE_BFF_ENABLED=false）下保持原有逻辑
 */

const BFF_ENABLED = import.meta.env.VITE_BFF_ENABLED !== 'false'

const TOKEN_REFRESH_THRESHOLD = 10 * 60 // 10 分钟（秒）

class TokenManager {
  constructor() {
    this.refreshPromise = null
  }

  getToken() {
    if (BFF_ENABLED) return null // Cookie 自动携带
    const user = JSON.parse(localStorage.getItem('user') || '{}')
    return user.token || null
  }

  getTokenExpireTime() {
    const user = JSON.parse(localStorage.getItem('user') || '{}')
    return user.tokenExpireTime || 0
  }

  setToken(token, expiresIn) {
    if (BFF_ENABLED) return // Cookie 由 BFF 管理
    const user = JSON.parse(localStorage.getItem('user') || '{}')
    user.token = token
    user.tokenExpireTime = Date.now() + expiresIn * 1000
    localStorage.setItem('user', JSON.stringify(user))
  }

  setUser(userInfo) {
    localStorage.setItem('user', JSON.stringify(userInfo))
  }

  getUser() {
    return JSON.parse(localStorage.getItem('user') || '{}')
  }

  isTokenExpired() {
    if (BFF_ENABLED) return false
    const expireTime = this.getTokenExpireTime()
    if (!expireTime) return true
    return Date.now() >= expireTime
  }

  isTokenAboutToExpire() {
    if (BFF_ENABLED) return false // BFF 端自动刷新
    const expireTime = this.getTokenExpireTime()
    if (!expireTime) return true
    const remainingTime = (expireTime - Date.now()) / 1000
    return remainingTime < TOKEN_REFRESH_THRESHOLD
  }

  async refreshTokenIfNeeded() {
    if (BFF_ENABLED) {
      try {
        await fetch('/api/auth/refresh', {
          method: 'POST',
          credentials: 'include',
        })
      } catch (err) {
        console.warn('[TokenManager] BFF Token 刷新失败:', err.message)
        // 静默失败，下次请求会触发 401
      }
      return null
    }

    if (!this.isTokenAboutToExpire()) {
      return this.getToken()
    }

    if (this.refreshPromise) {
      await this.refreshPromise
      return this.getToken()
    }

    this.refreshPromise = this.doRefreshToken()

    try {
      await this.refreshPromise
      return this.getToken()
    } finally {
      this.refreshPromise = null
    }
  }

  async doRefreshToken() {
    try {
      const currentToken = this.getToken()
      if (!currentToken) {
        throw new Error('No token available')
      }

      const axios = (await import('axios')).default
      const response = await axios.post('/api/auth/refresh', {}, {
        headers: {
          Authorization: `Bearer ${currentToken}`
        }
      })

      if (response.data.success) {
        this.setToken(response.data.token, response.data.expiresIn)
        console.log('Token refreshed successfully')
      } else {
        throw new Error(response.data.message || 'Token refresh failed')
      }
    } catch (error) {
      console.error('Token refresh failed:', error)
      this.clearToken()
      throw error
    }
  }

  clearToken() {
    localStorage.removeItem('user')
  }
}

const tokenManager = new TokenManager()

export default tokenManager