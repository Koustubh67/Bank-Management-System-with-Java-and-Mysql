package com.koustubh.bank.service;

import com.koustubh.bank.config.BankProperties;
import com.koustubh.bank.domain.Account;
import com.koustubh.bank.domain.Card;
import com.koustubh.bank.domain.Transaction;
import com.koustubh.bank.domain.TransactionType;
import com.koustubh.bank.exception.DailyLimitExceededException;
import com.koustubh.bank.exception.InvalidRequestException;
import com.koustubh.bank.exception.NotFoundException;
import com.koustubh.bank.repository.AccountRepository;
import com.koustubh.bank.repository.CardRepository;
import com.koustubh.bank.repository.TransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Operations a customer performs at the ATM, identified by the card they logged in with. */
@Service
public class AtmService {

    private static final int MINI_STATEMENT_SIZE = 10;

    private final CardRepository cards;
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final Clock clock;
    private final BankProperties.Atm limits;

    public AtmService(CardRepository cards, AccountRepository accounts, TransactionRepository transactions,
                      Clock clock, BankProperties properties) {
        this.cards = cards;
        this.accounts = accounts;
        this.transactions = transactions;
        this.clock = clock;
        this.limits = properties.atm();
    }

    @Transactional(readOnly = true)
    public AccountSummary summary(String cardNumber) {
        Card card = cards.findByCardNumber(cardNumber).orElseThrow(() -> new NotFoundException("Card not found"));
        Account account = card.getAccount();
        return new AccountSummary(account.getCustomer().getFullName(), account.getAccountNumber(),
                account.getMaskedNumber(), account.getAccountType().getLabel(), card.getMaskedNumber(),
                account.getBalance());
    }

    @Transactional
    public Transaction deposit(String cardNumber, BigDecimal amount) {
        Amounts.requireCashAmount(amount);
        if (amount.compareTo(limits.maxDeposit()) > 0) {
            throw new InvalidRequestException("Maximum deposit per transaction is Rs " + limits.maxDeposit().toPlainString());
        }
        Account account = lockAccount(cardNumber);
        account.credit(amount);
        return record(account, TransactionType.DEPOSIT, amount);
    }

    @Transactional
    public Transaction withdraw(String cardNumber, BigDecimal amount) {
        Amounts.requireCashAmount(amount);
        // Lock first, so two withdrawals at the same moment are checked one after the other.
        Account account = lockAccount(cardNumber);
        account.ensureActive();

        LocalDateTime startOfToday = LocalDate.now(clock).atStartOfDay();
        BigDecimal withdrawnToday = transactions.sumSince(account.getId(), TransactionType.WITHDRAWAL, startOfToday);
        BigDecimal remaining = limits.dailyWithdrawalLimit().subtract(withdrawnToday).max(BigDecimal.ZERO);
        if (amount.compareTo(remaining) > 0) {
            throw new DailyLimitExceededException(remaining);
        }

        account.debit(amount);
        return record(account, TransactionType.WITHDRAWAL, amount);
    }

    @Transactional(readOnly = true)
    public List<Transaction> miniStatement(String cardNumber) {
        Long accountId = accountIdFor(cardNumber);
        return transactions.findByAccountIdOrderByIdDesc(accountId, PageRequest.of(0, MINI_STATEMENT_SIZE));
    }

    private Account lockAccount(String cardNumber) {
        return accounts.findByIdForUpdate(accountIdFor(cardNumber))
                .orElseThrow(() -> new NotFoundException("Account not found"));
    }

    private Long accountIdFor(String cardNumber) {
        return cards.findAccountIdByCardNumber(cardNumber).orElseThrow(() -> new NotFoundException("Card not found"));
    }

    private Transaction record(Account account, TransactionType type, BigDecimal amount) {
        return transactions.save(new Transaction(account, type, amount, UUID.randomUUID().toString(), null,
                LocalDateTime.now(clock)));
    }
}
