package com.labcourse.repository;

import com.labcourse.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    Optional<Attendance> findByStudentIdAndCourseId(Long studentId, Long courseId);

    Optional<Attendance> findByStudentIdAndCourseIdAndAttendanceDate(Long studentId, Long courseId, LocalDate date);

    List<Attendance> findByStudentIdOrderByAttendanceDateDesc(Long studentId);

    List<Attendance> findByCourseIdAndAttendanceDate(Long courseId, LocalDate date);

    List<Attendance> findByCourseIdOrderByAttendanceDateDesc(Long courseId);

    boolean existsByStudentIdAndCourseIdAndAttendanceDate(Long studentId, Long courseId, LocalDate date);
}