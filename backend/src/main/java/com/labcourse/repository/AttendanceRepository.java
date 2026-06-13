package com.labcourse.repository;

import com.labcourse.entity.Attendance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    Optional<Attendance> findByStudentIdAndCourseId(Long studentId, Long courseId);

    Optional<Attendance> findByStudentIdAndCourseIdAndAttendanceDate(Long studentId, Long courseId, LocalDate date);

    /**
     * 悲观写锁查询，用于并发签到场景防止竞态条件
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Attendance a WHERE a.studentId = :studentId AND a.courseId = :courseId AND a.attendanceDate = :date")
    Optional<Attendance> findByStudentIdAndCourseIdAndAttendanceDateForUpdate(
            @Param("studentId") Long studentId,
            @Param("courseId") Long courseId,
            @Param("date") LocalDate date);

    List<Attendance> findByStudentIdOrderByAttendanceDateDesc(Long studentId);

    List<Attendance> findByCourseIdAndAttendanceDate(Long courseId, LocalDate date);

    List<Attendance> findByCourseIdOrderByAttendanceDateDesc(Long courseId);

    boolean existsByStudentIdAndCourseIdAndAttendanceDate(Long studentId, Long courseId, LocalDate date);
}