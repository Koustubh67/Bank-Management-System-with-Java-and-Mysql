package com.koustubh.bank.repository;

import com.koustubh.bank.domain.EmiPayment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EmiPaymentRepository extends JpaRepository<EmiPayment, Long> {

    @EntityGraph(attributePaths = {"loan", "loan.account", "loan.account.customer", "instalment"})
    Optional<EmiPayment> findByReference(String reference);

    @EntityGraph(attributePaths = {"loan", "loan.account", "instalment"})
    List<EmiPayment> findByLoanIdOrderByIdDesc(Long loanId);

    List<EmiPayment> findByLoanIdAndStatus(Long loanId, EmiPayment.Status status);

    boolean existsByReference(String reference);

    @Query("select p.loan.id from EmiPayment p where p.reference = :reference")
    Optional<Long> findLoanIdByReference(String reference);

    /** Card and UPI payments not completed within their 5 minutes. */
    @Transactional
    @Modifying
    @Query("update EmiPayment p set p.status = com.koustubh.bank.domain.EmiPayment.Status.EXPIRED, "
            + "p.failureReason = 'Not paid within 5 minutes', p.otpHash = null, p.version = p.version + 1 "
            + "where p.status = com.koustubh.bank.domain.EmiPayment.Status.PENDING and p.expiresAt < :now")
    int expireOlderThan(LocalDateTime now);
}
