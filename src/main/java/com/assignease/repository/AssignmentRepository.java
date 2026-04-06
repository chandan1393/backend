package com.assignease.repository;

import com.assignease.entity.Assignment;
import com.assignease.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    Page<Assignment> findByStudent(User student, Pageable pageable);
    List<Assignment> findByStudent(User student);
    Page<Assignment> findByStatus(Assignment.AssignmentStatus status, Pageable pageable);
    long countByStatus(Assignment.AssignmentStatus status);
    long countByStudent(User student);

    @Query("SELECT a FROM Assignment a ORDER BY a.createdAt DESC")
    Page<Assignment> findAllOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT COUNT(a) FROM Assignment a WHERE a.student = :student AND a.status = :status")
    long countByStudentAndStatus(User student, Assignment.AssignmentStatus status);
}
