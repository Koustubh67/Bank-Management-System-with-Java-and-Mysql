package com.koustubh.bank.repository;

import com.koustubh.bank.domain.Card;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CardRepository extends JpaRepository<Card, Long> {

    @EntityGraph(attributePaths = "account")
    Optional<Card> findByCardNumber(String cardNumber);

    Optional<Card> findByAccountId(Long accountId);

    /** Only the id, so the account row can then be loaded fresh with a lock. */
    @Query("select c.account.id from Card c where c.cardNumber = :cardNumber")
    Optional<Long> findAccountIdByCardNumber(String cardNumber);

    boolean existsByCardNumber(String cardNumber);
}
