package com.koustubh.bank.repository;

import com.koustubh.bank.domain.InsurancePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InsurancePolicyRepository extends JpaRepository<InsurancePolicy, Long> {

    List<InsurancePolicy> findByAccountIdOrderByIdDesc(Long accountId);

    boolean existsByPolicyNumber(String policyNumber);
}
