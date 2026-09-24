package com.koustubh.bank.repository;

import com.koustubh.bank.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    boolean existsByPan(String pan);

    boolean existsByAadhaar(String aadhaar);

    Optional<Customer> findByCustomerId(String customerId);

    boolean existsByCustomerId(String customerId);

    boolean existsByMobile(String mobile);
}
