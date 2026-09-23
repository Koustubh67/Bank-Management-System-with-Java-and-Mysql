package com.koustubh.bank.repository;

import com.koustubh.bank.domain.Account;
import com.koustubh.bank.domain.AccountStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    boolean existsByAccountNumber(String accountNumber);

    @Query("select a.id from Account a where a.accountNumber = :accountNumber")
    Optional<Long> findIdByAccountNumber(String accountNumber);

    @EntityGraph(attributePaths = "customer")
    Optional<Account> findWithCustomerById(Long id);

    /** SELECT ... FOR UPDATE: other transactions wait until this one commits. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(Long id);

    @EntityGraph(attributePaths = "customer")
    List<Account> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = "customer")
    List<Account> findByStatusOrderByCreatedAtDesc(AccountStatus status);

    long countByStatus(AccountStatus status);

    @Query("select coalesce(sum(a.balance), 0) from Account a")
    BigDecimal totalBalance();
}
