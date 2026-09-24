package com.koustubh.bank.service;

import com.koustubh.bank.domain.*;
import com.koustubh.bank.exception.InvalidRequestException;
import com.koustubh.bank.exception.NotFoundException;
import com.koustubh.bank.repository.AccountRepository;
import com.koustubh.bank.repository.InsurancePolicyRepository;
import com.koustubh.bank.repository.InsuranceRequestRepository;
import com.koustubh.bank.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Insurance is sold the way banks really sell it: the customer asks for a plan, an expert calls back within
 * 24 hours to understand their needs and get a quote from an insurer, and staff then issue the policy.
 * The first premium is auto-debited from the account (with the consent taken on the call).
 */
@Service
public class InsuranceService {

    public static final List<String> CALL_TIMES = List.of("Morning (9–12)", "Afternoon (12–4)", "Evening (4–8)");

    /** What the customer submits on the "Talk to an expert" form. */
    public record Request(InsurancePlan plan, Long cover, String contactName, String mobile, String city, Integer age,
                          String extra, String preferredTime) {
    }

    /** What staff enter when the insurer has issued the policy. */
    public record Issue(String insurer, String policyNumber, Long cover, BigDecimal annualPremium, LocalDate startDate) {
    }

    private final AccountRepository accounts;
    private final InsuranceRequestRepository requests;
    private final InsurancePolicyRepository policies;
    private final TransactionRepository transactions;
    private final NumberGenerator numbers;
    private final NotificationService notifications;
    private final Clock clock;

    public InsuranceService(AccountRepository accounts, InsuranceRequestRepository requests,
                            InsurancePolicyRepository policies, TransactionRepository transactions,
                            NumberGenerator numbers, NotificationService notifications, Clock clock) {
        this.accounts = accounts;
        this.requests = requests;
        this.policies = policies;
        this.transactions = transactions;
        this.numbers = numbers;
        this.notifications = notifications;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- customer side

    @Transactional
    public InsuranceRequest request(String customerId, Request r) {
        List<String> errors = new ArrayList<>();
        if (r.plan() == null) errors.add("Choose an insurance type");
        if (r.plan() != null && (r.cover() == null || !r.plan().getCovers().contains(r.cover()))) errors.add("Choose a cover amount");
        if (blank(r.contactName()) || r.contactName().trim().length() > 80) errors.add("Enter your name");
        if (r.mobile() == null || !r.mobile().trim().matches("[6-9]\\d{9}")) errors.add("Enter a valid 10-digit mobile number");
        if (blank(r.city()) || r.city().trim().length() > 50) errors.add("Enter your city");
        if (r.age() == null || r.age() < 18 || r.age() > 80) errors.add("Age must be between 18 and 80");
        if (blank(r.extra()) || r.extra().trim().length() > 300) {
            errors.add(r.plan() == null ? "Add the plan details" : "Fill in: " + r.plan().getExtraLabel().toLowerCase());
        }
        if (r.preferredTime() == null || !CALL_TIMES.contains(r.preferredTime())) errors.add("Choose a time for our call");
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(String.join(" · ", errors));
        }
        Account account = account(customerId);
        account.ensureActive();
        String ref;
        do {
            ref = numbers.newReference("JBQ");
        } while (requests.existsByReference(ref));
        InsuranceRequest saved = requests.save(new InsuranceRequest(account, r.plan(), ref, r.cover(),
                r.contactName().trim(), r.mobile().trim(), r.city().trim(), r.age(), r.extra().trim(),
                r.preferredTime(), LocalDateTime.now(clock)));
        notifications.notify(account.getCustomer(), "We've received your insurance request",
                "Thanks for choosing JavaBank " + r.plan().getLabel() + " insurance (ref " + ref + "). Our expert will call you on "
                        + r.mobile().trim() + " within 24 hours, " + r.preferredTime().toLowerCase() + ".");
        return saved;
    }

    @Transactional(readOnly = true)
    public List<InsuranceRequest> requestsOf(String customerId) {
        return requests.findByAccountIdOrderByIdDesc(account(customerId).getId());
    }

    @Transactional(readOnly = true)
    public List<InsurancePolicy> policiesOf(String customerId) {
        return policies.findByAccountIdOrderByIdDesc(account(customerId).getId());
    }

