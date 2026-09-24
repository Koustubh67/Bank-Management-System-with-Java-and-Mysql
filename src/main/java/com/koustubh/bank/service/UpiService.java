package com.koustubh.bank.service;

import com.koustubh.bank.config.BankProperties;
import com.koustubh.bank.domain.Account;
import com.koustubh.bank.domain.Card;
import com.koustubh.bank.domain.Transaction;
import com.koustubh.bank.domain.TransactionType;
import com.koustubh.bank.domain.UpiHandle;
import com.koustubh.bank.exception.InvalidRequestException;
import com.koustubh.bank.exception.NotFoundException;
import com.koustubh.bank.exception.WrongUpiPinException;
import com.koustubh.bank.repository.AccountRepository;
import com.koustubh.bank.repository.CardRepository;
import com.koustubh.bank.repository.TransactionRepository;
import com.koustubh.bank.repository.UpiHandleRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * JavaPay UPI: pay other JavaBank customers by UPI ID. Like real UPI apps, the customer activates UPI with
 * their debit card and ATM PIN, sets a separate 6-digit UPI PIN, and must enter it for every payment and
 * balance check.
 */
@Service
public class UpiService {

    public static final String HANDLE = "@javabank";
    private static final String VPA_PATTERN = "[a-z0-9._-]{2,40}@[a-z]{2,20}";
    private static final int HISTORY_SIZE = 20;

    public record Payee(String vpa, String name) {
    }

    public record Profile(String vpa, String name, String maskedAccountNumber, String accountType, String bankName) {
    }

    private final UpiHandleRepository handles;
    private final AccountRepository accounts;
    private final CardRepository cards;
    private final TransactionRepository transactions;
    private final CardSecurityService cardSecurity;
    private final TransferService transfers;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final BankProperties.Upi limits;
    private final TransactionTemplate transactionTemplate;

