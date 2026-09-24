package com.koustubh.bank.service;

import com.koustubh.bank.config.BankProperties;
import com.koustubh.bank.domain.AccountStatus;
import com.koustubh.bank.domain.Card;
import com.koustubh.bank.exception.InvalidRequestException;
import com.koustubh.bank.exception.NotFoundException;
import com.koustubh.bank.repository.CardRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CardSecurityService {

    private final CardRepository cards;
    private final PasswordEncoder passwordEncoder;
    private final int maxPinAttempts;

    public CardSecurityService(CardRepository cards, PasswordEncoder passwordEncoder, BankProperties properties) {
        this.cards = cards;
        this.passwordEncoder = passwordEncoder;
        this.maxPinAttempts = properties.atm().maxPinAttempts();
    }

    /**
     * Checks card number and PIN at ATM login. A wrong PIN is counted and the card is blocked after
     * {@code max-pin-attempts} tries. noRollbackFor keeps the failed-attempt count even though we throw.
     */
    @Transactional(noRollbackFor = AuthenticationException.class)
    public void verifyLogin(String cardNumber, String pin) {
        Card card = checkPin(cardNumber, pin);
        AccountStatus status = card.getAccount().getStatus();
        if (status == AccountStatus.PENDING) {
            throw new DisabledException("Your account is awaiting approval by the bank");
        }
        if (status == AccountStatus.DECLINED) {
            throw new DisabledException("Your account application was declined. Check its status on the Track application page");
        }
        if (status == AccountStatus.FROZEN) {
            throw new DisabledException("Your account is frozen. Please contact your branch");
        }
    }

    /** Checks card + PIN only (used to reset the net banking password). Wrong PINs still count towards the lock. */
    @Transactional(noRollbackFor = AuthenticationException.class)
    public void verifyCardPin(String cardNumber, String pin) {
        checkPin(cardNumber, pin);
    }

    private Card checkPin(String cardNumber, String pin) {
        Card card = cards.findByCardNumber(cardNumber)
                .orElseThrow(() -> new BadCredentialsException("Invalid card number or PIN"));
        if (card.isBlocked()) {
            throw new LockedException("This card is blocked. Please contact your branch");
        }
        if (!passwordEncoder.matches(pin, card.getPinHash())) {
            card.registerFailedAttempt(maxPinAttempts);
            if (card.isBlocked()) {
                throw new LockedException("Wrong PIN entered " + maxPinAttempts + " times. Your card is now blocked");
            }
            int left = maxPinAttempts - card.getFailedAttempts();
            throw new BadCredentialsException("Wrong PIN. " + left + (left == 1 ? " attempt" : " attempts") + " left");
        }
        card.resetFailedAttempts();
        return card;
    }

    @Transactional
    public void changePin(String cardNumber, String currentPin, String newPin) {
        Card card = cards.findByCardNumber(cardNumber)
                .orElseThrow(() -> new NotFoundException("Card not found"));
        if (!passwordEncoder.matches(currentPin, card.getPinHash())) {
            throw new InvalidRequestException("Current PIN is incorrect");
        }
        if (newPin == null || !newPin.matches("\\d{4}")) {
            throw new InvalidRequestException("New PIN must be exactly 4 digits");
        }
        if (newPin.equals(currentPin)) {
            throw new InvalidRequestException("New PIN must be different from the current PIN");
        }
        card.changePin(passwordEncoder.encode(newPin));
    }
}
