package com.koustubh.bank.service;

import com.koustubh.bank.domain.Account;
import com.koustubh.bank.domain.AccountStatus;
import com.koustubh.bank.domain.Card;
import com.koustubh.bank.domain.Transaction;
import com.koustubh.bank.domain.UpiHandle;
import com.koustubh.bank.exception.InvalidRequestException;
import com.koustubh.bank.exception.NotFoundException;
import com.koustubh.bank.repository.AccountRepository;
import com.koustubh.bank.repository.CardRepository;
import com.koustubh.bank.repository.CustomerRepository;
import com.koustubh.bank.repository.TransactionRepository;
import com.koustubh.bank.repository.UpiHandleRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Actions available to bank staff in the admin panel. */
@Service
public class AdminService {

    public record Dashboard(long customers, long pending, long active, long frozen, long transactions,
                            BigDecimal totalDeposits) {
    }

    public record AccountDetails(Account account, Card card, UpiHandle upi, List<Transaction> transactions) {
    }

    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final CardRepository cards;
    private final TransactionRepository transactions;
    private final UpiHandleRepository upiHandles;

    public AdminService(CustomerRepository customers, AccountRepository accounts, CardRepository cards,
                        TransactionRepository transactions, UpiHandleRepository upiHandles) {
        this.customers = customers;
        this.accounts = accounts;
        this.cards = cards;
        this.transactions = transactions;
        this.upiHandles = upiHandles;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        return new Dashboard(customers.count(), accounts.countByStatus(AccountStatus.PENDING),
                accounts.countByStatus(AccountStatus.ACTIVE), accounts.countByStatus(AccountStatus.FROZEN),
                transactions.count(), accounts.totalBalance());
    }

    @Transactional(readOnly = true)
    public List<Account> accounts(AccountStatus status) {
        return status == null
                ? accounts.findAllByOrderByCreatedAtDesc()
                : accounts.findByStatusOrderByCreatedAtDesc(status);
    }

    @Transactional(readOnly = true)
    public AccountDetails accountDetails(Long accountId) {
        Account account = accounts.findWithCustomerById(accountId)
                .orElseThrow(() -> new NotFoundException("Account not found"));
        Card card = cards.findByAccountId(accountId).orElseThrow(() -> new NotFoundException("Card not found"));
        return new AccountDetails(account, card, upiHandles.findByAccountId(accountId).orElse(null),
                transactions.findByAccountIdOrderByIdDesc(accountId, PageRequest.of(0, 50)));
    }

    @Transactional(readOnly = true)
    public List<Transaction> recentTransactions() {
        return transactions.findAllByOrderByIdDesc(PageRequest.of(0, 100));
    }

    @Transactional
    public void approve(Long accountId) {
        Account account = lock(accountId);
        if (account.getStatus() != AccountStatus.PENDING) {
            throw new InvalidRequestException("Only pending accounts can be approved");
        }
        account.activate();
    }

    @Transactional
    public void freeze(Long accountId) {
        Account account = lock(accountId);
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidRequestException("Only active accounts can be frozen");
        }
        account.freeze();
    }

    @Transactional
    public void unfreeze(Long accountId) {
        Account account = lock(accountId);
        if (account.getStatus() != AccountStatus.FROZEN) {
            throw new InvalidRequestException("Only frozen accounts can be unfrozen");
        }
        account.activate();
    }

    @Transactional
    public void unblockCard(Long accountId) {
        Card card = cards.findByAccountId(accountId).orElseThrow(() -> new NotFoundException("Card not found"));
        card.unblock();
    }

    @Transactional
    public void unblockUpi(Long accountId) {
        upiHandles.findByAccountId(accountId).orElseThrow(() -> new NotFoundException("UPI is not activated"))
                .unblock();
    }

    private Account lock(Long accountId) {
        return accounts.findByIdForUpdate(accountId).orElseThrow(() -> new NotFoundException("Account not found"));
    }
}
