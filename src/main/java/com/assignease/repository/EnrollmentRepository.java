package com.assignease.repository;

import com.assignease.entity.Enrollment;
import com.assignease.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    List<Enrollment> findByStudentOrderByCreatedAtDesc(User student);
    Page<Enrollment> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<Enrollment> findByAssignedWriterOrderByCreatedAtDesc(User writer);
    long countByStatus(Enrollment.EnrollmentStatus status);
    long countByStudent(User student);
}
