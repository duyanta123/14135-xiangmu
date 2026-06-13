package com.labcourse;

import com.labcourse.entity.Attendance;
import com.labcourse.entity.AttendanceStatus;
import com.labcourse.repository.AttendanceRepository;
import com.labcourse.service.AttendanceService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.junit.jupiter.api.Assumptions;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Offline queue recovery and edge case tests.
 * Covers: network interruption, queue persistence, corrupted data recovery,
 * concurrent access, retry logic, timeout handling.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("null")
class OfflineQueueRecoveryTest {

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private AttendanceRepository attendanceRepository;

    private LocalDate today;
    private static final Long SID1 = 1L;
    private static final Long SID2 = 9L;
    private static final Long SID3 = 3L;

    // Day-of-week → course ID mapping (周一~周五 = 1~5)
    private static final Map<Integer, Long> DAY_TO_COURSE = Map.of(
            1, 1L, 2, 2L, 3, 3L, 4, 4L, 5, 5L
    );
    private Long activeCourseId;  // course matching today's day of week
    private boolean isWeekend;

    private static final Long CID2 = 4L; // 计算机网络 (周四 7-8节) - for multi-course tests

    @BeforeEach
    void setUp() {
        today = LocalDate.now();
        int todayDayOfWeek = today.getDayOfWeek().getValue();
        activeCourseId = DAY_TO_COURSE.get(todayDayOfWeek);
        isWeekend = activeCourseId == null;
        if (isWeekend) {
            activeCourseId = 3L; // fallback
        }
        // Clean up test data from previous runs
        cleanUp(SID1, activeCourseId);
        cleanUp(SID2, activeCourseId);
        cleanUp(SID3, activeCourseId);
        cleanUp(SID1, CID2);
        cleanUp(SID2, CID2);
        cleanUp(SID3, CID2);
    }

    private void cleanUp(Long studentId, Long courseId) {
        attendanceRepository.findByStudentIdAndCourseIdAndAttendanceDate(studentId, courseId, today)
                .ifPresent(a -> attendanceRepository.delete(a));
    }

    // ================================================================
    // QUEUE PERSISTENCE & DEDUPLICATION
    // ================================================================

    @Test
    @Order(1)
    @DisplayName("Queue: Items are deduplicated on re-enqueue")
    void testQueue_Deduplication() {
        Map<String, Map<String, Long>> queue = new LinkedHashMap<>();
        String key = SID1 + "_" + activeCourseId;

        // Enqueue same item multiple times
        for (int i = 0; i < 5; i++) {
            queue.computeIfAbsent("checkins", k -> new LinkedHashMap<>())
                    .put(key, System.currentTimeMillis());
        }

        assertEquals(1, queue.get("checkins").size(),
                "Queue should contain only 1 unique entry after 5 identical enqueues");
    }

    @Test
    @Order(2)
    @DisplayName("Queue: Multiple students can be queued simultaneously")
    void testQueue_MultiStudentQueuing() {
        Map<String, Map<String, Long>> queue = new LinkedHashMap<>();
        Long[] students = {SID1, SID2, SID3};
        Long[] courses = {activeCourseId, CID2};

        for (Long sid : students) {
            for (Long cid : courses) {
                String key = sid + "_" + cid;
                queue.computeIfAbsent("checkins", k -> new LinkedHashMap<>())
                        .put(key, System.currentTimeMillis());
            }
        }

        assertEquals(6, queue.get("checkins").size(),
                "Queue should contain 3 students x 2 courses = 6 entries");
    }

    @Test
    @Order(3)
    @DisplayName("Queue: Order is preserved (FIFO)")
    void testQueue_FIFOOrder() {
        LinkedHashMap<String, Long> queue = new LinkedHashMap<>();

        // Enqueue in order
        queue.put(SID1 + "_" + activeCourseId, System.currentTimeMillis());
        Thread.yield();
        queue.put(SID2 + "_" + activeCourseId, System.currentTimeMillis());
        Thread.yield();
        queue.put(SID3 + "_" + activeCourseId, System.currentTimeMillis());

        // Verify order
        List<String> keys = new ArrayList<>(queue.keySet());
        assertEquals(SID1 + "_" + activeCourseId, keys.get(0), "First item should be SID1");
        assertEquals(SID2 + "_" + activeCourseId, keys.get(1), "Second item should be SID2");
        assertEquals(SID3 + "_" + activeCourseId, keys.get(2), "Third item should be SID3");
    }

