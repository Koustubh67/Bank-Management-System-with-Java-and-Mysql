package com.koustubh.bank.service;

import com.koustubh.bank.domain.Customer;
import com.koustubh.bank.exception.InvalidRequestException;
import com.koustubh.bank.exception.NotFoundException;
import com.koustubh.bank.repository.CardRepository;
import com.koustubh.bank.repository.CustomerRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Locale;

/**
 * Net banking login: Customer ID + password. Everything a customer does (dashboard, passbook, ATM, UPI)
 * starts from this login.
 */
@Service
public class CustomerLoginService {

    public static final int MAX_ATTEMPTS = 5;
    /** 8-64 characters with at least one letter and one digit. */
    public static final String PASSWORD_RULE = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$";
    public static final String PASSWORD_HINT = "must be 8+ characters with at least one letter and one number";

    private final CustomerRepository customers;
    private final CardRepository cards;
    private final CardSecurityService cardSecurity;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;

    public CustomerLoginService(CustomerRepository customers, CardRepository cards, CardSecurityService cardSecurity,
                                PasswordEncoder passwordEncoder, PlatformTransactionManager transactionManager) {
        this.customers = customers;
        this.cards = cards;
        this.cardSecurity = cardSecurity;
        this.passwordEncoder = passwordEncoder;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** Returns the normalised Customer ID. Wrong passwords are counted even though the login fails. */
    @Transactional(noRollbackFor = AuthenticationException.class)
    public String verifyLogin(String customerId, String password) {
        Customer customer = customers.findByCustomerId(normalise(customerId))
                .orElseThrow(() -> new BadCredentialsException("Invalid Customer ID or password"));
        if (customer.isLoginLocked()) {
            throw new LockedException("Login locked after " + MAX_ATTEMPTS + " wrong passwords. "
                    + "Reset your password with your debit card");
        }
        if (customer.getPasswordHash() == null) {
            throw new BadCredentialsException("You haven't set a password yet. Use \"Forgot or set password\"");
        }
        if (!passwordEncoder.matches(password, customer.getPasswordHash())) {
            customer.registerFailedLogin(MAX_ATTEMPTS);
            if (customer.isLoginLocked()) {
                throw new LockedException("Wrong password entered " + MAX_ATTEMPTS + " times. Your login is now locked");
            }
            int left = MAX_ATTEMPTS - customer.getFailedLogins();
            throw new BadCredentialsException("Invalid Customer ID or password. " + left
                    + (left == 1 ? " attempt" : " attempts") + " left");
        }
        customer.loginSucceeded();
        return customer.getCustomerId();
    }

    /**
     * Sets a new password (first time, forgotten or locked) using the debit card and ATM PIN, like real
     * net banking registration. Returns the Customer ID so the customer can log in.
     */
    public String resetWithCard(String cardNumber, String atmPin, String newPassword) {
        requireStrong(newPassword);
        cardSecurity.verifyCardPin(cardNumber, atmPin);
        return transactionTemplate.execute(status -> {
            Customer customer = cards.findByCardNumber(cardNumber)
                    .orElseThrow(() -> new NotFoundException("Card not found"))
                    .getAccount().getCustomer();
            customer.setPassword(passwordEncoder.encode(newPassword));
            return customer.getCustomerId();
        });
    }

    @Transactional
    public void changePassword(String customerId, String currentPassword, String newPassword) {
        Customer customer = customers.findByCustomerId(customerId)
                .orElseThrow(() -> new NotFoundException("Customer not found"));
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, customer.getPasswordHash())) {
            throw new InvalidRequestException("Current password is incorrect");
        }
        requireStrong(newPassword);
        if (passwordEncoder.matches(newPassword, customer.getPasswordHash())) {
            throw new InvalidRequestException("New password must be different from the current one");
        }
        customer.setPassword(passwordEncoder.encode(newPassword));
    }

    static void requireStrong(String password) {
        if (password == null || !password.matches(PASSWORD_RULE)) {
            throw new InvalidRequestException("Password " + PASSWORD_HINT);
        }
    }

    static String normalise(String customerId) {
        return customerId == null ? "" : customerId.trim().toUpperCase(Locale.ROOT);
    }
}
