package com.assignease.repository;
import com.assignease.entity.UserEvent;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UserEventRepository extends JpaRepository<UserEvent, Long> {}
