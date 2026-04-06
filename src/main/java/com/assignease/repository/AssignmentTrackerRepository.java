package com.assignease.repository;

import com.assignease.entity.AssignmentTracker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.util.List;

public interface AssignmentTrackerRepository extends JpaRepository<AssignmentTracker, Long> {
    List<AssignmentTracker> findByEnrollmentIdOrderByDueDateAsc(Long enrollmentId);

    @Query("SELECT t FROM AssignmentTracker t WHERE t.enrollment.student.id = :studentId ORDER BY t.dueDate ASC")
    List<AssignmentTracker> findByStudentId(Long studentId);

    @Query("SELECT t FROM AssignmentTracker t WHERE t.enrollment.assignedWriter.id = :writerId ORDER BY t.dueDate ASC")
    List<AssignmentTracker> findByWriterId(Long writerId);

    @Query("SELECT t FROM AssignmentTracker t WHERE t.dueDate BETWEEN :from AND :to ORDER BY t.dueDate ASC")
    List<AssignmentTracker> findDueBetween(LocalDate from, LocalDate to);

    @Query("SELECT t FROM AssignmentTracker t WHERE t.enrollment.assignedWriter.id = :writerId AND t.dueDate BETWEEN :from AND :to ORDER BY t.dueDate ASC")
    List<AssignmentTracker> findByWriterIdDueBetween(Long writerId, LocalDate from, LocalDate to);
}
