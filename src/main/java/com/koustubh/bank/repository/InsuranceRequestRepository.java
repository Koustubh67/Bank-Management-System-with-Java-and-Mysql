package com.koustubh.bank.repository;

import com.koustubh.bank.domain.InsuranceRequest;
import com.koustubh.bank.domain.InsuranceRequestStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface InsuranceRequestRepository extends JpaRepository<InsuranceRequest, Long> {

    List<InsuranceRequest> findByAccountIdOrderByIdDesc(Long accountId);

    @EntityGraph(attributePaths = {"account", "account.customer"})
    List<InsuranceRequest> findByStatusInOrderByIdAsc(Collection<InsuranceRequestStatus> statuses);

    @EntityGraph(attributePaths = {"account", "account.customer"})
    List<InsuranceRequest> findTop100ByOrderByIdDesc();

    long countByStatusIn(Collection<InsuranceRequestStatus> statuses);

    boolean existsByReference(String reference);
}
