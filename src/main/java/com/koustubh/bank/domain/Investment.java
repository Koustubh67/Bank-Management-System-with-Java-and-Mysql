package com.koustubh.bank.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A fixed deposit or a SIP mandate. For a SIP, {@code amount} is the monthly instalment. */
@Entity
public class Investment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    private InvestmentType type;

    private String reference;

    @Enumerated(EnumType.STRING)
    private Fund fund;

    private BigDecimal amount;
    private BigDecimal ratePercent;
    private Integer tenureMonths;
    private LocalDate startDate;
    private LocalDate maturityDate;
    private BigDecimal maturityAmount;
    private LocalDate nextDebitDate;
    private int instalmentsPaid;
    private LocalDateTime createdAt;

    protected Investment() {
    }

    public static Investment fixedDeposit(Account account, String reference, BigDecimal amount, FdTenure tenure,
                                          BigDecimal maturityAmount, LocalDate start, LocalDateTime now) {
        Investment i = new Investment(account, InvestmentType.FIXED_DEPOSIT, reference, amount, tenure.getRate(), start, now);
        i.tenureMonths = tenure.getMonths();
        i.maturityDate = start.plusMonths(tenure.getMonths());
        i.maturityAmount = maturityAmount;
        return i;
    }

    public static Investment sip(Account account, String reference, Fund fund, BigDecimal monthly, LocalDate start,
                                 LocalDateTime now) {
        Investment i = new Investment(account, InvestmentType.SIP, reference, monthly, fund.getExpectedReturn(), start, now);
        i.fund = fund;
        i.instalmentsPaid = 1;
        i.nextDebitDate = start.plusMonths(1);
        return i;
    }

    private Investment(Account account, InvestmentType type, String reference, BigDecimal amount, BigDecimal rate,
                       LocalDate start, LocalDateTime now) {
        this.account = account;
        this.type = type;
        this.reference = reference;
        this.amount = amount;
        this.ratePercent = rate;
        this.startDate = start;
        this.createdAt = now;
    }

    /** Money put in so far: the deposit, or instalments × monthly amount. */
    public BigDecimal getInvested() {
        return type == InvestmentType.SIP ? amount.multiply(BigDecimal.valueOf(instalmentsPaid)) : amount;
    }

    public String getTitle() {
        return type == InvestmentType.SIP ? fund.getLabel() : "Fixed Deposit · " + tenureMonths / 12
                + (tenureMonths == 12 ? " year" : " years");
    }

    public Long getId() { return id; }
    public Account getAccount() { return account; }
    public InvestmentType getType() { return type; }
    public String getReference() { return reference; }
    public Fund getFund() { return fund; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getRatePercent() { return ratePercent; }
    public Integer getTenureMonths() { return tenureMonths; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getMaturityDate() { return maturityDate; }
    public BigDecimal getMaturityAmount() { return maturityAmount; }
    public LocalDate getNextDebitDate() { return nextDebitDate; }
    public int getInstalmentsPaid() { return instalmentsPaid; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
