import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import tokenManager from './tokenManager'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

let isRefreshing = false
let requestQueue = []

const processQueue = (error, token = null) => {
  requestQueue.forEach(promise => {
    if (error) {
      promise.reject(error)
    } else {
      promise.resolve(token)
    }
  })
  requestQueue = []
}

request.interceptors.request.use(
  async config => {
    let token = tokenManager.getToken()
    
    if (token && tokenManager.isTokenAboutToExpire()) {
      if (!isRefreshing) {
        isRefreshing = true
        try {
          const newToken = await tokenManager.refreshTokenIfNeeded()
          processQueue(null, newToken)
        } catch (error) {
          processQueue(error, null)
          if (error.response?.status === 401) {
            ElMessage.warning('登录已过期，请重新登录')
            tokenManager.clearToken()
            router.push('/login')
          }
        } finally {
          isRefreshing = false
        }
      } else {
        return new Promise((resolve, reject) => {
          requestQueue.push({
            resolve: (token) => {
              config.headers.Authorization = `Bearer ${token}`
              resolve(config)
            },
            reject: (error) => {
              reject(error)
            }
          })
        })
      }
    }

    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

request.interceptors.response.use(
  response => {
    return response.data
  },
  error => {
    if (error.response && error.response.status === 401) {
      ElMessage.error('登录已过期，请重新登录')
      tokenManager.clearToken()
      router.push('/login')
    } else if (error.response && error.response.status === 403) {
      ElMessage.error('没有权限执行此操作')
    } else if (error.response && error.response.status === 423) {
      const data = error.response.data
      const msg = data.message || '账号已被锁定'
      ElMessage.error(msg)
    } else if (!error.response) {
      ElMessage.error('网络连接失败，请检查网络')
    } else {
      const message = error.response?.data?.message || '请求失败'
      ElMessage.error(message)
    }
    return Promise.reject(error)
  }
)

export default request
