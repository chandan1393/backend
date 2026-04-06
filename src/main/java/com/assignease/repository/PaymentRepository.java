package com.assignease.repository;

import com.assignease.entity.Payment;
import com.assignease.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByRazorpayOrderId(String orderId);
    List<Payment> findByStudent(User student);
    List<Payment> findByAssignmentId(Long assignmentId);
}
