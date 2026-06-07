import request from '../utils/request'

export function addSelection(data) {
  return request({
    url: '/selection/add',
    method: 'post',
    data
  })
}

export function deleteSelection(id) {
  return request({
    url: `/selection/delete/${id}`,
    method: 'delete'
  })
}

export function getMyCourses() {
  return request({
    url: '/selection/my',
    method: 'get'
  })
}

export function getStudentList(courseId) {
  return request({
    url: `/selection/studentList/${courseId}`,
    method: 'get'
  })
}
