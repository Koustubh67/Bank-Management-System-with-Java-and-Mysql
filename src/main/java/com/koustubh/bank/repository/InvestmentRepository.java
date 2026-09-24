package com.koustubh.bank.repository;

import com.koustubh.bank.domain.Investment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvestmentRepository extends JpaRepository<Investment, Long> {

    List<Investment> findByAccountIdOrderByIdDesc(Long accountId);

    boolean existsByReference(String reference);
}
