package com.koustubh.bank.service;

import com.koustubh.bank.domain.Account;
import com.koustubh.bank.domain.Card;
import com.koustubh.bank.domain.Customer;
import com.koustubh.bank.domain.Transaction;
import com.koustubh.bank.domain.TransactionType;
import com.koustubh.bank.domain.UpiHandle;
import com.koustubh.bank.exception.InvalidRequestException;
import com.koustubh.bank.exception.NotFoundException;
import com.koustubh.bank.repository.AccountRepository;
import com.koustubh.bank.repository.CardRepository;
import com.koustubh.bank.repository.CustomerRepository;
import com.koustubh.bank.repository.TransactionRepository;
import com.koustubh.bank.repository.UpiHandleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Everything the logged-in customer sees on their dashboard, passbook and profile. */
@Service
public class CustomerService {

    public record Overview(Customer customer, Account account, Card card, UpiHandle upi, List<Transaction> recent) {
    }

    /** Filter for the passbook: {@code credit} null = all, true = money in, false = money out. */
    public record PassbookFilter(LocalDate from, LocalDate to, Boolean credit) {
    }

    private static final List<TransactionType> CREDIT_TYPES =
            Arrays.stream(TransactionType.values()).filter(TransactionType::isCredit).toList();
    public static final int PAGE_SIZE = 15;

    private final AccountRepository accounts;
    private final CardRepository cards;
    private final UpiHandleRepository upiHandles;
    private final TransactionRepository transactions;
    private final CustomerRepository customers;

    public CustomerService(AccountRepository accounts, CardRepository cards, UpiHandleRepository upiHandles,
                           TransactionRepository transactions, CustomerRepository customers) {
        this.customers = customers;
        this.accounts = accounts;
        this.cards = cards;
        this.upiHandles = upiHandles;
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public Overview overview(String customerId) {
        Account account = account(customerId);
        Card card = cards.findByAccountId(account.getId()).orElseThrow(() -> new NotFoundException("Card not found"));
        return new Overview(account.getCustomer(), account, card,
                upiHandles.findByAccountId(account.getId()).orElse(null),
                transactions.findByAccountIdOrderByIdDesc(account.getId(), PageRequest.of(0, 5)));
    }

    @Transactional(readOnly = true)
    public Page<Transaction> passbook(String customerId, PassbookFilter filter, int page, int size) {
        return transactions.passbook(account(customerId).getId(), filter.from().atStartOfDay(),
                filter.to().plusDays(1).atStartOfDay(), filter.credit(), CREDIT_TYPES,
                PageRequest.of(Math.max(page, 0), size));
    }

    /** The debit card linked to this customer's account, used when they open the ATM from the dashboard. */
    @Transactional(readOnly = true)
    public String cardNumber(String customerId) {
        return cards.findByAccountId(account(customerId).getId()).map(Card::getCardNumber)
                .orElseThrow(() -> new NotFoundException("Card not found"));
    }

    /** Mobile and email for alerts. The mobile must be a valid Indian number not used by another customer. */
    @Transactional
    public void updateContact(String customerId, String mobile, String email) {
        Customer c = account(customerId).getCustomer();
        String cleanMobile = mobile == null ? "" : mobile.replaceAll("[\\s-]", "").replaceFirst("^(\\+91|0)", "");
        String cleanEmail = email == null ? "" : email.trim();
        List<String> errors = new ArrayList<>();
        if (!cleanMobile.matches("[6-9]\\d{9}")) {
            errors.add("Enter a valid 10-digit mobile number");
        } else if (!cleanMobile.equals(c.getMobile()) && customers.existsByMobile(cleanMobile)) {
            errors.add("This mobile number is already registered to another customer");
        }
        if (!cleanEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") || cleanEmail.length() > 100) {
            errors.add("Enter a valid email address");
        }
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(String.join(". ", errors));
        }
        c.setMobile(cleanMobile);
        c.setEmail(cleanEmail);
    }

    private Account account(String customerId) {
        return accounts.findByCustomerLogin(customerId).orElseThrow(() -> new NotFoundException("Account not found"));
    }
}
