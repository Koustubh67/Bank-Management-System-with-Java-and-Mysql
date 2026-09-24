package com.koustubh.bank;

import com.koustubh.bank.domain.AccountType;
import com.koustubh.bank.dto.SignupForm;
import com.koustubh.bank.repository.AccountRepository;
import com.koustubh.bank.service.AccountOpeningService;
import com.koustubh.bank.service.AdminService;
import com.koustubh.bank.service.AtmService;
import com.koustubh.bank.service.OpenedAccount;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Test helper that opens (and optionally approves and funds) accounts through the real services. */
@Component
public class TestAccounts {

    private static final AtomicInteger COUNTER = new AtomicInteger(1000);
    /** Net banking password of every test customer. */
    public static final String PASSWORD = "Test@1234";
    private static final ThreadLocal<String> LAST_PAN = new ThreadLocal<>();

    /** PAN of the last form created on this thread. */
    public static String lastPan() {
        return LAST_PAN.get();
    }

    private final AccountOpeningService opening;
    private final AdminService admin;
    private final AtmService atm;
    private final AccountRepository accounts;

    private final TransactionTemplate tx;

    public TestAccounts(AccountOpeningService opening, AdminService admin, AtmService atm, AccountRepository accounts,
                        PlatformTransactionManager txManager) {
        this.tx = new TransactionTemplate(txManager);
        this.opening = opening;
        this.admin = admin;
        this.atm = atm;
        this.accounts = accounts;
    }

    public static SignupForm form() {
        int n = COUNTER.incrementAndGet();
        SignupForm f = new SignupForm();
        f.setFullName("Test Customer " + n);
        f.setFatherName("Test Father");
        f.setDateOfBirth(LocalDate.of(1999, 5, 17));
        f.setGender("Male");
        f.setEmail("test" + n + "@example.com");
        f.setMobile("9" + String.format("%09d", n));
        f.setMaritalStatus("Unmarried");
        f.setAddress("12 MG Road");
        f.setCity("Bhopal");
        f.setState("Madhya Pradesh");
        f.setPincode("462001");
        f.setCountry("India");
        f.setReligion("Hindu");
        f.setCategory("General");
        f.setIncome("None");
        f.setEducation("Graduate");
        f.setOccupation("Student");
        f.setPan("ABCDE" + n + "F");
        LAST_PAN.set(f.getPan());
        f.setAadhaar("12345678" + n);
        f.setSeniorCitizen(false);
        f.setExistingAccount(false);
        f.setAccountType(AccountType.SAVINGS);
        f.setServices(List.of("ATM Card"));
        f.setDeclaration(true);
        f.setPassword(PASSWORD);
        f.setConfirmPassword(PASSWORD);
        return f;
    }

    public OpenedAccount pending() {
        return opening.open(form());
    }

    public OpenedAccount active(int openingBalance) {
        OpenedAccount opened = pending();
        admin.approve(idOf(opened));
        if (openingBalance > 0) {
            atm.deposit(opened.cardNumber(), BigDecimal.valueOf(openingBalance));
        }
        return opened;
    }

    public Long idOf(OpenedAccount opened) {
        return accounts.findIdByAccountNumber(opened.accountNumber()).orElseThrow();
    }

    /** Takes money out of the account so only {@code keep} is left (for testing low-balance cases). */
    public void spendAllBut(OpenedAccount opened, BigDecimal keep) {
        tx.executeWithoutResult(s -> {
            var account = accounts.findByIdForUpdate(idOf(opened)).orElseThrow();
            account.debit(account.getBalance().subtract(keep));
        });
    }

    public void deposit(OpenedAccount opened, BigDecimal amount) {
        tx.executeWithoutResult(s -> accounts.findByIdForUpdate(idOf(opened)).orElseThrow().credit(amount));
    }

    public BigDecimal balanceOf(OpenedAccount opened) {
        return accounts.findById(idOf(opened)).orElseThrow().getBalance();
    }
}
