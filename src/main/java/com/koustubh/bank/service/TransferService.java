package com.koustubh.bank.service;

import com.koustubh.bank.domain.Account;
import com.koustubh.bank.domain.AccountStatus;
import com.koustubh.bank.domain.Transaction;
import com.koustubh.bank.domain.TransactionType;
import com.koustubh.bank.exception.InvalidRequestException;
import com.koustubh.bank.exception.NotFoundException;
import com.koustubh.bank.repository.AccountRepository;
import com.koustubh.bank.repository.CardRepository;
import com.koustubh.bank.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class TransferService {

    private final CardRepository cards;
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final Clock clock;

    public TransferService(CardRepository cards, AccountRepository accounts, TransactionRepository transactions,
                           Clock clock) {
        this.cards = cards;
        this.accounts = accounts;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * Moves money between two accounts in one database transaction: both the debit and the credit
     * are saved, or neither is.
     */
    @Transactional
    public Transaction transfer(String fromCardNumber, String toAccountNumber, BigDecimal amount) {
        Amounts.requirePositive(amount);
        Long fromId = cards.findAccountIdByCardNumber(fromCardNumber)
                .orElseThrow(() -> new NotFoundException("Card not found"));
        Long toId = accounts.findIdByAccountNumber(toAccountNumber)
                .orElseThrow(() -> new NotFoundException("Beneficiary account not found"));
        if (fromId.equals(toId)) {
            throw new InvalidRequestException("You cannot transfer money to your own account");
        }
        return moveMoney(fromId, toId, amount, TransactionType.TRANSFER_OUT, TransactionType.TRANSFER_IN, null, null);
    }

    /**
     * The money movement shared by ATM transfers and UPI payments. Both legs are saved in the caller's
     * transaction, so either both happen or neither does. Returns the debit leg.
     */
    @Transactional
    public Transaction moveMoney(Long fromId, Long toId, BigDecimal amount, TransactionType outType,
                                 TransactionType inType, String outRemarks, String inRemarks) {
        // Always lock the lower id first. If two customers send money to each other at the same time,
        // both lock in the same order, so they can never wait on each other forever (deadlock).
        Account first = lock(Math.min(fromId, toId));
        Account second = lock(Math.max(fromId, toId));
        Account from = first.getId().equals(fromId) ? first : second;
        Account to = from == first ? second : first;

        if (to.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidRequestException("Beneficiary account cannot receive transfers");
        }
        from.debit(amount);
        to.credit(amount);

        String reference = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now(clock);
        Transaction out = transactions.save(new Transaction(from, outType, amount, reference,
                to.getAccountNumber(), outRemarks, now));
        transactions.save(new Transaction(to, inType, amount, reference,
                from.getAccountNumber(), inRemarks, now));
        return out;
    }

    private Account lock(Long id) {
        return accounts.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Account not found"));
    }
}
