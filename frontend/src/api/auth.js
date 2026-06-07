import request from '../utils/request'

export const refreshToken = () => {
  return request.post('/auth/refresh')
}

export const validateToken = () => {
  return request.get('/auth/validate')
}
