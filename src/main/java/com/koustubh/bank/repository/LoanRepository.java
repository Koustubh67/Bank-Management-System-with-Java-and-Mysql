package com.koustubh.bank.repository;

import com.koustubh.bank.domain.Loan;
import com.koustubh.bank.domain.LoanStatus;
import com.koustubh.bank.domain.LoanType;
import com.koustubh.bank.domain.RateType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    List<Loan> findByAccountIdOrderByIdDesc(Long accountId);

    @EntityGraph(attributePaths = {"account", "account.customer"})
    List<Loan> findByStatusInOrderByIdAsc(Collection<LoanStatus> statuses);

    @EntityGraph(attributePaths = {"account", "account.customer"})
    Optional<Loan> findWithCustomerById(Long id);

    /** Locks the loan row while it is approved, rejected or repaid. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Loan l where l.id = :id")
    Optional<Loan> findByIdForUpdate(Long id);

    /** Locks every loan in the given state with the given rate type, in id order (used to reprice floating loans). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Loan l where l.status = :status and l.rateType = :rateType order by l.id")
    List<Loan> findForUpdate(LoanStatus status, RateType rateType);

    long countByStatusAndRateType(LoanStatus status, RateType rateType);

    boolean existsByAccountIdAndTypeAndStatus(Long accountId, LoanType type, LoanStatus status);

    boolean existsByReference(String reference);

    long countByStatus(LoanStatus status);
}
