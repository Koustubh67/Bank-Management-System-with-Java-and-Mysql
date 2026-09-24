package com.koustubh.bank.service;

import com.koustubh.bank.domain.Customer;
import com.koustubh.bank.domain.Notification;
import com.koustubh.bank.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Sends customer alerts by SMS and email. This demo has no SMS or email provider, so each alert is stored in the
 * notification table (shown on the dashboard and to staff) and written to the log, as a provider call would be.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    /** Sends the alert by SMS (if a mobile number is on file) and by email. Joins the caller's transaction. */
    @Transactional
    public void notify(Customer customer, String subject, String message) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (customer.getMobile() != null) {
            notifications.save(new Notification(customer, Notification.Channel.SMS, "+91" + customer.getMobile(),
                    subject, message, now));
            log.info("[SMS to +91******{}] {}", customer.getMobile().substring(6), message);
        }
        notifications.save(new Notification(customer, Notification.Channel.EMAIL, customer.getEmail(), subject,
                message, now));
        log.info("[Email to {}] {}: {}", customer.getMaskedEmail(), subject, message);
    }

    /** One-time password for a payment, sent by SMS only. */
    @Transactional
    public void sendOtp(Customer customer, String otp, String purpose) {
        String message = otp + " is your JavaBank OTP to " + purpose + ". Valid for 5 minutes. Never share it.";
        notifications.save(new Notification(customer, Notification.Channel.SMS,
                customer.getMobile() == null ? "-" : "+91" + customer.getMobile(), "OTP", message,
                LocalDateTime.now(clock)));
        log.info("[SMS OTP to customer {}] {}", customer.getCustomerId(), message);
    }

    @Transactional(readOnly = true)
    public List<Notification> recentFor(Customer customer) {
        return notifications.findTop10ByCustomerIdAndChannelOrderByIdDesc(customer.getId(), Notification.Channel.EMAIL);
    }

    @Transactional(readOnly = true)
    public List<Notification> outbox() {
        return notifications.findTop100ByOrderByIdDesc();
    }
}