    public UpiService(UpiHandleRepository handles, AccountRepository accounts, CardRepository cards, TransactionRepository transactions,
                      CardSecurityService cardSecurity, TransferService transfers, PasswordEncoder passwordEncoder,
                      Clock clock, BankProperties properties, PlatformTransactionManager transactionManager) {
        this.handles = handles;
        this.accounts = accounts;
        this.cards = cards;
        this.transactions = transactions;
        this.cardSecurity = cardSecurity;
        this.transfers = transfers;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.limits = properties.upi();
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Activates UPI (or resets a forgotten UPI PIN) using the debit card and ATM PIN. Returns the UPI ID.
     * The card check runs in its own transaction first, so wrong ATM PINs are counted even though this fails.
     */
    public String register(String cardNumber, String atmPin, String newUpiPin) {
        if (newUpiPin == null || !newUpiPin.matches("\\d{6}")) {
            throw new InvalidRequestException("UPI PIN must be exactly 6 digits");
        }
        cardSecurity.verifyLogin(cardNumber, atmPin);
        return transactionTemplate.execute(status -> {
            Card card = cards.findByCardNumber(cardNumber).orElseThrow(() -> new NotFoundException("Card not found"));
            Account account = card.getAccount();
            String pinHash = passwordEncoder.encode(newUpiPin);
            return handles.findByAccountId(account.getId())
                    .map(existing -> {
                        existing.resetPin(pinHash);
                        return existing.getVpa();
                    })
                    .orElseGet(() -> handles.save(new UpiHandle(newVpa(account), account, pinHash,
                            LocalDateTime.now(clock))).getVpa());
        });
    }

    /** The UPI ID of a logged-in net banking customer, if they have activated UPI. */
    @Transactional(readOnly = true)
    public Optional<String> vpaForCustomer(String customerId) {
        return accounts.findByCustomerLogin(customerId)
                .flatMap(a -> handles.findByAccountId(a.getId()))
                .map(UpiHandle::getVpa);
    }

    @Transactional(readOnly = true)
    public Profile profile(String vpa) {
        UpiHandle handle = handle(vpa);
        Account account = handle.getAccount();
        return new Profile(handle.getVpa(), account.getCustomer().getFullName(), account.getMaskedNumber(),
                account.getAccountType().getLabel(), "JavaBank");
    }

    /** Checks a UPI ID before paying, and returns the registered name so the customer can confirm it. */
    @Transactional(readOnly = true)
    public Payee lookup(String payerVpa, String payeeVpa) {
        String payee = normalise(payeeVpa);
        if (!payee.matches(VPA_PATTERN)) {
            throw new InvalidRequestException("Enter a valid UPI ID, for example name.1234" + HANDLE);
        }
        if (!payee.endsWith(HANDLE)) {
            throw new InvalidRequestException("Only " + HANDLE + " UPI IDs can be paid in this demo");
        }
        if (payee.equals(normalise(payerVpa))) {
            throw new InvalidRequestException("You cannot pay your own UPI ID");
        }
        UpiHandle handle = handles.findByVpa(payee)
                .orElseThrow(() -> new NotFoundException("No account is linked to " + payee));
        return new Payee(handle.getVpa(), handle.getAccount().getCustomer().getFullName().toUpperCase(Locale.ROOT));
    }

    /** Pays another UPI ID. Wrong-PIN attempts are kept even though the payment is refused. */
    @Transactional(noRollbackFor = WrongUpiPinException.class)
    public Transaction pay(String payerVpa, String payeeVpa, BigDecimal amount, String note, String pin) {
        Amounts.requirePositive(amount);
        if (amount.compareTo(limits.perTransactionLimit()) > 0) {
            throw new InvalidRequestException("UPI limit is Rs " + limits.perTransactionLimit().toPlainString()
                    + " per transaction");
        }
        Payee payee = lookup(payerVpa, payeeVpa);
        UpiHandle payer = checkPin(payerVpa, pin);

        Long payerAccountId = payer.getAccount().getId();
        LocalDateTime startOfToday = LocalDate.now(clock).atStartOfDay();
        BigDecimal paidToday = transactions.sumSince(payerAccountId, TransactionType.UPI_OUT, startOfToday);
        if (paidToday.add(amount).compareTo(limits.dailyLimit()) > 0) {
            BigDecimal left = limits.dailyLimit().subtract(paidToday).max(BigDecimal.ZERO);
            throw new InvalidRequestException("Daily UPI limit reached. You can pay up to Rs " + left.toPlainString()
                    + " more today");
        }

        UpiHandle payeeHandle = handle(payee.vpa());
        String narration = cleanNote(note);
        return transfers.moveMoney(payerAccountId, payeeHandle.getAccount().getId(), amount,
                TransactionType.UPI_OUT, TransactionType.UPI_IN,
                "UPI/" + payee.vpa() + narration, "UPI/" + payer.getVpa() + narration);
    }

    /** Checks the UPI PIN on its own (e.g. to approve a payment at checkout). Wrong PINs still count. */
    @Transactional(noRollbackFor = WrongUpiPinException.class)
    public void verifyPin(String vpa, String pin) {
        checkPin(vpa, pin);
    }

    @Transactional(noRollbackFor = WrongUpiPinException.class)
    public BigDecimal balance(String vpa, String pin) {
        return checkPin(vpa, pin).getAccount().getBalance();
    }

    @Transactional(readOnly = true)
    public List<Transaction> history(String vpa) {
        return transactions.findByAccountIdOrderByIdDesc(handle(vpa).getAccount().getId(),
                PageRequest.of(0, HISTORY_SIZE));
    }

    /** The standard UPI payment link that any UPI app understands when scanned as a QR code. */
    @Transactional(readOnly = true)
    public String paymentUri(String vpa) {
        Profile p = profile(vpa);
        return "upi://pay?pa=" + p.vpa() + "&pn=" + URLEncoder.encode(p.name(),
                StandardCharsets.UTF_8).replace("+", "%20") + "&cu=INR";
    }

    private UpiHandle checkPin(String vpa, String pin) {
        UpiHandle handle = handle(vpa);
        if (handle.isBlocked()) {
            throw new WrongUpiPinException("UPI is locked after too many wrong PINs. Reset your UPI PIN with your debit card");
        }
        if (pin == null || !passwordEncoder.matches(pin, handle.getPinHash())) {
            handle.registerFailedAttempt(limits.maxPinAttempts());
            throw new WrongUpiPinException(wrongPinMessage(handle));
        }
        handle.resetFailedAttempts();
        return handle;
    }

    private String wrongPinMessage(UpiHandle handle) {
        if (handle.isBlocked()) {
            return "Wrong UPI PIN entered " + limits.maxPinAttempts() + " times. UPI is now locked";
        }
        int left = limits.maxPinAttempts() - handle.getFailedAttempts();
        return "Incorrect UPI PIN. " + left + (left == 1 ? " attempt" : " attempts") + " left";
    }

    private UpiHandle handle(String vpa) {
        return handles.findByVpa(normalise(vpa)).orElseThrow(() -> new NotFoundException("UPI ID not found"));
    }

    /** e.g. "Priya Sharma" with account ...3311 becomes priya.3311@javabank */
    private String newVpa(Account account) {
        String first = account.getCustomer().getFullName().trim().split("\\s+")[0]
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        if (first.length() < 2) {
            first = "user";
        }
        first = first.substring(0, Math.min(first.length(), 20));
        String number = account.getAccountNumber();
        String base = first + "." + number.substring(number.length() - 4);
        String vpa = base + HANDLE;
        for (int i = 1; handles.existsByVpa(vpa); i++) {
            vpa = base + i + HANDLE;
        }
        return vpa;
    }

    private static String cleanNote(String note) {
        if (note == null || note.isBlank()) {
            return "";
        }
        String clean = note.trim().replaceAll("[\\r\\n/]", " ");
        return "/" + clean.substring(0, Math.min(clean.length(), 40));
    }

    static String normalise(String vpa) {
        return vpa == null ? "" : vpa.trim().toLowerCase(Locale.ROOT);
    }
}
