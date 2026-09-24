package com.koustubh.bank.repository;

import com.koustubh.bank.domain.Investment;
import com.koustubh.bank.domain.InvestmentType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface InvestmentRepository extends JpaRepository<Investment, Long> {

    List<Investment> findByAccountIdOrderByIdDesc(Long accountId);

    boolean existsByReference(String reference);

    @EntityGraph(attributePaths = {"account", "account.customer"})
    List<Investment> findAllByOrderByIdDesc();

    /** SIPs whose next instalment is due, for the monthly debit job. */
    List<Investment> findByTypeAndNextDebitDateLessThanEqual(InvestmentType type,
                                                              LocalDate date);
}
