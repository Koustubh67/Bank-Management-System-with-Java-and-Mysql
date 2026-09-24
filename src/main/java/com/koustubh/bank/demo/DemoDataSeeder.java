package com.koustubh.bank.demo;

import com.koustubh.bank.config.BankProperties;
import com.koustubh.bank.domain.Account;
import com.koustubh.bank.domain.AccountType;
import com.koustubh.bank.domain.Card;
import com.koustubh.bank.domain.Customer;
import com.koustubh.bank.domain.UpiHandle;
import com.koustubh.bank.exception.WrongUpiPinException;
import com.koustubh.bank.repository.AccountRepository;
import com.koustubh.bank.repository.CardRepository;
import com.koustubh.bank.repository.CustomerRepository;
import com.koustubh.bank.repository.UpiHandleRepository;
import com.koustubh.bank.service.AdminService;
import com.koustubh.bank.service.AtmService;
import com.koustubh.bank.service.CardSecurityService;
import com.koustubh.bank.service.TransferService;
import com.koustubh.bank.service.UpiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Loads a fixed set of demo customers so the app can be tried straight away. Card numbers, PINs and UPI PINs are
 * listed in the README. Runs only once: if the first demo card already exists, nothing is changed.
 * Money is moved through the real services, so balances and the ledger are always consistent.
 */
@Component
@Order(2)
public class DemoDataSeeder implements ApplicationRunner {

    /** One demo customer. {@code upiPin} is null when UPI is not activated. */
    public record DemoCustomer(String name, String fatherName, String gender, LocalDate dob, String city,
                               String state, String occupation, String pan, String aadhaar, AccountType type,
                               String accountNumber, String cardNumber, String pin, String upiPin, String vpa) {

        /** Net banking login, e.g. JB10000001 for account 100000000001. */
        public String customerId() {
            return "JB1000000" + accountNumber.substring(11);
        }
    }

    /** Net banking password of every demo customer. */
    public static final String DEMO_PASSWORD = "Demo@1234";

    public static final DemoCustomer RAHUL = new DemoCustomer("Rahul Sharma", "Suresh Sharma", "Male",
            LocalDate.of(1998, 4, 12), "Bhopal", "Madhya Pradesh", "Salaried", "ABCPS1234A", "999900000001",
            AccountType.SAVINGS, "100000000001", "5040930000000017", "1234", "123456", "rahul.0001@javabank");
    public static final DemoCustomer PRIYA = new DemoCustomer("Priya Verma", "Rakesh Verma", "Female",
            LocalDate.of(1996, 11, 3), "Indore", "Madhya Pradesh", "Business", "ABCPV2345B", "999900000002",
            AccountType.CURRENT, "100000000002", "5040930000000025", "2345", "234567", "priya.0002@javabank");
    public static final DemoCustomer AMIT = new DemoCustomer("Amit Patel", "Mahesh Patel", "Male",
            LocalDate.of(2001, 1, 22), "Ahmedabad", "Gujarat", "Student", "ABCPP3456C", "999900000003",
            AccountType.SAVINGS, "100000000003", "5040930000000033", "3456", null, null);
    public static final DemoCustomer SNEHA = new DemoCustomer("Sneha Iyer", "Venkat Iyer", "Female",
            LocalDate.of(1990, 7, 9), "Chennai", "Tamil Nadu", "Self Employed", "ABCPI4567D", "999900000004",
            AccountType.SAVINGS, "100000000004", "5040930000000041", "4567", null, null);
    public static final DemoCustomer VIKRAM = new DemoCustomer("Vikram Singh", "Harpal Singh", "Male",
            LocalDate.of(1985, 3, 30), "Jaipur", "Rajasthan", "Government Employee", "ABCPS5678E", "999900000005",
            AccountType.SAVINGS, "100000000005", "5040930000000058", "5678", null, null);
    public static final DemoCustomer ANJALI = new DemoCustomer("Anjali Gupta", "Ramesh Gupta", "Female",
            LocalDate.of(1999, 9, 14), "Lucknow", "Uttar Pradesh", "Salaried", "ABCPG6789F", "999900000006",
            AccountType.SAVINGS, "100000000006", "5040930000000066", "6789", "345678", "anjali.0006@javabank");

    public static final List<DemoCustomer> ALL = List.of(RAHUL, PRIYA, AMIT, SNEHA, VIKRAM, ANJALI);

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final BankProperties properties;
    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final CardRepository cards;
    private final UpiHandleRepository upiHandles;
    private final AtmService atm;
    private final TransferService transfers;
    private final UpiService upi;
    private final AdminService admin;
    private final CardSecurityService cardSecurity;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final TransactionTemplate tx;

