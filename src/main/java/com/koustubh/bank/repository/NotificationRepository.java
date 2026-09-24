package com.koustubh.bank.repository;

import com.koustubh.bank.domain.Notification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop10ByCustomerIdAndChannelOrderByIdDesc(Long customerId, Notification.Channel channel);

    @EntityGraph(attributePaths = "customer")
    List<Notification> findTop100ByOrderByIdDesc();
}
