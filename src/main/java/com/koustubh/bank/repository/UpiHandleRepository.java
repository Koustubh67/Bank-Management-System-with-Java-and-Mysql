package com.koustubh.bank.repository;

import com.koustubh.bank.domain.UpiHandle;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UpiHandleRepository extends JpaRepository<UpiHandle, Long> {

    @EntityGraph(attributePaths = {"account", "account.customer"})
    Optional<UpiHandle> findByVpa(String vpa);

    Optional<UpiHandle> findByAccountId(Long accountId);

    boolean existsByVpa(String vpa);
}
