package com.assignease.repository;
import com.assignease.entity.BugReport;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BugReportRepository extends JpaRepository<BugReport, Long> {}
