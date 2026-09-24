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

@Service
public class AccountOpeningService {

    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final CardRepository cards;
    private final NumberGenerator numbers;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final KycDocumentRepository documents;

    /** What the "Track application" page shows. */
    public record ApplicationStatus(String customerName, String accountNumber, String accountType,
                                    AccountStatus status, String declineReason, LocalDateTime appliedAt) {
    }

    public AccountOpeningService(CustomerRepository customers, AccountRepository accounts, CardRepository cards,
                                 NumberGenerator numbers, PasswordEncoder passwordEncoder, Clock clock,
                                 KycDocumentRepository documents) {
        this.documents = documents;
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
        if (customers.existsByPan(form.getPan())) {
            throw new InvalidRequestException("A customer with this PAN is already registered");
        }
        if (customers.existsByAadhaar(form.getAadhaar())) {
            throw new InvalidRequestException("A customer with this Aadhaar number is already registered");
        }
        LocalDateTime now = LocalDateTime.now(clock);

        Customer customer = form.toCustomer();
        customer.setCreatedAt(now);
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

        return new OpenedAccount(accountNumber, cardNumber, pin);
    }

    /** Both the account number and the PAN must match, so nobody can look up someone else's application. */
    @Transactional(readOnly = true)
    public ApplicationStatus status(String accountNumber, String pan) {
        String cleanPan = pan == null ? "" : pan.trim().toUpperCase();
        return accounts.findIdByAccountNumber(accountNumber == null ? "" : accountNumber.trim())
                .flatMap(accounts::findWithCustomerById)
                .filter(a -> a.getCustomer().getPan().equals(cleanPan))
                .map(a -> new ApplicationStatus(a.getCustomer().getFullName(), a.getAccountNumber(),
                        a.getAccountType().getLabel(), a.getStatus(), a.getDeclineReason(), a.getCreatedAt()))
                .orElseThrow(() -> new NotFoundException("No application found with this account number and PAN"));
    }

    private void saveDocument(Customer customer, DocumentType type, UploadedFile file, LocalDateTime now) {
        if (file != null) {
            documents.save(new KycDocument(customer, type, file.fileName(), file.contentType(), file.content(), now));
        }
    }
}
