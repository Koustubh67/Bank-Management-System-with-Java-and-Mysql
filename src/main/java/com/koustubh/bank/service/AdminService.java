package com.koustubh.bank.service;

import com.koustubh.bank.domain.Account;
import com.koustubh.bank.domain.AccountStatus;
import com.koustubh.bank.domain.Card;
import com.koustubh.bank.domain.InsurancePolicy;
import com.koustubh.bank.domain.InsuranceRequest;
import com.koustubh.bank.domain.Notification;
import com.koustubh.bank.domain.KycDocument;
import com.koustubh.bank.domain.Transaction;
import com.koustubh.bank.domain.UpiHandle;
import com.koustubh.bank.exception.InvalidRequestException;
import com.koustubh.bank.exception.NotFoundException;
import com.koustubh.bank.repository.AccountRepository;
import com.koustubh.bank.repository.CardRepository;
import com.koustubh.bank.repository.CustomerRepository;
import com.koustubh.bank.repository.InsurancePolicyRepository;
import com.koustubh.bank.repository.InsuranceRequestRepository;
import com.koustubh.bank.repository.KycDocumentRepository;
import com.koustubh.bank.repository.DocumentInfo;
import com.koustubh.bank.repository.TransactionRepository;
import com.koustubh.bank.repository.UpiHandleRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Actions available to bank staff in the admin panel. */
@Service
public class AdminService {

    public record Dashboard(long customers, long pending, long active, long frozen, long declined, long transactions,
                            long upiUsers, BigDecimal totalDeposits, long insuranceRequests) {
    }

    public record AccountDetails(Account account, Card card, UpiHandle upi, List<DocumentInfo> documents,
                                 List<InvestmentService.Holding> holdings, List<InsurancePolicy> policies,
                                 List<InsuranceRequest> insuranceRequests, List<Notification> notifications,
                                 List<Transaction> transactions) {
    }

    /** Reasons offered in the decline form. */
    public static final List<String> DECLINE_REASONS = List.of(
            "PAN card image is unclear or unreadable",
            "Aadhaar card image is unclear or unreadable",
            "Name does not match the documents",
            "PAN number does not match the PAN card",
            "Aadhaar number does not match the Aadhaar card",
            "Duplicate application",
            "Incomplete application");

    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final CardRepository cards;
    private final TransactionRepository transactions;
    private final UpiHandleRepository upiHandles;
    private final KycDocumentRepository documents;
    private final InsurancePolicyRepository policies;
    private final InsuranceRequestRepository insuranceRequests;
    private final InvestmentService investmentService;
    private final InsuranceService insurance;
    private final NotificationService notifications;
    private final Clock clock;

    public AdminService(CustomerRepository customers, AccountRepository accounts, CardRepository cards,
                        TransactionRepository transactions, UpiHandleRepository upiHandles,
                        KycDocumentRepository documents, InsurancePolicyRepository policies,
                        InsuranceRequestRepository insuranceRequests, InvestmentService investmentService,
                        InsuranceService insurance, NotificationService notifications, Clock clock) {
        this.documents = documents;
        this.policies = policies;
        this.insuranceRequests = insuranceRequests;
        this.investmentService = investmentService;
        this.insurance = insurance;
        this.notifications = notifications;
        this.clock = clock;
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
                accounts.countByStatus(AccountStatus.DECLINED), transactions.count(), upiHandles.count(),
                accounts.totalBalance(), insurance.openRequestCount());
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
                documents.findInfoByCustomerId(account.getCustomer().getId()),
                investmentService.portfolio(account.getCustomer().getCustomerId()).holdings(),
                policies.findByAccountIdOrderByIdDesc(accountId), insuranceRequests.findByAccountIdOrderByIdDesc(accountId),
                notifications.recentFor(account.getCustomer()),
                transactions.findByAccountIdOrderByIdDesc(accountId, PageRequest.of(0, 50)));
    }

    @Transactional(readOnly = true)
    public List<Transaction> recentTransactions() {
        return transactions.findAllByOrderByIdDesc(PageRequest.of(0, 100));
    }

    @Transactional
    public void approve(Long accountId) {
        approve(accountId, "system");
    }

    @Transactional
    public void approve(Long accountId, String staff) {
        Account account = lock(accountId);
        if (account.getStatus() != AccountStatus.PENDING) {
            throw new InvalidRequestException("Only pending accounts can be approved");
        }
        account.activate();
        account.reviewed(staff, LocalDateTime.now(clock));
        notifications.notify(account.getCustomer(), "Your account is active",
                "Good news! Your JavaBank account " + account.getMaskedNumber() + " is approved. Log in with Customer ID "
                        + account.getCustomer().getCustomerId() + " to use net banking, the ATM and UPI.");
    }

    @Transactional
    public void decline(Long accountId, String reason) {
        decline(accountId, reason, "system");
    }

    @Transactional
    public void decline(Long accountId, String reason, String staff) {
        String clean = reason == null ? "" : reason.trim();
        if (clean.isEmpty()) {
            throw new InvalidRequestException("Please give a reason for declining");
        }
        if (clean.length() > 200) {
            throw new InvalidRequestException("Reason must be at most 200 characters");
        }
        Account account = lock(accountId);
        if (account.getStatus() != AccountStatus.PENDING) {
            throw new InvalidRequestException("Only pending applications can be declined");
        }
        account.decline(clean);
        account.reviewed(staff, LocalDateTime.now(clock));
        notifications.notify(account.getCustomer(), "Update on your JavaBank application",
                "We couldn't approve your application: " + clean + ". You can apply again with the correct details.");
    }

    @Transactional(readOnly = true)
    public KycDocument document(Long documentId) {
        return documents.findById(documentId).orElseThrow(() -> new NotFoundException("Document not found"));
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
    public void unlockLogin(Long accountId) {
        accounts.findWithCustomerById(accountId).orElseThrow(() -> new NotFoundException("Account not found"))
                .getCustomer().unlockLogin();
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
