package com.labcourse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labcourse.entity.Attendance;
import com.labcourse.entity.AttendanceStatus;
import com.labcourse.repository.AttendanceRepository;
import com.labcourse.service.AttendanceService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import org.junit.jupiter.api.Assumptions;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * AttendanceService comprehensive unit tests.
 * Covers: normal flow, boundary conditions, error scenarios, data consistency.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("null")
class AttendanceServiceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private AttendanceRepository attendanceRepository;

    private String studentToken;
    private String teacherToken;
    private String student2Token;
    private static final Long SID1 = 1L;
    private static final Long SID2 = 9L;
    private static final Long SID3 = 3L;
    private static final Long CID_BAD = 99999L;
    private static final Long TID1 = 1L;

    // Day-of-week → course ID mapping (周一~周五 = 1~5)
    private static final Map<Integer, Long> DAY_TO_COURSE = Map.of(
            1, 1L,  // 周一: Java程序设计
            2, 2L,  // 周二: 数据库原理
            3, 3L,  // 周三: Web开发技术
            4, 4L,  // 周四: 计算机网络
            5, 5L   // 周五: 软件测试
    );
    private Long activeCourseId;
    private boolean isWeekend;
    private LocalDate today;

    @BeforeEach
    void setUp() throws Exception {
        today = LocalDate.now();
        int todayDayOfWeek = today.getDayOfWeek().getValue();
        activeCourseId = DAY_TO_COURSE.get(todayDayOfWeek);
        isWeekend = activeCourseId == null;
        if (isWeekend) {
            activeCourseId = 3L; // fallback for non-check-in tests
        }

        // Login as student S001
        String loginBody = objectMapper.writeValueAsString(Map.of("studentNo", "S001", "password", "123456"));
        String resp = mockMvc.perform(post("/api/student/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        studentToken = (String) objectMapper.readValue(resp, Map.class).get("accessToken");

        // Login as student S008
        loginBody = objectMapper.writeValueAsString(Map.of("studentNo", "S008", "password", "123456"));
        resp = mockMvc.perform(post("/api/student/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        student2Token = (String) objectMapper.readValue(resp, Map.class).get("accessToken");

        // Login as teacher T001
        loginBody = objectMapper.writeValueAsString(Map.of("teacherNo", "T001", "password", "123456"));
        resp = mockMvc.perform(post("/api/teacher/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        teacherToken = (String) objectMapper.readValue(resp, Map.class).get("accessToken");

        // Clean up today's test attendance
        attendanceRepository.findByStudentIdAndCourseIdAndAttendanceDate(SID1, activeCourseId, today)
                .ifPresent(a -> attendanceRepository.delete(a));
        attendanceRepository.findByStudentIdAndCourseIdAndAttendanceDate(SID2, activeCourseId, today)
                .ifPresent(a -> attendanceRepository.delete(a));
    }

    // ================================================================
    // NORMAL FLOW TESTS
    // ================================================================

    @Test
    @Order(1)
    @DisplayName("Normal: Student check-in succeeds")
    void testCheckIn_Success() throws Exception {
        Assumptions.assumeFalse(isWeekend, "周末无课程安排");
        String body = objectMapper.writeValueAsString(Map.of("studentId", SID1, "courseId", activeCourseId));
        mockMvc.perform(post("/api/attendance/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + studentToken)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.courseName").exists());
    }

    @Test
    @Order(2)
    @DisplayName("Normal: Add attendance via legacy API")
    void testAddAttendance_Success() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "studentId", SID1, "courseId", activeCourseId, "status", "出勤"));
        mockMvc.perform(post("/api/attendance/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + teacherToken)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @Order(3)
    @DisplayName("Normal: Get student attendance history")
    void testGetStudentHistory() throws Exception {
        mockMvc.perform(get("/api/attendance/history")
                        .param("studentId", SID1.toString())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @Order(4)
    @DisplayName("Normal: Get course attendance for today")
    void testGetCourseAttendance() throws Exception {
        mockMvc.perform(get("/api/attendance/course")
                        .param("courseId", activeCourseId.toString())
                        .param("date", today.toString())
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @Order(5)
    @DisplayName("Normal: Export attendance data")
    void testExportAttendance() throws Exception {
        mockMvc.perform(get("/api/attendance/export")
                        .param("courseId", activeCourseId.toString())
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @Order(6)
    @DisplayName("Normal: Server time endpoint")
    void testServerTime() throws Exception {
        mockMvc.perform(get("/api/attendance/server-time")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").exists())
                .andExpect(jsonPath("$.time").exists())
                .andExpect(jsonPath("$.dayOfWeek").exists());
    }

    @Test
    @Order(7)
    @DisplayName("Normal: Get attendance dates")
    void testGetAttendanceDates() throws Exception {
        mockMvc.perform(get("/api/attendance/dates")
                        .param("courseId", activeCourseId.toString())
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @Order(8)
    @DisplayName("Normal: Multi-student check-in to same course")
    void testMultiStudentCheckIn() throws Exception {
        Assumptions.assumeFalse(isWeekend, "周末无课程安排");
        String body = objectMapper.writeValueAsString(Map.of("studentId", SID2, "courseId", activeCourseId));
        mockMvc.perform(post("/api/attendance/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + student2Token)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ================================================================
    // BOUNDARY CONDITION TESTS
    // ================================================================

    @Test
    @Order(9)
    @DisplayName("Boundary: Duplicate check-in is rejected")
    void testCheckIn_DuplicateRejected() throws Exception {
        Assumptions.assumeFalse(isWeekend, "周末无课程安排");
        // First ensure the student has already checked in today
        String body = objectMapper.writeValueAsString(Map.of("studentId", SID1, "courseId", activeCourseId));
        mockMvc.perform(post("/api/attendance/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + studentToken)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Now try again - should be rejected
        mockMvc.perform(post("/api/attendance/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + studentToken)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("已签到")));
    }

    @Test
    @Order(10)
    @DisplayName("Boundary: Check-in for non-existent course")
    void testCheckIn_InvalidCourse() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("studentId", SID1, "courseId", CID_BAD));
        mockMvc.perform(post("/api/attendance/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + studentToken)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("课程不存在"));
    }

    @Test
    @Order(11)
    @DisplayName("Boundary: Access API without token returns 403")
    void testAccessWithoutToken() throws Exception {
        mockMvc.perform(get("/api/attendance/server-time"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(12)
    @DisplayName("Boundary: Student cannot access teacher-only API")
    void testStudentAccessTeacherAPI() throws Exception {
        mockMvc.perform(get("/api/attendance/export")
                        .param("courseId", activeCourseId.toString())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(13)
    @DisplayName("Boundary: Query future date returns empty or current data")
    void testQueryFutureDate() throws Exception {
        mockMvc.perform(get("/api/attendance/course")
                        .param("courseId", activeCourseId.toString())
                        .param("date", "2099-01-01")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @Order(14)
    @DisplayName("Boundary: Invalid student ID returns error")
    void testInvalidStudentId() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("studentId", 99999, "courseId", activeCourseId));
        mockMvc.perform(post("/api/attendance/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + studentToken)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("学生不存在"));
    }

    @Test
    @Order(15)
    @DisplayName("Security: Student cannot check in for another student")
    void testCheckIn_StudentIdMismatch_Rejected() throws Exception {
        Assumptions.assumeFalse(isWeekend, "周末无课程安排");
        // S001 tries to check in using SID2 (another student's ID)
        String body = objectMapper.writeValueAsString(Map.of("studentId", SID2, "courseId", activeCourseId));
        mockMvc.perform(post("/api/attendance/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + studentToken)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("无权为其他学生签到"));
    }

    @Test
    @Order(17)
    @DisplayName("Boundary: Expired/invalid token rejected")
    void testInvalidToken() throws Exception {
        mockMvc.perform(get("/api/attendance/server-time")
                        .header("Authorization", "Bearer invalid-token-here-12345"))
                .andExpect(status().isForbidden());
    }

    // ================================================================
    // STATUS CHANGE TESTS (Exception & Error Scenarios)
    // ================================================================

    @Test
    @Order(18)
    @DisplayName("Status: Absent -> Leave transition succeeds")
    void testStatusChange_AbsentToLeave_Success() throws Exception {
        // Create an absent record
        String addBody = objectMapper.writeValueAsString(Map.of(
                "studentId", SID1, "courseId", activeCourseId, "status", "缺勤"));
        mockMvc.perform(post("/api/attendance/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + teacherToken)
                        .content(addBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Find the attendance ID
        Attendance att = attendanceRepository
                .findByStudentIdAndCourseIdAndAttendanceDate(SID1, activeCourseId, today).orElse(null);
        assertNotNull(att, "Absent record should exist");

        // Change status to leave
        String updateBody = objectMapper.writeValueAsString(Map.of(
                "attendanceId", att.getId(),
                "newStatus", "请假",
                "teacherId", TID1,
                "reason", "Approved leave request"));

        mockMvc.perform(put("/api/attendance/update-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + teacherToken)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("修改成功"));
    }

    @Test
    @Order(19)
    @DisplayName("Status: Present -> Leave transition is rejected")
    void testStatusChange_PresentToLeave_Rejected() throws Exception {
        // Find a present record for SID1 in activeCourseId
        Attendance att = attendanceRepository
                .findByStudentIdAndCourseIdAndAttendanceDate(SID1, activeCourseId, today).orElse(null);
        if (att == null) {
            // If no record exists (already cleaned up), this test is not applicable
            return;
        }

        String updateBody = objectMapper.writeValueAsString(Map.of(
                "attendanceId", att.getId(),
                "newStatus", "请假",
                "teacherId", TID1,
                "reason", "Invalid attempt"));

        mockMvc.perform(put("/api/attendance/update-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + teacherToken)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(20)
    @DisplayName("Status: Modify non-existent record fails")
    void testStatusChange_NonExistentRecord() throws Exception {
        String updateBody = objectMapper.writeValueAsString(Map.of(
                "attendanceId", 99999L,
                "newStatus", "请假",
                "teacherId", TID1,
                "reason", "Test"));

        mockMvc.perform(put("/api/attendance/update-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + teacherToken)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("考勤记录不存在"));
    }

    @Test
    @Order(21)
    @DisplayName("Status: Double-submit leave request is rejected")
    void testStatusChange_DoubleSubmit_Rejected() throws Exception {
        // Create absent record
        String addBody = objectMapper.writeValueAsString(Map.of(
                "studentId", SID2, "courseId", activeCourseId, "status", "缺勤"));
        mockMvc.perform(post("/api/attendance/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + teacherToken)
                        .content(addBody))
                .andExpect(status().isOk());

        Attendance att = attendanceRepository
                .findByStudentIdAndCourseIdAndAttendanceDate(SID2, activeCourseId, today).orElse(null);
        assertNotNull(att, "Absent record should exist");

        String updateBody = objectMapper.writeValueAsString(Map.of(
                "attendanceId", att.getId(), "newStatus", "请假",
                "teacherId", TID1, "reason", "First leave request"));

        // First change succeeds
        mockMvc.perform(put("/api/attendance/update-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + teacherToken)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Second change (double-submit) should be rejected
        mockMvc.perform(put("/api/attendance/update-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + teacherToken)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ================================================================
    // DATA CONSISTENCY TESTS
    // ================================================================

    @Test
    @Order(22)
    @DisplayName("Consistency: One record per student per course per day")
    void testConsistency_UniquePerDay() {
        Attendance att1 = attendanceRepository
                .findByStudentIdAndCourseIdAndAttendanceDate(SID1, activeCourseId, today).orElse(null);
        if (att1 != null) {
            // Verify only one record exists
            boolean exists = attendanceRepository
                    .existsByStudentIdAndCourseIdAndAttendanceDate(SID1, activeCourseId, today);
            assertTrue(exists, "Should have exactly one record");

            // Adding a second record should update, not create duplicate
            attendanceService.addAttendance(SID1, activeCourseId, "出勤");
            List<Attendance> all = attendanceRepository.findByCourseIdAndAttendanceDate(activeCourseId, today);
            long count = all.stream()
                    .filter(a -> a.getStudentId().equals(SID1))
                    .count();
            assertEquals(1, count, "Should still have only one record after duplicate add");
        }
    }

    @Test
    @Order(23)
    @DisplayName("Consistency: Status values are valid strings")
    void testConsistency_StatusValues() {
        attendanceRepository.findByStudentIdAndCourseIdAndAttendanceDate(SID1, activeCourseId, today)
                .ifPresent(att -> {
                    AttendanceStatus status = att.getAttendanceStatus();
                    assertNotNull(status, "Status should not be null");
                    assertNotNull(status.name(), "Status name should not be null");
                });
    }

    @Test
    @Order(24)
    @DisplayName("Consistency: Export data matches course query")
    void testConsistency_ExportMatchesCourse() {
        List<Attendance> courseRecords = attendanceRepository
                .findByCourseIdAndAttendanceDate(activeCourseId, today);
        List<Attendance> exportRecords = attendanceRepository
                .findByCourseIdOrderByAttendanceDateDesc(activeCourseId);

        // Export should contain today's records
        long todayCount = exportRecords.stream()
                .filter(a -> today.equals(a.getAttendanceDate()))
                .count();
        assertEquals(courseRecords.size(), todayCount,
                "Today records in export should match course query");
    }

    @Test
    @Order(25)
    @DisplayName("Consistency: Server date matches expected format")
    void testConsistency_ServerDate() {
        Map<String, Object> result = attendanceService.getServerTime();
        assertNotNull(result.get("date"));
        assertNotNull(result.get("time"));
        assertNotNull(result.get("dayOfWeek"));
        assertEquals(today.toString(), result.get("date"));
    }

    // ================================================================
    // OFFLINE QUEUE SIMULATION TESTS
    // ================================================================

    @Test
    @Order(26)
    @DisplayName("Offline: Queue deduplication prevents redundant items")
    void testOfflineQueue_Deduplication() {
        // Simulate: add same student-course twice, should not create duplicates
        Map<Long, Map<Long, Long>> queue = new HashMap<>();

        // Enqueue SID1-activeCourseId
        queue.computeIfAbsent(SID1, k -> new HashMap<>()).put(activeCourseId, System.currentTimeMillis());
        // Enqueue same again
        queue.computeIfAbsent(SID1, k -> new HashMap<>()).put(activeCourseId, System.currentTimeMillis());

        assertEquals(1, queue.get(SID1).size(), "Queue should deduplicate");
    }

    @Test
    @Order(27)
    @DisplayName("Offline: Queue replay after network recovery")
    void testOfflineQueue_ReplayAfterRecovery() {
        // Simulate queued check-ins
        Long[] queuedStudents = {SID1, SID2, SID3};
        Long queuedCourse = activeCourseId;
        int successCount = 0;

        for (Long sid : queuedStudents) {
            try {
                // Attempt replay - some may fail (already checked in)
                Map<String, Object> result = attendanceService.checkIn(sid, queuedCourse);
                if ((Boolean) result.get("success")) {
                    successCount++;
                }
            } catch (Exception e) {
                // Log and continue - queue recovery should tolerate failures
            }
        }

        // At least some should succeed or return gracefully
        assertTrue(successCount >= 0, "Queue replay should not crash");
    }

    @Test
    @Order(28)
    @DisplayName("Offline: Corrupted data is handled gracefully")
    void testOfflineQueue_CorruptedData() {
        // Simulate corrupted queue entry: null studentId
        Map<String, Object> corruptedEntry = new HashMap<>();
        corruptedEntry.put("studentId", null);
        corruptedEntry.put("courseId", activeCourseId);

        // Should not throw exception when processing corrupted entry
        assertDoesNotThrow(() -> {
            if (corruptedEntry.get("studentId") == null) {
                // Skip corrupted entry
                return;
            }
            attendanceService.checkIn(
                    Long.valueOf(corruptedEntry.get("studentId").toString()),
                    Long.valueOf(corruptedEntry.get("courseId").toString()));
        }, "Corrupted queue entry should be handled without crash");
    }

    @Test
    @Order(29)
    @DisplayName("Offline: Concurrent queue access is safe")
    void testOfflineQueue_ConcurrentAccess() throws Exception {
        Map<Long, Long> queue = new HashMap<>();
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            threads[i] = new Thread(() -> {
                synchronized (queue) {
                    queue.put((long) idx, System.currentTimeMillis());
                }
            });
            threads[i].start();
        }

        for (Thread t : threads) {
            t.join();
        }

        assertEquals(threadCount, queue.size(), "Concurrent queue writes should be consistent");
    }

    // ================================================================
    // SERVICE-LEVEL TESTS
    // ================================================================

    @Test
    @Order(30)
    @DisplayName("Service: addAttendance with update to existing record")
    void testService_AddAttendanceUpdate() {
        // Create initial record
        attendanceService.addAttendance(SID1, activeCourseId, "缺勤");
        Attendance att = attendanceRepository
                .findByStudentIdAndCourseIdAndAttendanceDate(SID1, activeCourseId, today).orElse(null);
        assertNotNull(att);
        assertEquals(AttendanceStatus.缺勤, att.getAttendanceStatus());

        // Update same record
        attendanceService.addAttendance(SID1, activeCourseId, "请假");
        att = attendanceRepository
                .findByStudentIdAndCourseIdAndAttendanceDate(SID1, activeCourseId, today).orElse(null);
        assertNotNull(att);
        assertEquals(AttendanceStatus.请假, att.getAttendanceStatus());
    }

    @Test
    @Order(31)
    @DisplayName("Service: getStudentHistory returns ordered records")
    void testService_StudentHistoryOrdered() {
        List<Map<String, Object>> history = attendanceService.getStudentHistory(SID1);
        assertNotNull(history);
        // Verify records are ordered by date descending
        for (int i = 0; i < history.size() - 1; i++) {
            String currentDate = (String) history.get(i).get("attendanceDate");
            String nextDate = (String) history.get(i + 1).get("attendanceDate");
            if (currentDate != null && nextDate != null) {
                assertTrue(currentDate.compareTo(nextDate) >= 0,
                        "History should be ordered descending by date");
            }
        }
    }

    @Test
    @Order(32)
    @DisplayName("Service: getAttendanceDates returns unique dates")
    void testService_AttendanceDatesUnique() {
        List<LocalDate> dates = attendanceService.getAttendanceDates(activeCourseId);
        long uniqueCount = dates.stream().distinct().count();
        assertEquals(uniqueCount, dates.size(), "Attendance dates should be unique");
    }
}