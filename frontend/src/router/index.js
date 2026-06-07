import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import StudentLayout from '../views/student/StudentLayout.vue'
import StudentCourse from '../views/student/StudentCourse.vue'
import StudentMyCourse from '../views/student/StudentMyCourse.vue'
import StudentSchedule from '../views/student/StudentSchedule.vue'
import StudentAttendance from '../views/student/StudentAttendance.vue'
import StudentAttendanceHistory from '../views/student/StudentAttendanceHistory.vue'
import TeacherLayout from '../views/teacher/TeacherLayout.vue'
import TeacherCourse from '../views/teacher/TeacherCourse.vue'
import TeacherStudentList from '../views/teacher/TeacherStudentList.vue'
import TeacherScore from '../views/teacher/TeacherScore.vue'
import TeacherAttendance from '../views/teacher/TeacherAttendance.vue'
import AdminLayout from '../views/admin/AdminLayout.vue'
import AdminStudent from '../views/admin/AdminStudent.vue'
import AdminTeacher from '../views/admin/AdminTeacher.vue'
import AdminCourse from '../views/admin/AdminCourse.vue'
import AdminLab from '../views/admin/AdminLab.vue'

const routes = [
  {
    path: '/',
    redirect: '/login'
  },
  {
    path: '/login',
    name: 'Login',
    component: Login
  },
  {
    path: '/student',
    component: StudentLayout,
    children: [
      {
        path: '',
        redirect: '/student/course'
      },
      {
        path: 'course',
        name: 'StudentCourse',
        component: StudentCourse
      },
      {
        path: 'my-course',
        name: 'StudentMyCourse',
        component: StudentMyCourse
      },
      {
        path: 'schedule',
        name: 'StudentSchedule',
        component: StudentSchedule
      },
      {
        path: 'attendance',
        name: 'StudentAttendance',
        component: StudentAttendance
      },
      {
        path: 'attendance-history',
        name: 'StudentAttendanceHistory',
        component: StudentAttendanceHistory
      }
    ]
  },
  {
    path: '/teacher',
    component: TeacherLayout,
    children: [
      {
        path: '',
        redirect: '/teacher/course'
      },
      {
        path: 'course',
        name: 'TeacherCourse',
        component: TeacherCourse
      },
      {
        path: 'student-list/:courseId',
        name: 'TeacherStudentList',
        component: TeacherStudentList
      },
      {
        path: 'score',
        name: 'TeacherScore',
        component: TeacherScore
      },
      {
        path: 'attendance',
        name: 'TeacherAttendance',
        component: TeacherAttendance
      }
    ]
  },
  {
    path: '/admin',
    component: AdminLayout,
    children: [
      {
        path: '',
        redirect: '/admin/student'
      },
      {
        path: 'student',
        name: 'AdminStudent',
        component: AdminStudent
      },
      {
        path: 'teacher',
        name: 'AdminTeacher',
        component: AdminTeacher
      },
      {
        path: 'course',
        name: 'AdminCourse',
        component: AdminCourse
      },
      {
        path: 'lab',
        name: 'AdminLab',
        component: AdminLab
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const user = JSON.parse(localStorage.getItem('user') || '{}')
  const isLoggedIn = user && user.token

  // 检查路由是否需要特定角色
  const requiresRole = to.path.startsWith('/student') || to.path.startsWith('/teacher') || to.path.startsWith('/admin')

  if (to.path === '/login') {
    // 如果已登录，跳转到对应首页
    if (isLoggedIn) {
      next(`/${user.role}`)
    } else {
      next()
    }
  } else if (requiresRole) {
    // 检查是否登录
    if (!isLoggedIn) {
      next('/login')
    } else {
      // 检查角色是否匹配
      const isStudentRoute = to.path.startsWith('/student') && user.role === 'student'
      const isTeacherRoute = to.path.startsWith('/teacher') && user.role === 'teacher'
      const isAdminRoute = to.path.startsWith('/admin') && user.role === 'admin'

      if (isStudentRoute || isTeacherRoute || isAdminRoute) {
        next()
      } else {
        // 角色不匹配，跳转到登录或对应首页
        next(`/${user.role}`)
      }
    }
  } else {
    next()
  }
})

export default router