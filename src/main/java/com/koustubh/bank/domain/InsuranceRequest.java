package com.koustubh.bank.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A customer's request for an insurance plan. Staff call the customer back, then issue the policy or close it. */
@Entity
public class InsuranceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    private InsurancePlan plan;

    private String reference;
    private long coverWanted;
    private String contactName;
    private String mobile;
    private String city;
    private int age;
    private String extra;
    private String preferredTime;

    @Enumerated(EnumType.STRING)
    private InsuranceRequestStatus status;

    private String staffNote;
    private String handledBy;

    @OneToOne(fetch = FetchType.LAZY)
    private InsurancePolicy policy;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    protected InsuranceRequest() {
    }

    public InsuranceRequest(Account account, InsurancePlan plan, String reference, long coverWanted, String contactName,
                            String mobile, String city, int age, String extra, String preferredTime, LocalDateTime now) {
        this.account = account;
        this.plan = plan;
        this.reference = reference;
        this.coverWanted = coverWanted;
        this.contactName = contactName;
        this.mobile = mobile;
        this.city = city;
        this.age = age;
        this.extra = extra;
        this.preferredTime = preferredTime;
        this.status = InsuranceRequestStatus.REQUESTED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markContacted(String staff, String note, LocalDateTime now) {
        status = InsuranceRequestStatus.CONTACTED;
        update(staff, note, now);
    }

    public void issued(InsurancePolicy policy, String staff, LocalDateTime now) {
        this.policy = policy;
        status = InsuranceRequestStatus.POLICY_ISSUED;
        update(staff, staffNote, now);
    }

    public void close(String staff, String note, LocalDateTime now) {
        status = InsuranceRequestStatus.CLOSED;
        update(staff, note, now);
    }

    public boolean isOpen() {
        return status == InsuranceRequestStatus.REQUESTED || status == InsuranceRequestStatus.CONTACTED;
    }

    private void update(String staff, String note, LocalDateTime now) {
        handledBy = staff;
        if (note != null && !note.isBlank()) {
            staffNote = note.trim();
        }
        updatedAt = now;
    }

    public Long getId() { return id; }
    public Account getAccount() { return account; }
    public InsurancePlan getPlan() { return plan; }
    public String getReference() { return reference; }
    public long getCoverWanted() { return coverWanted; }
    public String getContactName() { return contactName; }
    public String getMobile() { return mobile; }
    public String getCity() { return city; }
    public int getAge() { return age; }
    public String getExtra() { return extra; }
    public String getPreferredTime() { return preferredTime; }
    public InsuranceRequestStatus getStatus() { return status; }
    public String getStaffNote() { return staffNote; }
    public String getHandledBy() { return handledBy; }
    public InsurancePolicy getPolicy() { return policy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
