package com.koustubh.bank.repository;

import com.koustubh.bank.domain.InstalmentStatus;
import com.koustubh.bank.domain.LoanInstalment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LoanInstalmentRepository extends JpaRepository<LoanInstalment, Long> {

    List<LoanInstalment> findByLoanIdOrderByNumberAsc(Long loanId);

    /** The earliest instalment not yet paid. */
    Optional<LoanInstalment> findFirstByLoanIdAndStatusInOrderByNumberAsc(Long loanId, Collection<InstalmentStatus> statuses);

    /** Loans with at least one unpaid EMI due on or before the date, for the daily collection job. */
    @Query("""
            select distinct i.loan.id from LoanInstalment i
            where i.status in (com.koustubh.bank.domain.InstalmentStatus.DUE, com.koustubh.bank.domain.InstalmentStatus.OVERDUE)
              and i.dueDate <= :date
            """)
    List<Long> loansWithEmisDueBy(LocalDate date);

    long countByStatus(InstalmentStatus status);

    long countByLoanIdAndStatus(Long loanId, InstalmentStatus status);
}
