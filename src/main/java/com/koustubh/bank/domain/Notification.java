package com.koustubh.bank.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** An SMS or email alert sent to a customer (simulated: stored here and written to the log). */
@Entity
public class Notification {

    public enum Channel { SMS, EMAIL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    private Channel channel;

    private String destination;
    private String subject;
    private String message;
    private LocalDateTime createdAt;

    protected Notification() {
    }

    public Notification(Customer customer, Channel channel, String destination, String subject, String message,
                        LocalDateTime createdAt) {
        this.customer = customer;
        this.channel = channel;
        this.destination = destination;
        this.subject = subject;
        this.message = message;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    public Channel getChannel() { return channel; }
    public String getDestination() { return destination; }
    public String getSubject() { return subject; }
    public String getMessage() { return message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
