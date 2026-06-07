package com.labcourse.service.impl;

import com.labcourse.entity.Attendance;
import com.labcourse.entity.Course;
import com.labcourse.entity.Student;
import com.labcourse.repository.AttendanceRepository;
import com.labcourse.repository.CourseRepository;
import com.labcourse.repository.SelectionRepository;
import com.labcourse.repository.StudentRepository;
import com.labcourse.service.AttendanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@SuppressWarnings("null")
public class AttendanceServiceImpl implements AttendanceService {

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private SelectionRepository selectionRepository;

    // 课程节次 → 上课开始时间映射
    private static final Map<Integer, LocalTime> PERIOD_START_TIMES = new LinkedHashMap<>();

    static {
        PERIOD_START_TIMES.put(1, LocalTime.of(8, 0));
        PERIOD_START_TIMES.put(3, LocalTime.of(10, 0));
        PERIOD_START_TIMES.put(5, LocalTime.of(14, 0));
        PERIOD_START_TIMES.put(7, LocalTime.of(16, 0));
        PERIOD_START_TIMES.put(9, LocalTime.of(19, 0));
    }

    // 星期映射
    private static final Map<String, Integer> DAY_OF_WEEK_MAP = new LinkedHashMap<>();

    static {
        DAY_OF_WEEK_MAP.put("周一", 1);
        DAY_OF_WEEK_MAP.put("周二", 2);
        DAY_OF_WEEK_MAP.put("周三", 3);
        DAY_OF_WEEK_MAP.put("周四", 4);
        DAY_OF_WEEK_MAP.put("周五", 5);
        DAY_OF_WEEK_MAP.put("周六", 6);
        DAY_OF_WEEK_MAP.put("周日", 7);
        DAY_OF_WEEK_MAP.put("星期一", 1);
        DAY_OF_WEEK_MAP.put("星期二", 2);
        DAY_OF_WEEK_MAP.put("星期三", 3);
        DAY_OF_WEEK_MAP.put("星期四", 4);
        DAY_OF_WEEK_MAP.put("星期五", 5);
        DAY_OF_WEEK_MAP.put("星期六", 6);
        DAY_OF_WEEK_MAP.put("星期日", 7);
    }

    @Override
    public boolean addAttendance(Long studentId, Long courseId, String status) {
        Attendance existing = attendanceRepository.findByStudentIdAndCourseId(studentId, courseId).orElse(null);
        if (existing != null) {
            existing.setAttendanceStatus(status);
            attendanceRepository.save(existing);
        } else {
            Attendance newAttendance = new Attendance();
            newAttendance.setStudentId(studentId);
            newAttendance.setCourseId(courseId);
            newAttendance.setAttendanceStatus(status);
            newAttendance.setAttendanceDate(LocalDate.now());
            attendanceRepository.save(newAttendance);
        }
        return true;
    }

    @Override
    public List<Attendance> list() {
        return attendanceRepository.findAll();
    }

    @Override
    public boolean save(Attendance attendance) {
        attendanceRepository.save(attendance);
        return true;
    }

    @Override
    public boolean updateById(Attendance attendance) {
        attendanceRepository.save(attendance);
        return true;
    }

    @Override
    public boolean removeById(Long id) {
        attendanceRepository.deleteById(id);
        return true;
    }

