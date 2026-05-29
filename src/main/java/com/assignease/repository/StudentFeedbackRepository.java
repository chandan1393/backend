package com.assignease.repository;

import com.assignease.entity.StudentFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StudentFeedbackRepository extends JpaRepository<StudentFeedback, Long> {
    List<StudentFeedback> findByVisibleTrueOrderByCreatedAtDesc();
}
