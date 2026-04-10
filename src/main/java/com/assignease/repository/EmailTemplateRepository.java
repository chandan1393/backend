package com.assignease.repository;


import com.assignease.entity.EmailTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {

    Optional<EmailTemplate> findByNameAndActiveTrue(String name);
}