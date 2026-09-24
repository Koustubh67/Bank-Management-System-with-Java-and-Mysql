package com.koustubh.bank.repository;

import com.koustubh.bank.domain.LoanEnquiry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoanEnquiryRepository extends JpaRepository<LoanEnquiry, Long> {

    List<LoanEnquiry> findTop100ByOrderByIdDesc();

    long countByStatus(LoanEnquiry.Status status);

    boolean existsByReference(String reference);
}
