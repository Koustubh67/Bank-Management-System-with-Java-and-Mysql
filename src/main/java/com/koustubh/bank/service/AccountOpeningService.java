package com.koustubh.bank.service;

import com.koustubh.bank.domain.Account;
import com.koustubh.bank.domain.AccountStatus;
import com.koustubh.bank.domain.DocumentType;
import com.koustubh.bank.domain.KycDocument;
import com.koustubh.bank.domain.Card;
import com.koustubh.bank.domain.Customer;
import com.koustubh.bank.dto.SignupForm;
import com.koustubh.bank.dto.UploadedFile;
import com.koustubh.bank.exception.InvalidRequestException;
import com.koustubh.bank.exception.NotFoundException;
import com.koustubh.bank.repository.AccountRepository;
import com.koustubh.bank.repository.CardRepository;
import com.koustubh.bank.repository.CustomerRepository;
import com.koustubh.bank.repository.KycDocumentRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class AccountOpeningService {

    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final CardRepository cards;
    private final NumberGenerator numbers;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final KycDocumentRepository documents;
    private final NotificationService notifications;

    /** What the "Track application" page shows. */
    public record ApplicationStatus(String customerName, String accountNumber, String accountType,
                                    AccountStatus status, String declineReason, LocalDateTime appliedAt) {
    }

    public AccountOpeningService(CustomerRepository customers, AccountRepository accounts, CardRepository cards,
                                 NumberGenerator numbers, PasswordEncoder passwordEncoder, Clock clock,
                                 KycDocumentRepository documents, NotificationService notifications) {
        this.documents = documents;
        this.notifications = notifications;
        this.customers = customers;
        this.accounts = accounts;
        this.cards = cards;
        this.numbers = numbers;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /** Saves the customer, account and card together: if any step fails, nothing is saved. */
    @Transactional
    public OpenedAccount open(SignupForm form) {
        // Checked again here (pages 1 and 2 check early) in case someone registered in the meantime
        Map<String, String> taken = new LinkedHashMap<>();
        taken.putAll(duplicatesOnPersonalPage(form.getMobile()));
        taken.putAll(duplicatesOnKycPage(form.getPan(), form.getAadhaar()));
        if (!taken.isEmpty()) {
            throw new InvalidRequestException(String.join(". ", taken.values()));
        }
        LocalDateTime now = LocalDateTime.now(clock);

        Customer customer = form.toCustomer();
        customer.setCreatedAt(now);
        String customerId;
        do {
            customerId = numbers.newCustomerId();
        } while (customers.existsByCustomerId(customerId));
        customer.setCustomerId(customerId);
        customer.setPassword(passwordEncoder.encode(form.getPassword()));
        customers.save(customer);
        saveDocument(customer, DocumentType.PAN, form.getPanDocument(), now);
        saveDocument(customer, DocumentType.AADHAAR, form.getAadhaarDocument(), now);

        String accountNumber;
        do {
            accountNumber = numbers.newAccountNumber();
        } while (accounts.existsByAccountNumber(accountNumber));
        Account account = accounts.save(new Account(accountNumber, customer, form.getAccountType(),
                String.join(", ", form.getServices()), now));

        String cardNumber;
        do {
            cardNumber = numbers.newCardNumber();
        } while (cards.existsByCardNumber(cardNumber));
        String pin = numbers.newPin();
        cards.save(new Card(cardNumber, account, passwordEncoder.encode(pin)));

        notifications.notify(customer, "Application received",
                "Dear " + customer.getFullName() + ", we've received your JavaBank account application (A/c "
                        + accountNumber + ", Customer ID " + customerId + "). We'll SMS and email you as soon as our team "
                        + "has verified your documents, usually within 24 hours.");
        return new OpenedAccount(customerId, accountNumber, cardNumber, pin);
    }

    /** Field name → message for details already used by another customer (shown on signup page 1). */
    @Transactional(readOnly = true)
    public Map<String, String> duplicatesOnPersonalPage(String mobile) {
        Map<String, String> taken = new LinkedHashMap<>();
        if (mobile != null && customers.existsByMobile(mobile)) {
            taken.put("mobile", "This mobile number is already registered with JavaBank. Log in or use another number");
        }
        return taken;
    }

    /** Field name → message for PAN or Aadhaar already registered (shown on signup page 2). */
    @Transactional(readOnly = true)
    public Map<String, String> duplicatesOnKycPage(String pan, String aadhaar) {
        Map<String, String> taken = new LinkedHashMap<>();
        if (pan != null && customers.existsByPan(pan)) {
            taken.put("pan", "A customer with this PAN already exists. Log in or track your application instead");
        }
        if (aadhaar != null && customers.existsByAadhaar(aadhaar)) {
            taken.put("aadhaar", "A customer with this Aadhaar number already exists");
        }
        return taken;
    }

    /** Both the account number and the PAN must match, so nobody can look up someone else's application. */
    @Transactional(readOnly = true)
    public ApplicationStatus status(String accountNumberOrCustomerId, String pan) {
        String cleanPan = pan == null ? "" : pan.replaceAll("\\s", "").toUpperCase();
        String id = accountNumberOrCustomerId == null ? "" : accountNumberOrCustomerId.replaceAll("[\\s-]", "").toUpperCase();
        // Customers may type either the account number (12 digits) or the Customer ID (JB + 8 digits)
        Optional<Account> account = id.startsWith("JB")
                ? accounts.findByCustomerLogin(id)
                : accounts.findIdByAccountNumber(id).flatMap(accounts::findWithCustomerById);
        return account
                .filter(a -> a.getCustomer().getPan().equals(cleanPan))
                .map(a -> new ApplicationStatus(a.getCustomer().getFullName(), a.getAccountNumber(),
                        a.getAccountType().getLabel(), a.getStatus(), a.getDeclineReason(), a.getCreatedAt()))
                .orElseThrow(() -> new NotFoundException("We couldn't find an application with these details. "
                        + "Check the account number (or Customer ID) and PAN shown when you applied"));
    }

    private void saveDocument(Customer customer, DocumentType type, UploadedFile file, LocalDateTime now) {
        if (file != null) {
            documents.save(new KycDocument(customer, type, file.fileName(), file.contentType(), file.content(), now));
        }
    }
}
