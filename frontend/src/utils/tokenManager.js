import axios from 'axios'

const TOKEN_REFRESH_THRESHOLD = 10 * 60 // 10分钟（秒）

class TokenManager {
  constructor() {
    this.refreshPromise = null
  }

  getToken() {
    const user = JSON.parse(localStorage.getItem('user') || '{}')
    return user.token || null
  }

  getTokenExpireTime() {
    const user = JSON.parse(localStorage.getItem('user') || '{}')
    return user.tokenExpireTime || 0
  }

  setToken(token, expiresIn) {
    const user = JSON.parse(localStorage.getItem('user') || '{}')
    user.token = token
    user.tokenExpireTime = Date.now() + expiresIn * 1000
    localStorage.setItem('user', JSON.stringify(user))
  }

  isTokenExpired() {
    const expireTime = this.getTokenExpireTime()
    if (!expireTime) return true
    return Date.now() >= expireTime
  }

  isTokenAboutToExpire() {
    const expireTime = this.getTokenExpireTime()
    if (!expireTime) return true
    const remainingTime = (expireTime - Date.now()) / 1000
    return remainingTime < TOKEN_REFRESH_THRESHOLD
  }

  async refreshTokenIfNeeded() {
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
