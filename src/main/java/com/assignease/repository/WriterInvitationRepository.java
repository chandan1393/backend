package com.assignease.repository;

import com.assignease.entity.WriterInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WriterInvitationRepository extends JpaRepository<WriterInvitation, Long> {
    List<WriterInvitation> findByEnrollmentIdOrderByCreatedAtDesc(Long enrollmentId);
    List<WriterInvitation> findByWriterIdOrderByCreatedAtDesc(Long writerId);
    List<WriterInvitation> findByEnrollmentIdAndStatus(Long enrollmentId, WriterInvitation.InvitationStatus status);
    Optional<WriterInvitation> findByEnrollmentIdAndWriterId(Long enrollmentId, Long writerId);
}
