package com.labcourse.service.impl;

import com.labcourse.entity.Attendance;
import com.labcourse.entity.AttendanceStatus;
import com.labcourse.entity.Course;
import com.labcourse.entity.Student;
import com.labcourse.repository.AttendanceRepository;
import com.labcourse.repository.CourseRepository;
import com.labcourse.repository.SelectionRepository;
import com.labcourse.repository.StudentRepository;
import com.labcourse.service.AttendanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@SuppressWarnings("null")
public class AttendanceServiceImpl implements AttendanceService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceServiceImpl.class);

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
        LocalDate today = LocalDate.now();
        Attendance existing = attendanceRepository.findByStudentIdAndCourseIdAndAttendanceDate(studentId, courseId, today).orElse(null);
        if (existing != null) {
            existing.setAttendanceStatus(AttendanceStatus.valueOf(status));
            attendanceRepository.save(existing);
        } else {
            Attendance newAttendance = new Attendance();
            newAttendance.setStudentId(studentId);
            newAttendance.setCourseId(courseId);
            newAttendance.setAttendanceStatus(AttendanceStatus.valueOf(status));
            newAttendance.setAttendanceDate(today);
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
    @Transactional
    public Map<String, Object> checkIn(Long studentId, Long courseId) {
        long threadId = Thread.currentThread().getId();
        log.info("[checkIn] 线程-{} | 开始签到 | studentId={}, courseId={}, time={}",
                threadId, studentId, courseId, LocalDateTime.now());

        Map<String, Object> result = new HashMap<>();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        // 1. 获取课程信息
        Course course = courseRepository.findById(courseId).orElse(null);
        if (course == null) {
            log.warn("[checkIn] 线程-{} | 分支-课程不存在 | studentId={}, courseId={}, result=success:false",
                    threadId, studentId, courseId);
            result.put("success", false);
            result.put("message", "课程不存在");
            return result;
        }
        log.debug("[checkIn] 线程-{} | 课程已加载 | courseName={}, courseTime={}",
                threadId, course.getCourseName(), course.getCourseTime());

        // 2. 校验学生是否存在
        Student student = studentRepository.findById(studentId).orElse(null);
        if (student == null) {
            log.warn("[checkIn] 线程-{} | 分支-学生不存在 | studentId={}, courseId={}, result=success:false",
                    threadId, studentId, courseId);
            result.put("success", false);
            result.put("message", "学生不存在");
            return result;
        }
        log.debug("[checkIn] 线程-{} | 学生已加载 | studentName={}, major={}",
                threadId, student.getName(), student.getMajor());

        // 3. 检查是否已签到（悲观写锁防止并发竞态条件）
        log.debug("[checkIn] 线程-{} | 获取悲观写锁 | studentId={}, courseId={}, date={}",
                threadId, studentId, courseId, today);
        long lockStart = System.currentTimeMillis();
        Optional<Attendance> existingOpt = attendanceRepository
                .findByStudentIdAndCourseIdAndAttendanceDateForUpdate(studentId, courseId, today);
        long lockEnd = System.currentTimeMillis();
        log.debug("[checkIn] 线程-{} | 悲观锁获取完成 | 耗时={}ms, hasExisting={}",
                threadId, lockEnd - lockStart, existingOpt.isPresent());

        if (existingOpt.isPresent()) {
            Attendance existing = existingOpt.get();
            log.info("[checkIn] 线程-{} | 分支-重复签到 | studentId={}, courseId={}, existingStatus={}, result=success:false",
                    threadId, studentId, courseId, existing.getAttendanceStatus().name());
            result.put("success", false);
            result.put("message", "今日已签到，无需重复签到");
            result.put("status", existing.getAttendanceStatus().name());
            return result;
        }

        // 4. 解析课程时间，找到今天对应的上课时间
        String courseTime = course.getCourseTime();
        if (courseTime == null || courseTime.isEmpty()) {
            log.warn("[checkIn] 线程-{} | 分支-课程时间未设置 | courseId={}, courseName={}, result=success:false",
                    threadId, courseId, course.getCourseName());
            result.put("success", false);
            result.put("message", "课程时间未设置");
            return result;
        }

        // 获取今天的星期几（1=周一, 7=周日）
        int todayDayOfWeek = now.getDayOfWeek().getValue();
        log.debug("[checkIn] 线程-{} | 星期信息 | todayDayOfWeek={}, rawCourseTime={}",
                threadId, todayDayOfWeek, courseTime);

        // 解析课程时间，找到今天的时间段
        LocalTime courseStartTime = null;
        String matchedSlot = null;
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
                        matchedSlot = slot;
                        break;
                    }
                }
            }
            if (courseStartTime != null) break;
        }

        if (courseStartTime == null) {
            log.info("[checkIn] 线程-{} | 分支-今天无课 | studentId={}, courseId={}, todayDayOfWeek={}, courseTime={}, result=success:false",
                    threadId, studentId, courseId, todayDayOfWeek, courseTime);
            result.put("success", false);
            result.put("message", "今天没有该课程安排");
            return result;
        }
        log.debug("[checkIn] 线程-{} | 匹配到课程时段 | matchedSlot={}, courseStartTime={}",
                threadId, matchedSlot, courseStartTime);

        // 5. 根据时间判断签到状态
        LocalTime nowTime = now.toLocalTime();
        long minutesBeforeStart = ChronoUnit.MINUTES.between(nowTime, courseStartTime);
        long minutesAfterStart = ChronoUnit.MINUTES.between(courseStartTime, nowTime);
        log.debug("[checkIn] 线程-{} | 时间差计算 | nowTime={}, courseStartTime={}, minutesBefore={}, minutesAfter={}",
                threadId, nowTime, courseStartTime, minutesBeforeStart, minutesAfterStart);

        AttendanceStatus status;
        String statusReason;
        if (minutesBeforeStart >= 0 && minutesBeforeStart <= 10) {
            status = AttendanceStatus.出勤;
            statusReason = "课前" + minutesBeforeStart + "分钟签到";
            log.info("[checkIn] 线程-{} | 分支-出勤(课前) | studentId={}, courseId={}, minutesBeforeStart={}",
                    threadId, studentId, courseId, minutesBeforeStart);
        } else if (minutesAfterStart >= 0 && minutesAfterStart <= 3) {
            status = AttendanceStatus.迟到;
            statusReason = "课后" + minutesAfterStart + "分钟签到(迟到容忍期内)";
            log.info("[checkIn] 线程-{} | 分支-迟到(容忍期内) | studentId={}, courseId={}, minutesAfterStart={}",
                    threadId, studentId, courseId, minutesAfterStart);
        } else if (minutesAfterStart > 3) {
            status = AttendanceStatus.迟到;
            statusReason = "课后" + minutesAfterStart + "分钟签到(超容忍期)";
            log.info("[checkIn] 线程-{} | 分支-迟到(超时) | studentId={}, courseId={}, minutesAfterStart={}",
                    threadId, studentId, courseId, minutesAfterStart);
        } else {
            status = AttendanceStatus.出勤;
            statusReason = "过早签到(课前超过" + (-minutesBeforeStart) + "分钟)";
            log.info("[checkIn] 线程-{} | 分支-出勤(过早) | studentId={}, courseId={}, minutesBeforeStart={}",
                    threadId, studentId, courseId, minutesBeforeStart);
        }

        // 6. 创建签到记录
        try {
            Attendance attendance = new Attendance();
            attendance.setStudentId(studentId);
            attendance.setCourseId(courseId);
            attendance.setAttendanceStatus(status);
            attendance.setAttendanceDate(today);
            attendanceRepository.save(attendance);

            log.info("[checkIn] 线程-{} | 分支-签到成功 | studentId={}, courseId={}, status={}, reason={}, recordId={}",
                    threadId, studentId, courseId, status.name(), statusReason, attendance.getId());

            result.put("success", true);
            result.put("message", "签到成功");
            result.put("status", status.name());
            result.put("courseName", course.getCourseName());
            result.put("studentName", student.getName());
            result.put("checkInTime", now.toString());
            return result;
        } catch (Exception e) {
            log.error("[checkIn] 线程-{} | 分支-数据库异常 | studentId={}, courseId={}, error={}",
                    threadId, studentId, courseId, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "签到失败: " + e.getMessage());
            return result;
        }
    }

    @Override
    public List<Map<String, Object>> getStudentHistory(Long studentId) {
        List<Attendance> records = attendanceRepository.findByStudentIdOrderByAttendanceDateDesc(studentId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Attendance record : records) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", record.getId());
            item.put("courseId", record.getCourseId());
            item.put("attendanceStatus", record.getAttendanceStatus().name());
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
                item.put("status", att.getAttendanceStatus().name());
                item.put("checkInTime", att.getCreatedAt() != null ? att.getCreatedAt().toString() : null);
                item.put("modifyTime", att.getModifyTime() != null ? att.getModifyTime().toString() : null);
                item.put("modifiedBy", att.getModifiedBy());
                item.put("modifyReason", att.getModifyReason());
            } else {
                item.put("attendanceId", null);
                item.put("status", AttendanceStatus.缺勤.name());
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
        AttendanceStatus currentStatus = attendance.getAttendanceStatus();

        // 仅允许从"缺勤"修改为"请假"
        if (currentStatus == AttendanceStatus.缺勤 && AttendanceStatus.请假.name().equals(newStatus)) {
            attendance.setAttendanceStatus(AttendanceStatus.请假);
            attendance.setModifiedBy(teacherId);
            attendance.setModifyTime(LocalDateTime.now());
            attendance.setModifyReason(reason);
            attendanceRepository.save(attendance);

            result.put("success", true);
            result.put("message", "修改成功");
            return result;
        }

        if (AttendanceStatus.请假.name().equals(newStatus) && currentStatus != AttendanceStatus.缺勤) {
            result.put("success", false);
            result.put("message", "仅可将【缺勤】状态修改为【请假】");
        } else if (!AttendanceStatus.请假.name().equals(newStatus)) {
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
            item.put("status", record.getAttendanceStatus().name());
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