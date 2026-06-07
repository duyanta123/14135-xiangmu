package com.labcourse.controller;

import com.labcourse.service.AttendanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    @Autowired
    private AttendanceService attendanceService;

    /**
     * 学生签到 - 自动判定出勤/迟到
     */
    @PostMapping("/check-in")
    public ResponseEntity<Map<String, Object>> checkIn(@RequestBody Map<String, Object> data) {
        Long studentId = Long.valueOf(data.get("studentId").toString());
        Long courseId = Long.valueOf(data.get("courseId").toString());

        Map<String, Object> result = attendanceService.checkIn(studentId, courseId);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取学生考勤历史
     */
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getHistory(@RequestParam Long studentId) {
        List<Map<String, Object>> records = attendanceService.getStudentHistory(studentId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", records);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取某课程某天的考勤列表（教师端）
     */
    @GetMapping("/course")
    public ResponseEntity<Map<String, Object>> getCourseAttendance(
            @RequestParam Long courseId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<Map<String, Object>> records = attendanceService.getCourseAttendance(courseId, date);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", records);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取课程的考勤日期列表
     */
    @GetMapping("/dates")
    public ResponseEntity<Map<String, Object>> getAttendanceDates(@RequestParam Long courseId) {
        List<LocalDate> dates = attendanceService.getAttendanceDates(courseId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", dates);
        return ResponseEntity.ok(result);
    }

    /**
     * 教师修改考勤状态（仅 缺勤→请假）
     */
    @PutMapping("/update-status")
    public ResponseEntity<Map<String, Object>> updateStatus(@RequestBody Map<String, Object> data) {
        Long attendanceId = Long.valueOf(data.get("attendanceId").toString());
        String newStatus = data.get("newStatus").toString();
        Long teacherId = Long.valueOf(data.get("teacherId").toString());
        String reason = data.get("reason") != null ? data.get("reason").toString() : "";

        Map<String, Object> result = attendanceService.updateAttendanceStatus(attendanceId, newStatus, teacherId, reason);
        return ResponseEntity.ok(result);
    }

    /**
     * 导出考勤数据
     */
    @GetMapping("/export")
    public ResponseEntity<Map<String, Object>> export(@RequestParam Long courseId) {
        List<Map<String, Object>> records = attendanceService.exportAttendance(courseId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", records);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取服务器时间
     */
    @GetMapping("/server-time")
    public ResponseEntity<Map<String, Object>> serverTime() {
        Map<String, Object> result = attendanceService.getServerTime();
        return ResponseEntity.ok(result);
    }

    /**
     * 批量创建缺勤记录（对未签到的学生）
     */
    @PostMapping("/batch-absent")
    public ResponseEntity<Map<String, Object>> batchAbsent(@RequestBody Map<String, Object> data) {
        Long courseId = Long.valueOf(data.get("courseId").toString());
        // 此功能简化：返回提示，实际缺勤由前端查询时自动展示
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "缺勤记录由系统自动判定");
        return ResponseEntity.ok(result);
    }

    // ===== 兼容旧接口 =====

    @PostMapping("/add")
    public ResponseEntity<Map<String, Object>> add(@RequestBody Map<String, Object> data) {
        Long studentId = Long.valueOf(data.get("studentId").toString());
        Long courseId = Long.valueOf(data.get("courseId").toString());
        String status = data.get("status").toString();

        Map<String, Object> result = new HashMap<>();
        boolean success = attendanceService.addAttendance(studentId, courseId, status);

        result.put("success", success);
        result.put("message", success ? "考勤录入成功" : "考勤录入失败");
        return ResponseEntity.ok(result);
    }
}