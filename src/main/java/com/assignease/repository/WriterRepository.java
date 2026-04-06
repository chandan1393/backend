package com.assignease.repository;

import com.assignease.entity.Writer;
import com.assignease.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WriterRepository extends JpaRepository<Writer, Long> {
    Optional<Writer> findByUser(User user);
    List<Writer> findByAvailableTrue();
}
