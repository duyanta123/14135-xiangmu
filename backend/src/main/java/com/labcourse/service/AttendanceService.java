package com.labcourse.service;

import com.labcourse.entity.Attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface AttendanceService {
    boolean addAttendance(Long studentId, Long courseId, String status);

    List<Attendance> list();

    boolean save(Attendance attendance);

    boolean updateById(Attendance attendance);

    boolean removeById(Long id);

    /**
     * 学生签到：根据服务器时间和课程时间自动判定签到状态
     * @return { success, message, status, attendance }
     */
    Map<String, Object> checkIn(Long studentId, Long courseId);

    /**
     * 获取学生签到历史
     */
    List<Map<String, Object>> getStudentHistory(Long studentId);

    /**
     * 获取某课程某天的考勤列表（教师端）
     */
    List<Map<String, Object>> getCourseAttendance(Long courseId, LocalDate date);

    /**
     * 教师修改考勤状态（仅 缺勤→请假）
     */
    Map<String, Object> updateAttendanceStatus(Long attendanceId, String newStatus, Long teacherId, String reason);

    /**
     * 获取课程的所有考勤日期列表
     */
    List<LocalDate> getAttendanceDates(Long courseId);

    /**
     * 导出考勤数据（教师端）
     */
    List<Map<String, Object>> exportAttendance(Long courseId);

    /**
     * 获取服务器当前时间
     */
    Map<String, Object> getServerTime();
}