    @Override
    public Map<String, Object> checkIn(Long studentId, Long courseId) {
        Map<String, Object> result = new HashMap<>();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        // 获取课程信息
        Course course = courseRepository.findById(courseId).orElse(null);
        if (course == null) {
            result.put("success", false);
            result.put("message", "课程不存在");
            return result;
        }

        // 检查是否已签到
        Optional<Attendance> existingOpt = attendanceRepository
                .findByStudentIdAndCourseIdAndAttendanceDate(studentId, courseId, today);
        if (existingOpt.isPresent()) {
            Attendance existing = existingOpt.get();
            result.put("success", false);
            result.put("message", "今日已签到，无需重复签到");
            result.put("status", existing.getAttendanceStatus());
            return result;
        }

        // 解析课程时间，找到今天对应的上课时间
        String courseTime = course.getCourseTime();
        if (courseTime == null || courseTime.isEmpty()) {
            result.put("success", false);
            result.put("message", "课程时间未设置");
            return result;
        }

        // 获取今天的星期几（1=周一, 7=周日）
        int todayDayOfWeek = now.getDayOfWeek().getValue();

        // 解析课程时间，找到今天的时间段
        LocalTime courseStartTime = null;
        String[] timeSlots = courseTime.split("[,，]");
        for (String slot : timeSlots) {
            slot = slot.trim();
            for (Map.Entry<String, Integer> dayEntry : DAY_OF_WEEK_MAP.entrySet()) {
                if (slot.contains(dayEntry.getKey()) && dayEntry.getValue() == todayDayOfWeek) {
                    // 解析节次
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+)-(\\d+)节");
                    java.util.regex.Matcher matcher = pattern.matcher(slot);
                    if (matcher.find()) {
                        int startPeriod = Integer.parseInt(matcher.group(1));
                        courseStartTime = PERIOD_START_TIMES.get(startPeriod);
                        break;
                    }
                }
            }
            if (courseStartTime != null) break;
        }

        if (courseStartTime == null) {
            result.put("success", false);
            result.put("message", "今天没有该课程安排");
            return result;
        }

        // 根据时间判断签到状态
        // 出勤：上课前10分钟内签到
        // 迟到：上课后3分钟内签到
        // 超出范围签到视为迟到
        LocalTime nowTime = now.toLocalTime();
        long minutesBeforeStart = ChronoUnit.MINUTES.between(nowTime, courseStartTime);
        long minutesAfterStart = ChronoUnit.MINUTES.between(courseStartTime, nowTime);

        String status;
        if (minutesBeforeStart >= 0 && minutesBeforeStart <= 10) {
            // 上课前10分钟内
            status = "出勤";
        } else if (minutesAfterStart >= 0 && minutesAfterStart <= 3) {
            // 上课后3分钟内
            status = "迟到";
        } else if (minutesAfterStart > 3) {
            // 上课超过3分钟后仍可签到，标记为迟到
            status = "迟到";
        } else {
            // 过早签到（超过课前10分钟），仍允许但标记为出勤
            status = "出勤";
        }

        // 创建签到记录
        Attendance attendance = new Attendance();
        attendance.setStudentId(studentId);
        attendance.setCourseId(courseId);
        attendance.setAttendanceStatus(status);
        attendance.setAttendanceDate(today);
        attendanceRepository.save(attendance);

