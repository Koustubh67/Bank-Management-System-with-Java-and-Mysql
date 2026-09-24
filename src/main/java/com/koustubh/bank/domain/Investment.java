package com.koustubh.bank.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A fixed deposit, a mutual fund SIP or a one-time mutual fund purchase.
 * For a SIP, {@code amount} is the monthly instalment and {@code units} grows with every instalment.
 */
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
    private Long schemeCode;
    private String schemeName;
    private BigDecimal units;
    private BigDecimal amount;
    private BigDecimal ratePercent;
    private Integer tenureMonths;
    private LocalDate startDate;
    private LocalDate maturityDate;
    private BigDecimal maturityAmount;
    private LocalDate nextDebitDate;
    private int instalmentsPaid;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    private LocalDateTime createdAt;

    protected Investment() {
    }

    public static Investment fixedDeposit(Account account, String reference, BigDecimal amount, FdTenure tenure,
                                          BigDecimal maturityAmount, LocalDate start, PaymentMethod method,
                                          LocalDateTime now) {
        Investment i = new Investment(account, InvestmentType.FIXED_DEPOSIT, reference, amount, start, method, now);
        i.ratePercent = tenure.getRate();
        i.tenureMonths = tenure.getMonths();
        i.maturityDate = start.plusMonths(tenure.getMonths());
        i.maturityAmount = maturityAmount;
        return i;
    }

    /** A SIP (first instalment bought now) or a one-time purchase of {@code units} units. */
    public static Investment mutualFund(Account account, InvestmentType type, String reference, long schemeCode,
                                        String schemeName, BigDecimal amount, BigDecimal units, LocalDate start,
                                        PaymentMethod method, LocalDateTime now) {
        Investment i = new Investment(account, type, reference, amount, start, method, now);
        i.ratePercent = BigDecimal.ZERO;
        i.schemeCode = schemeCode;
        i.schemeName = schemeName;
        i.units = units;
        i.instalmentsPaid = 1;
        if (type == InvestmentType.SIP) {
            i.nextDebitDate = start.plusMonths(1);
        }
        return i;
    }

    private Investment(Account account, InvestmentType type, String reference, BigDecimal amount, LocalDate start,
                       PaymentMethod method, LocalDateTime now) {
        this.account = account;
        this.type = type;
        this.reference = reference;
        this.amount = amount;
        this.startDate = start;
        this.paymentMethod = method;
        this.createdAt = now;
    }

    /** A monthly SIP instalment was paid and bought {@code newUnits} more units. */
    public void addInstalment(BigDecimal newUnits) {
        units = (units == null ? BigDecimal.ZERO : units).add(newUnits);
        instalmentsPaid++;
        nextDebitDate = nextDebitDate.plusMonths(1);
    }

    /** A monthly instalment could not be paid (e.g. low balance): move on to next month without buying units. */
    public void skipInstalment() {
        nextDebitDate = nextDebitDate.plusMonths(1);
    }

    /** Older SIPs were created before units were tracked; they get their units worked out once from real NAVs. */
    public void setUnits(BigDecimal units) {
        this.units = units;
    }

    /** Money put in so far. */
    public BigDecimal getInvested() {
        return type == InvestmentType.SIP ? amount.multiply(BigDecimal.valueOf(instalmentsPaid)) : amount;
    }

    public String getTitle() {
        return type.isMutualFund() ? schemeName
                : "Fixed Deposit · " + tenureMonths / 12 + (tenureMonths == 12 ? " year" : " years");
    }

    public Long getId() { return id; }
    public Account getAccount() { return account; }
    public InvestmentType getType() { return type; }
    public String getReference() { return reference; }
    public Long getSchemeCode() { return schemeCode; }
    public String getSchemeName() { return schemeName; }
    public BigDecimal getUnits() { return units; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getRatePercent() { return ratePercent; }
    public Integer getTenureMonths() { return tenureMonths; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getMaturityDate() { return maturityDate; }
    public BigDecimal getMaturityAmount() { return maturityAmount; }
    public LocalDate getNextDebitDate() { return nextDebitDate; }
    public int getInstalmentsPaid() { return instalmentsPaid; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
