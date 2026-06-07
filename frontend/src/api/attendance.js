import request from '../utils/request'

export function addAttendance(data) {
  return request({
    url: '/attendance/add',
    method: 'post',
    data
  })
}

export function checkIn(data) {
  return request({
    url: '/attendance/check-in',
    method: 'post',
    data
  })
}

export function getAttendanceHistory(studentId) {
  return request({
    url: '/attendance/history',
    method: 'get',
    params: { studentId }
  })
}

export function getCourseAttendance(courseId, date) {
  return request({
    url: '/attendance/course',
    method: 'get',
    params: { courseId, date }
  })
}

export function getAttendanceDates(courseId) {
  return request({
    url: '/attendance/dates',
    method: 'get',
    params: { courseId }
  })
}

export function updateAttendanceStatus(data) {
  return request({
    url: '/attendance/update-status',
    method: 'put',
    data
  })
}

export function exportAttendance(courseId) {
  return request({
    url: '/attendance/export',
    method: 'get',
    params: { courseId }
  })
}

export function getServerTime() {
  return request({
    url: '/attendance/server-time',
    method: 'get'
  })
}