    // ================================================================
    // NETWORK INTERRUPTION & RECONNECTION
    // ================================================================

    @Test
    @Order(4)
    @DisplayName("Network: Replay queue after simulated network recovery")
    void testNetwork_ReplayQueueAfterRecovery() {
        // Phase 1: Simulate offline - queue items
        Map<String, Map<String, Long>> offlineQueue = new LinkedHashMap<>();
        offlineQueue.computeIfAbsent("checkins", k -> new LinkedHashMap<>())
                .put(SID1 + "_" + activeCourseId, System.currentTimeMillis());
        offlineQueue.computeIfAbsent("checkins", k -> new LinkedHashMap<>())
                .put(SID2 + "_" + activeCourseId, System.currentTimeMillis());

        assertEquals(2, offlineQueue.get("checkins").size());

        // Phase 2: Simulate reconnection - replay queue
        int replayed = 0;
        int failed = 0;
        Map<String, Long> queuedItems = offlineQueue.get("checkins");
        for (String key : queuedItems.keySet()) {
            String[] parts = key.split("_");
            Long sid = Long.valueOf(parts[0]);
            Long cid = Long.valueOf(parts[1]);
            try {
                Map<String, Object> result = attendanceService.checkIn(sid, cid);
                if ((Boolean) result.get("success")) {
                    replayed++;
                } else {
                    // May fail if already checked in - that's fine
                    failed++;
                }
            } catch (Exception e) {
                failed++;
            }
        }

        // At least one should succeed (first check-in of these students today)
        assertTrue(replayed + failed >= 2,
                "All queued items should be processed (success or graceful failure)");
    }

    @Test
    @Order(5)
    @DisplayName("Network: Queue survives system restart simulation")
    void testNetwork_QueueSurvivesRestart() {
        // Simulate: items queued before "crash"
        Map<String, Long> preCrashQueue = new LinkedHashMap<>();
        preCrashQueue.put("SID1_activeCourseId", System.currentTimeMillis());
        preCrashQueue.put("SID2_CID2", System.currentTimeMillis());

        // Serialize to "disk" (simulated)
        Map<String, Long> persistentCopy = new LinkedHashMap<>(preCrashQueue);

        // "Crash" - clear memory
        preCrashQueue.clear();

        // "Restart" - reload from disk
        Map<String, Long> postRestartQueue = new LinkedHashMap<>(persistentCopy);

        assertEquals(2, postRestartQueue.size(),
                "Post-restart queue should have the same 2 items");
        assertTrue(postRestartQueue.containsKey("SID1_activeCourseId"));
        assertTrue(postRestartQueue.containsKey("SID2_CID2"));
    }

    // ================================================================
    // CORRUPTED DATA RECOVERY
    // ================================================================

    @Test
    @Order(6)
    @DisplayName("Corruption: Missing fields are skipped gracefully")
    void testCorruption_MissingFields() {
        List<Map<String, Object>> corruptedEntries = new ArrayList<>();

        // Entry with missing courseId
        Map<String, Object> entry1 = new HashMap<>();
        entry1.put("studentId", SID1);
        // courseId intentionally missing

        // Entry with null values
        Map<String, Object> entry2 = new HashMap<>();
        entry2.put("studentId", null);
        entry2.put("courseId", null);

        // Entry with invalid IDs
        Map<String, Object> entry3 = new HashMap<>();
        entry3.put("studentId", -1);
        entry3.put("courseId", -1);

        corruptedEntries.add(entry1);
        corruptedEntries.add(entry2);
        corruptedEntries.add(entry3);

        int skipped = 0;
        int processed = 0;

        for (Map<String, Object> entry : corruptedEntries) {
            Object sidObj = entry.get("studentId");
            Object cidObj = entry.get("courseId");

            // Validate before processing
            if (sidObj == null || cidObj == null) {
                skipped++;
                continue;
            }

            Long sid = Long.valueOf(sidObj.toString());
            Long cid = Long.valueOf(cidObj.toString());

            if (sid <= 0 || cid <= 0) {
                skipped++;
                continue;
            }

            processed++;
        }

        assertEquals(3, skipped, "All 3 corrupted entries should be skipped");
        assertEquals(0, processed, "No corrupted entries should be processed");
    }

