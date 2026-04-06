package com.assignease.repository;

import com.assignease.entity.TextMeLead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TextMeLeadRepository extends JpaRepository<TextMeLead, Long> {
    List<TextMeLead> findByStatusOrderByCreatedAtDesc(TextMeLead.LeadStatus status);
    List<TextMeLead> findAllByOrderByCreatedAtDesc();
    boolean existsByPhoneNumber(String phoneNumber);
}
