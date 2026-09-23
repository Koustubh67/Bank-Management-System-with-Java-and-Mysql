package com.koustubh.bank.repository;

import com.koustubh.bank.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    boolean existsByPan(String pan);

    boolean existsByAadhaar(String aadhaar);
}