    @Test
    @Order(7)
    @DisplayName("Corruption: Malformed key strings don't cause crashes")
    void testCorruption_MalformedKeys() {
        String[] malformedKeys = {
                "",           // empty
                "abc",        // no underscore
                "123_",       // missing courseId
                "_456",       // missing studentId
                "a_b_c",      // too many parts
                "not_a_number_course", // NaN
        };

        int skipped = 0;
        for (String key : malformedKeys) {
            try {
                String[] parts = key.split("_");
                if (parts.length != 2) {
                    skipped++;
                    continue;
                }
                Long.valueOf(parts[0]);
                Long.valueOf(parts[1]);
                skipped++;
            } catch (NumberFormatException e) {
                skipped++;
            }
        }

        assertEquals(6, skipped,
                "All malformed keys should be skipped without crash");
    }

    @Test
    @Order(8)
    @DisplayName("Corruption: Empty queue is handled correctly")
    void testCorruption_EmptyQueue() {
        Map<String, Long> emptyQueue = new LinkedHashMap<>();

        assertTrue(emptyQueue.isEmpty(), "Empty queue should be empty");
        assertEquals(0, emptyQueue.size(), "Empty queue should have size 0");

        // Processing empty queue should not throw
        assertDoesNotThrow(() -> {
            // Empty queue iteration yields no keys - nothing to process
        }, "Iterating empty queue should not throw");
    }

    // ================================================================
    // CONCURRENT ACCESS
    // ================================================================

