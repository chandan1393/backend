package com.assignease.repository;

import com.assignease.entity.WriterSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WriterSubmissionRepository extends JpaRepository<WriterSubmission, Long> {
    List<WriterSubmission> findByEnrollmentIdOrderByUploadedAtDesc(Long enrollmentId);
    List<WriterSubmission> findByWriterIdOrderByUploadedAtDesc(Long writerId);

    List<WriterSubmission> findByEnrollmentIdAndStatusOrderByUploadedAtDesc(Long enrollmentId, WriterSubmission.SubmissionStatus status);
}
