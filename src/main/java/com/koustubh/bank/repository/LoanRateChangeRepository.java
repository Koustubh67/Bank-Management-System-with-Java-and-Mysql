package com.koustubh.bank.repository;

import com.koustubh.bank.domain.LoanRateChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoanRateChangeRepository extends JpaRepository<LoanRateChange, Long> {

    List<LoanRateChange> findByLoanIdOrderByIdAsc(Long loanId);
}
