/**
 * 课表解析工具
 * 将 courseTime 字符串（如 "周一 1-2节"）解析为结构化数据
 */

const DAY_MAP = {
  '周一': 1, '周二': 2, '周三': 3, '周四': 4, '周五': 5,
  '周六': 6, '周日': 7,
  '星期一': 1, '星期二': 2, '星期三': 3, '星期四': 4, '星期五': 5,
  '星期六': 6, '星期日': 7
}

const DAY_NAMES = ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日']

const PERIOD_CONFIG = [
  { label: '1-2节', start: 1, end: 2, time: '08:00 - 09:40' },
  { label: '3-4节', start: 3, end: 4, time: '10:00 - 11:40' },
  { label: '5-6节', start: 5, end: 6, time: '14:00 - 15:40' },
  { label: '7-8节', start: 7, end: 8, time: '16:00 - 17:40' },
  { label: '9-10节', start: 9, end: 10, time: '19:00 - 20:40' }
]

const PERIOD_LABELS = PERIOD_CONFIG.map(p => p.label)

/**
 * 解析课程时间字符串
 * @param {string} courseTime - 例如 "周一 1-2节"、"周三 3-4节" 或 "周一 1-2节, 周三 3-4节"
 * @returns {Array<{day: number, dayName: string, periods: string, startPeriod: number, endPeriod: number, time: string}>}
 */
export function parseCourseTime(courseTime) {
  if (!courseTime) return []

  const parts = courseTime.split(/[,，]/)
  const results = []

  for (const part of parts) {
    const trimmed = part.trim()
    if (!trimmed) continue

    const dayMatch = trimmed.match(/(周[一二三四五六日]|星期[一二三四五六日])/)
    const periodMatch = trimmed.match(/(\d+)-(\d+)节/)

    if (!dayMatch || !periodMatch) continue

    const dayName = dayMatch[0]
    const day = DAY_MAP[dayName] || 0
    const startPeriod = parseInt(periodMatch[1])
    const endPeriod = parseInt(periodMatch[2])

    const periodLabel = `${startPeriod}-${endPeriod}节`
    const config = PERIOD_CONFIG.find(p => p.label === periodLabel)

    results.push({
      day,
      dayName,
      periods: periodLabel,
      startPeriod,
      endPeriod,
      time: config ? config.time : ''
    })
  }

  return results
}

/**
 * 检测两个课程时间是否冲突
 * @param {string} timeA - 课程时间字符串 A
 * @param {string} timeB - 课程时间字符串 B
 * @returns {{hasConflict: boolean, conflicts: Array<{a: object, b: object}>}}
 */
export function detectConflict(timeA, timeB) {
  const slotsA = parseCourseTime(timeA)
  const slotsB = parseCourseTime(timeB)
  const conflicts = []

  for (const a of slotsA) {
    for (const b of slotsB) {
      if (a.day === b.day) {
        const overlap = a.startPeriod < b.endPeriod && b.startPeriod < a.endPeriod
        if (overlap) {
          conflicts.push({
            a: { dayName: a.dayName, periods: a.periods },
            b: { dayName: b.dayName, periods: b.periods }
          })
        }
      }
    }
  }

  return {
    hasConflict: conflicts.length > 0,
    conflicts
  }
}

/**
 * 检测新课程时间是否与已有课程列表冲突
 * @param {string} newCourseTime - 新课程时间
 * @param {Array<{courseTime: string, courseName: string}>} existingCourses
 * @returns {{hasConflict: boolean, conflictDetails: Array<{courseName: string, detail: string}>}}
 */
export function detectConflictWithList(newCourseTime, existingCourses) {
  const conflictDetails = []

  for (const course of existingCourses) {
    const result = detectConflict(newCourseTime, course.course_time)
    if (result.hasConflict) {
      for (const c of result.conflicts) {
        conflictDetails.push({
          courseName: course.course_name,
          detail: `${c.a.dayName} ${c.a.periods}`
        })
      }
    }
  }

  return {
    hasConflict: conflictDetails.length > 0,
    conflictDetails
  }
}

/**
 * 将课程列表转换为课表格子数据
 * @param {Array} courses - 课程列表，每项包含 courseName, courseTime, teacherName, labName, location 等
 * @returns {Array<{day: number, period: string, startPeriod: number, courses: Array}>}
 */
export function buildScheduleGrid(courses) {
  const grid = []

  for (const period of PERIOD_CONFIG) {
    for (let day = 1; day <= 7; day++) {
      const cellCourses = []

      for (const course of courses) {
        if (!course.courseTime) continue
        const slots = parseCourseTime(course.courseTime)
        for (const slot of slots) {
          if (slot.day === day && slot.startPeriod === period.start) {
            cellCourses.push({
              ...course,
              slot
            })
          }
        }
      }

      grid.push({
        day,
        dayName: DAY_NAMES[day],
        period: period.label,
        startPeriod: period.start,
        endPeriod: period.end,
        time: period.time,
        courses: cellCourses
      })
    }
  }

  return grid
}

export { DAY_NAMES, PERIOD_CONFIG, PERIOD_LABELS }