package com.labcourse.controller;

import com.labcourse.service.AttendanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
        Object studentIdObj = data.get("studentId");
        Object courseIdObj = data.get("courseId");

        if (studentIdObj == null || courseIdObj == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "studentId 和 courseId 不能为空");
            return ResponseEntity.badRequest().body(result);
        }

        Long studentId = Long.valueOf(studentIdObj.toString());

        // Security fix: 从JWT Token中获取当前认证用户ID，学生只能为自己签到
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long currentUserId = Long.valueOf(authentication.getPrincipal().toString());
        if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_STUDENT"))) {
            if (!studentId.equals(currentUserId)) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "无权为其他学生签到");
                return ResponseEntity.status(403).body(result);
            }
        }

        Long courseId = Long.valueOf(courseIdObj.toString());

        Map<String, Object> result = attendanceService.checkIn(studentId, courseId);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取学生考勤历史
     */
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getHistory(@RequestParam Long studentId) {
        // Security fix: 学生只能查看自己的考勤历史
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long currentUserId = Long.valueOf(authentication.getPrincipal().toString());
        Map<String, Object> result = new HashMap<>();

        if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_STUDENT"))) {
            if (!studentId.equals(currentUserId)) {
                result.put("success", false);
                result.put("message", "无权查看其他学生的考勤记录");
                return ResponseEntity.status(403).body(result);
            }
        }

        List<Map<String, Object>> records = attendanceService.getStudentHistory(studentId);
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

        // Security fix: teacherId从JWT Token中获取，不信任请求体
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long teacherId = Long.valueOf(authentication.getPrincipal().toString());

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
        // 此功能简化：返回提示，实际缺勤由前端查询时自动展示
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "缺勤记录由系统自动判定");
        return ResponseEntity.ok(result);
    }

    // ===== 兼容旧接口 =====

    @PostMapping("/add")
    public ResponseEntity<Map<String, Object>> add(@RequestBody Map<String, Object> data) {
        // Security fix: studentId从JWT Token中获取，不信任请求体
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long studentId = Long.valueOf(authentication.getPrincipal().toString());
        Long courseId = Long.valueOf(data.get("courseId").toString());
        String status = data.get("status").toString();

        Map<String, Object> result = new HashMap<>();
        boolean success = attendanceService.addAttendance(studentId, courseId, status);

        result.put("success", success);
        result.put("message", success ? "考勤录入成功" : "考勤录入失败");
        return ResponseEntity.ok(result);
    }
}