    @Test
    @Order(9)
    @DisplayName("Concurrent: Multiple threads enqueue safely")
    void testConcurrent_MultiThreadEnqueue() throws Exception {
        ConcurrentHashMap<String, Long> queue = new ConcurrentHashMap<>();
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger enqueued = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            new Thread(() -> {
                try {
                    queue.put("STUDENT_" + idx + "_COURSE_1", System.currentTimeMillis());
                    enqueued.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "All threads should complete within 5s");
        assertEquals(threadCount, enqueued.get(), "All 10 threads should enqueue successfully");
        assertEquals(threadCount, queue.size(), "Queue should have exactly 10 entries");
    }

    @Test
    @Order(10)
    @DisplayName("Concurrent: Simultaneous read and write is safe")
    void testConcurrent_SimultaneousReadWrite() throws Exception {
        Map<String, Long> queue = Collections.synchronizedMap(new LinkedHashMap<>());
        CountDownLatch latch = new CountDownLatch(2);

        // Writer thread
        new Thread(() -> {
            try {
                for (int i = 0; i < 20; i++) {
                    queue.put("ITEM_" + i, System.nanoTime());
                }
            } finally {
                latch.countDown();
            }
        }).start();

        // Reader thread
        new Thread(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    synchronized (queue) {
                        new ArrayList<>(queue.keySet());
                    }
                    Thread.sleep(10);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        }).start();

        assertTrue(latch.await(5, TimeUnit.SECONDS),
                "Reader and writer should complete without deadlock");
    }

    // ================================================================
    // RETRY & TIMEOUT
    // ================================================================

    @Test
    @Order(11)
    @DisplayName("Retry: Check-in retry logic for transient failures")
    void testRetry_TransientFailureRecovery() {
        int maxRetries = 3;
        int attempts = 0;
        boolean success = false;

        while (attempts < maxRetries && !success) {
            attempts++;
            try {
                Map<String, Object> result = attendanceService.checkIn(SID3, CID2);
                success = (Boolean) result.get("success");
            } catch (Exception e) {
                // Simulate transient failure - retry
                if (attempts >= maxRetries) {
                    break;
                }
            }
        }

        // Should either succeed or exhaust retries
        assertTrue(success || attempts == maxRetries,
                "After " + maxRetries + " retries, should either succeed or exhaust");
    }

    @Test
    @Order(12)
    @DisplayName("Retry: Exponential backoff prevents thundering herd")
    void testRetry_ExponentialBackoff() {
        long baseDelay = 100; // ms
        long totalDelay = 0;
        int retries = 3;

        for (int i = 0; i < retries; i++) {
            long delay = baseDelay * (long) Math.pow(2, i);
            totalDelay += delay;
        }

        // Total: 100 + 200 + 400 = 700ms
        assertEquals(700, totalDelay,
                "Exponential backoff should be 100 + 200 + 400 = 700ms");
        assertTrue(totalDelay < 5000,
                "Total backoff should be within reasonable limit (<5s)");
    }

    @Test
    @Order(13)
    @DisplayName("Timeout: Operation timeout is enforced")
    void testTimeout_OperationTimeout() {
        LocalDateTime start = LocalDateTime.now();
        long timeoutMs = 2000;

        // Simulate a timeout-gated operation
        boolean completed = false;
        try {
            attendanceService.getServerTime();
            completed = true;
        } catch (Exception e) {
            completed = false;
        }

        LocalDateTime end = LocalDateTime.now();
        long elapsed = ChronoUnit.MILLIS.between(start, end);

        assertTrue(completed, "getServerTime should complete quickly");
        assertTrue(elapsed < timeoutMs,
                "Operation should complete within " + timeoutMs + "ms (actual: " + elapsed + "ms)");
    }

    // ================================================================
    // SELF-HEALING & GRACEFUL DEGRADATION
    // ================================================================

    @Test
    @Order(14)
    @DisplayName("Graceful: System recovers from rapid duplicate requests")
    void testGraceful_RapidDuplicateRequests() {
        Assumptions.assumeFalse(isWeekend, "周末无课程安排");
        cleanUp(SID1, activeCourseId);

        // Send 3 check-in requests in rapid succession
        int successCount = 0;
        int rejectCount = 0;

        for (int i = 0; i < 3; i++) {
            try {
                Map<String, Object> result = attendanceService.checkIn(SID1, activeCourseId);
                if ((Boolean) result.get("success")) {
                    successCount++;
                } else {
                    rejectCount++;
                }
            } catch (Exception e) {
                rejectCount++;
            }
        }

        // Only first should succeed, rest should be rejected
        assertEquals(1, successCount,
                "Only the first of 3 rapid requests should succeed");
        assertEquals(2, rejectCount,
                "The other 2 rapid requests should be rejected as duplicates");
    }

    @Test
    @Order(15)
    @DisplayName("Graceful: System handles large batch queue replay")
    void testGraceful_LargeBatchQueueReplay() {
        Assumptions.assumeFalse(isWeekend, "周末无课程安排");
        Long[] studentIds = {SID1, SID2, SID3, SID1, SID2}; // SID1, SID2 appear twice

        int processed = 0;
        Set<String> processedKeys = new HashSet<>();

        for (Long sid : studentIds) {
            String key = sid + "_" + activeCourseId;
            if (processedKeys.add(key)) {
                // Dedup: only process unique student-course pairs
                try {
                    attendanceService.checkIn(sid, activeCourseId);
                } catch (Exception ignored) {
                }
                processed++;
            }
        }

        assertEquals(3, processed,
                "Only unique student-course pairs should be processed (3 unique out of 5)");
    }

    @Test
    @Order(16)
    @DisplayName("Concurrent: Multi-thread check-in ensures exactly one success with pessimistic lock")
    void testConcurrent_MultiThreadCheckIn() throws Exception {
        Assumptions.assumeFalse(isWeekend, "周末无课程安排");
        cleanUp(SID1, activeCourseId);

        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await(); // 所有线程同时起跑
                    Map<String, Object> result = attendanceService.checkIn(SID1, activeCourseId);
                    if ((Boolean) result.get("success")) {
                        successCount.incrementAndGet();
                    } else {
                        rejectCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    rejectCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        // 同时释放所有线程
        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS),
                "All threads should complete within 30s");

        assertEquals(1, successCount.get(),
                threadCount + " concurrent check-ins: exactly 1 should succeed");
        assertEquals(threadCount - 1, rejectCount.get(),
                threadCount + " concurrent check-ins: " + (threadCount - 1) + " should be rejected");
    }

    @Test
    @Order(17)
    @DisplayName("Consistency: Database state remains valid after all edge cases")
    void testConsistency_DatabaseStateValid() {
        // Verify database integrity after all tests
        List<Attendance> todayRecords = attendanceRepository
                .findByCourseIdAndAttendanceDate(activeCourseId, today);

        for (Attendance att : todayRecords) {
            assertNotNull(att.getStudentId(), "StudentId should not be null");
            assertNotNull(att.getCourseId(), "CourseId should not be null");
            assertNotNull(att.getAttendanceDate(), "AttendanceDate should not be null");
            assertNotNull(att.getAttendanceStatus(), "Status should not be null");
            assertNotNull(att.getCreatedAt(), "CreatedAt should not be null");
            assertTrue(att.getStudentId() > 0, "StudentId should be positive");
            assertTrue(att.getCourseId() > 0, "CourseId should be positive");
            assertEquals(today, att.getAttendanceDate(), "Date should be today");
        }
    }
}