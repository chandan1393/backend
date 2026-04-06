package com.assignease.repository;

import com.assignease.entity.PaymentInstallment;
import com.assignease.entity.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface PaymentInstallmentRepository extends JpaRepository<PaymentInstallment, Long> {
    List<PaymentInstallment> findByEnrollmentOrderByInstallmentNumberAsc(Enrollment enrollment);
    List<PaymentInstallment> findByEnrollmentId(Long enrollmentId);

    // Find installments due tomorrow that haven't had reminders sent
    @Query("SELECT p FROM PaymentInstallment p WHERE p.dueDate = :tomorrow AND p.reminderSent = false AND p.status = 'PENDING'")
    List<PaymentInstallment> findInstallmentsDueTomorrow(LocalDate tomorrow);

    @Query("SELECT p FROM PaymentInstallment p WHERE p.status = 'CONFIRMED' AND p.enrollment.id = :enrollmentId")
    List<PaymentInstallment> findConfirmedByEnrollment(Long enrollmentId);

    @Query("SELECT p FROM PaymentInstallment p WHERE p.enrollment.student.id = :studentId")
    List<PaymentInstallment> findByEnrollmentStudentId(Long studentId);
}
