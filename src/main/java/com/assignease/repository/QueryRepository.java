package com.assignease.repository;

import com.assignease.entity.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QueryRepository extends JpaRepository<Query, Long> {
    Page<Query> findByStatus(Query.QueryStatus status, Pageable pageable);
    long countByStatus(Query.QueryStatus status);
    Page<Query> findByEmail(String email, Pageable pageable);
}
