package com.koustubh.bank.repository;

import com.koustubh.bank.domain.Transaction;
import com.koustubh.bank.domain.TransactionType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByAccountIdOrderByIdDesc(Long accountId, Pageable pageable);

    @EntityGraph(attributePaths = "account")
    List<Transaction> findAllByOrderByIdDesc(Pageable pageable);

    @Query("""
            select coalesce(sum(t.amount), 0) from Transaction t
            where t.account.id = :accountId and t.type = :type and t.createdAt >= :since
            """)
    BigDecimal sumSince(Long accountId, TransactionType type, LocalDateTime since);
}