        result.put("success", true);
        result.put("message", "签到成功");
        result.put("status", status);
        result.put("courseName", course.getCourseName());
        result.put("checkInTime", now.toString());
        return result;
    }

    @Override
    public List<Map<String, Object>> getStudentHistory(Long studentId) {
        List<Attendance> records = attendanceRepository.findByStudentIdOrderByAttendanceDateDesc(studentId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Attendance record : records) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", record.getId());
            item.put("courseId", record.getCourseId());
            item.put("attendanceStatus", record.getAttendanceStatus());
            item.put("attendanceDate", record.getAttendanceDate() != null ? record.getAttendanceDate().toString() : null);
            item.put("createdAt", record.getCreatedAt() != null ? record.getCreatedAt().toString() : null);

            // 关联课程名称
            courseRepository.findById(record.getCourseId()).ifPresent(course -> {
                item.put("courseName", course.getCourseName());
                item.put("courseTime", course.getCourseTime());
            });

            result.add(item);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getCourseAttendance(Long courseId, LocalDate date) {
        // 获取选课学生列表
        List<com.labcourse.entity.Selection> selections = selectionRepository.findByCourseId(courseId);
        List<Long> studentIds = selections.stream()
                .map(com.labcourse.entity.Selection::getStudentId)
                .collect(Collectors.toList());

        // 获取当天的考勤记录
        List<Attendance> attendances = attendanceRepository.findByCourseIdAndAttendanceDate(courseId, date);
        Map<Long, Attendance> attendanceMap = attendances.stream()
                .collect(Collectors.toMap(Attendance::getStudentId, a -> a, (a, b) -> a));

        // 获取学生信息
        List<Student> students = studentRepository.findAllById(studentIds);
        Map<Long, Student> studentMap = students.stream()
                .collect(Collectors.toMap(Student::getId, s -> s));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Long studentId : studentIds) {
            Map<String, Object> item = new HashMap<>();
            Student student = studentMap.get(studentId);
            if (student == null) continue;

            item.put("studentId", student.getId());
            item.put("studentNo", student.getStudentNo());
            item.put("studentName", student.getName());
            item.put("major", student.getMajor());

            Attendance att = attendanceMap.get(studentId);
            if (att != null) {
                item.put("attendanceId", att.getId());
                item.put("status", att.getAttendanceStatus());
                item.put("checkInTime", att.getCreatedAt() != null ? att.getCreatedAt().toString() : null);
                item.put("modifyTime", att.getModifyTime() != null ? att.getModifyTime().toString() : null);
                item.put("modifiedBy", att.getModifiedBy());
                item.put("modifyReason", att.getModifyReason());
            } else {
                item.put("attendanceId", null);
                item.put("status", "缺勤");
                item.put("checkInTime", null);
                item.put("modifyTime", null);
                item.put("modifiedBy", null);
                item.put("modifyReason", null);
            }

            result.add(item);
        }
        return result;
    }

    @Override
    public Map<String, Object> updateAttendanceStatus(Long attendanceId, String newStatus, Long teacherId, String reason) {
        Map<String, Object> result = new HashMap<>();

        Optional<Attendance> opt = attendanceRepository.findById(attendanceId);
        if (opt.isEmpty()) {
            result.put("success", false);
            result.put("message", "考勤记录不存在");
            return result;
        }

        Attendance attendance = opt.get();
        String currentStatus = attendance.getAttendanceStatus();

        // 仅允许从"缺勤"修改为"请假"
        if ("缺勤".equals(currentStatus) && "请假".equals(newStatus)) {
            attendance.setAttendanceStatus(newStatus);
            attendance.setModifiedBy(teacherId);
            attendance.setModifyTime(LocalDateTime.now());
            attendance.setModifyReason(reason);
            attendanceRepository.save(attendance);

            result.put("success", true);
            result.put("message", "修改成功");
            return result;
        }

        if ("请假".equals(newStatus) && !"缺勤".equals(currentStatus)) {
            result.put("success", false);
            result.put("message", "仅可将【缺勤】状态修改为【请假】");
        } else if (!"请假".equals(newStatus)) {
            result.put("success", false);
            result.put("message", "仅可将【缺勤】状态修改为【请假】");
        } else {
            result.put("success", false);
            result.put("message", "修改失败");
        }
        return result;
    }

    @Override
    public List<LocalDate> getAttendanceDates(Long courseId) {
        List<Attendance> records = attendanceRepository.findByCourseIdOrderByAttendanceDateDesc(courseId);
        return records.stream()
                .map(Attendance::getAttendanceDate)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> exportAttendance(Long courseId) {
        List<Attendance> records = attendanceRepository.findByCourseIdOrderByAttendanceDateDesc(courseId);

        // 获取所有学生
        Set<Long> studentIds = records.stream().map(Attendance::getStudentId).collect(Collectors.toSet());
        Map<Long, Student> studentMap = studentRepository.findAllById(studentIds).stream()
                .collect(Collectors.toMap(Student::getId, s -> s));

        // 获取课程信息
        Course course = courseRepository.findById(courseId).orElse(null);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Attendance record : records) {
            Map<String, Object> item = new LinkedHashMap<>();
            Student student = studentMap.get(record.getStudentId());
            item.put("studentNo", student != null ? student.getStudentNo() : "");
            item.put("studentName", student != null ? student.getName() : "");
            item.put("major", student != null ? student.getMajor() : "");
            item.put("courseName", course != null ? course.getCourseName() : "");
            item.put("attendanceDate", record.getAttendanceDate() != null ? record.getAttendanceDate().toString() : "");
            item.put("status", record.getAttendanceStatus());
            item.put("checkInTime", record.getCreatedAt() != null ? record.getCreatedAt().toString() : "");
            result.add(item);
        }
        return result;
    }

    @Override
    public Map<String, Object> getServerTime() {
        Map<String, Object> result = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        result.put("timestamp", now.toString());
        result.put("date", now.toLocalDate().toString());
        result.put("time", now.toLocalTime().toString());
        result.put("dayOfWeek", now.getDayOfWeek().getValue());
        return result;
    }
}