    public DemoDataSeeder(BankProperties properties, CustomerRepository customers, AccountRepository accounts,
                          CardRepository cards, UpiHandleRepository upiHandles, AtmService atm,
                          TransferService transfers, UpiService upi, AdminService admin,
                          CardSecurityService cardSecurity, PasswordEncoder passwordEncoder, Clock clock,
                          PlatformTransactionManager transactionManager) {
        this.admin = admin;
        this.cardSecurity = cardSecurity;
        this.properties = properties;
        this.customers = customers;
        this.accounts = accounts;
        this.cards = cards;
        this.upiHandles = upiHandles;
        this.atm = atm;
        this.transfers = transfers;
        this.upi = upi;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.demo() == null || !properties.demo().enabled()) {
            return;
        }
        if (cards.existsByCardNumber(RAHUL.cardNumber())) {
            // Loaded by an older version of the app: just add the net banking logins if they're missing.
            tx.executeWithoutResult(status -> ALL.forEach(this::ensureLogin));
            return;
        }

        // 1. Customers, accounts, cards and UPI IDs with fixed numbers. Everyone except Amit gets approved.
        tx.executeWithoutResult(status -> ALL.forEach(this::create));

        // 2. Realistic history, using the same services as the ATM and UPI screens
        atm.deposit(RAHUL.cardNumber(), rs(50_000));
        atm.deposit(PRIYA.cardNumber(), rs(120_000));
        atm.deposit(SNEHA.cardNumber(), rs(20_000));
        atm.deposit(VIKRAM.cardNumber(), rs(15_000));
        atm.deposit(ANJALI.cardNumber(), rs(8_000));
        atm.withdraw(RAHUL.cardNumber(), rs(5_000));
        atm.withdraw(PRIYA.cardNumber(), rs(2_000));
        transfers.transfer(RAHUL.cardNumber(), PRIYA.accountNumber(), rs(2_500));
        upi.pay(RAHUL.vpa(), PRIYA.vpa(), rs(1_200), "Dinner", RAHUL.upiPin());
        upi.pay(PRIYA.vpa(), RAHUL.vpa(), rs(750), "Movie tickets", PRIYA.upiPin());
        upi.pay(ANJALI.vpa(), RAHUL.vpa(), rs(300), "Chai", ANJALI.upiPin());

        // 3. Accounts in the states staff need to handle, reached the same way a real customer would
        admin.freeze(accounts.findIdByAccountNumber(SNEHA.accountNumber()).orElseThrow());
        for (int i = 0; i < properties.atm().maxPinAttempts(); i++) {
            ignoreLoginFailure(() -> cardSecurity.verifyLogin(VIKRAM.cardNumber(), "0000"));
        }
        for (int i = 0; i < properties.upi().maxPinAttempts(); i++) {
            try {
                upi.balance(ANJALI.vpa(), "000000");
            } catch (WrongUpiPinException expected) {
                // a wrong UPI PIN is exactly what we want here
            }
        }
        log.info("Loaded {} demo customers (see README for card numbers and PINs)", ALL.size());
    }

    private void create(DemoCustomer d) {
        LocalDateTime now = LocalDateTime.now(clock);
        Customer c = new Customer();
        c.setFullName(d.name());
        c.setFatherName(d.fatherName());
        c.setDateOfBirth(d.dob());
        c.setGender(d.gender());
        c.setEmail(d.name().toLowerCase().replace(' ', '.') + "@example.com");
        c.setMaritalStatus("Unmarried");
        c.setAddress("12 MG Road");
        c.setCity(d.city());
        c.setState(d.state());
        c.setPincode("462001");
        c.setCountry("India");
        c.setReligion("Other");
        c.setCategory("General");
        c.setIncome("2,50,000 - 5,00,000");
        c.setEducation("Graduate");
        c.setOccupation(d.occupation());
        c.setPan(d.pan());
        c.setAadhaar(d.aadhaar());
        c.setSeniorCitizen(false);
        c.setExistingAccount(false);
        c.setCreatedAt(now);
        c.setCustomerId(d.customerId());
        c.setPassword(passwordEncoder.encode(DEMO_PASSWORD));
        customers.save(c);

        Account account = new Account(d.accountNumber(), c, d.type(), "ATM Card, Internet Banking, Mobile Banking", now);
        if (d != AMIT) {
            account.activate();
        }
        accounts.save(account);
        cards.save(new Card(d.cardNumber(), account, passwordEncoder.encode(d.pin())));
        if (d.upiPin() != null) {
            upiHandles.save(new UpiHandle(d.vpa(), account, passwordEncoder.encode(d.upiPin()), now));
        }
    }

    private void ensureLogin(DemoCustomer d) {
        cards.findByCardNumber(d.cardNumber()).map(card -> card.getAccount().getCustomer()).ifPresent(c -> {
            if (c.getPasswordHash() == null) {
                if (!c.getCustomerId().equals(d.customerId()) && !customers.existsByCustomerId(d.customerId())) {
                    c.setCustomerId(d.customerId());
                }
                c.setPassword(passwordEncoder.encode(DEMO_PASSWORD));
            }
        });
    }

    private static void ignoreLoginFailure(Runnable wrongPinLogin) {
        try {
            wrongPinLogin.run();
        } catch (AuthenticationException expected) {
            // a wrong PIN is exactly what we want here
        }
    }

    private static BigDecimal rs(long amount) {
        return BigDecimal.valueOf(amount);
    }
}