    /** A policy page; only the policy's own customer can open it. */
    @Transactional(readOnly = true)
    public InsurancePolicy policyOf(String customerId, Long policyId) {
        return policies.findById(policyId)
                .filter(p -> p.getAccount().getCustomer().getCustomerId().equals(customerId))
                .orElseThrow(() -> new NotFoundException("Policy not found"));
    }

    // ---------------------------------------------------------------- staff side

    @Transactional(readOnly = true)
    public List<InsuranceRequest> openRequests() {
        return requests.findByStatusInOrderByIdAsc(List.of(InsuranceRequestStatus.REQUESTED, InsuranceRequestStatus.CONTACTED));
    }

    @Transactional(readOnly = true)
    public List<InsuranceRequest> recentRequests() {
        return requests.findTop100ByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public List<InsurancePolicy> allPolicies() {
        return policies.findAllByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public long openRequestCount() {
        return requests.countByStatusIn(List.of(InsuranceRequestStatus.REQUESTED, InsuranceRequestStatus.CONTACTED));
    }

    @Transactional
    public void markContacted(Long requestId, String staff, String note) {
        InsuranceRequest r = openRequest(requestId);
        r.markContacted(staff, note, LocalDateTime.now(clock));
    }

    /** Records the insurer's policy and auto-debits the first annual premium. */
    @Transactional
    public InsurancePolicy issue(Long requestId, String staff, Issue issue) {
        InsuranceRequest r = openRequest(requestId);
        if (blank(issue.insurer()) || blank(issue.policyNumber())) {
            throw new InvalidRequestException("Enter the insurer and the policy number");
        }
        if (issue.cover() == null || issue.cover() <= 0 || issue.annualPremium() == null || issue.annualPremium().signum() <= 0) {
            throw new InvalidRequestException("Enter the cover and the annual premium");
        }
        if (policies.existsByPolicyNumber(issue.policyNumber().trim())) {
            throw new InvalidRequestException("This policy number is already recorded");
        }
        LocalDate start = issue.startDate() == null ? LocalDate.now(clock) : issue.startDate();
        Account account = accounts.findByIdForUpdate(r.getAccount().getId()).orElseThrow();
        BigDecimal premium = issue.annualPremium().setScale(2, RoundingMode.HALF_UP);
        account.debit(premium);
        transactions.save(new Transaction(account, TransactionType.INSURANCE_PREMIUM, premium, UUID.randomUUID().toString(),
                null, "INS/" + issue.policyNumber().trim() + "/" + issue.insurer().trim(), LocalDateTime.now(clock)));
        InsurancePolicy policy = policies.save(new InsurancePolicy(account, r.getPlan(), issue.insurer().trim(),
                issue.policyNumber().trim(), BigDecimal.valueOf(issue.cover()), premium, r.getExtra(), start,
                LocalDateTime.now(clock)));
        r.issued(policy, staff, LocalDateTime.now(clock));
        notifications.notify(account.getCustomer(), "Your policy is active",
                "Your " + r.getPlan().getLabel() + " policy " + policy.getPolicyNumber() + " with " + policy.getInsurer()
                        + " is active from " + start + ". First premium of Rs " + premium.toPlainString() + " paid from your account.");
        return policy;
    }

    @Transactional
    public void close(Long requestId, String staff, String note) {
        if (blank(note)) {
            throw new InvalidRequestException("Add a note explaining why the request is closed");
        }
        InsuranceRequest r = openRequest(requestId);
        r.close(staff, note, LocalDateTime.now(clock));
        notifications.notify(r.getAccount().getCustomer(), "Insurance request closed",
                "Your insurance request " + r.getReference() + " was closed: " + note.trim());
    }

    /** Demo data only: an already-issued policy, without debiting the account. */
    @Transactional
    public InsurancePolicy importExistingPolicy(String customerId, InsurancePlan plan, String insurer, String policyNumber,
                                                long cover, BigDecimal premium, String details, LocalDate start) {
        return policies.save(new InsurancePolicy(account(customerId), plan, insurer, policyNumber, BigDecimal.valueOf(cover),
                premium, details, start, LocalDateTime.now(clock)));
    }

    private InsuranceRequest openRequest(Long id) {
        InsuranceRequest r = requests.findById(id).orElseThrow(() -> new NotFoundException("Request not found"));
        if (!r.isOpen()) {
            throw new InvalidRequestException("This request is already " + r.getStatus().getLabel().toLowerCase());
        }
        return r;
    }

    private Account account(String customerId) {
        return accounts.findByCustomerLogin(customerId).orElseThrow(() -> new NotFoundException("Account not found"));
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
