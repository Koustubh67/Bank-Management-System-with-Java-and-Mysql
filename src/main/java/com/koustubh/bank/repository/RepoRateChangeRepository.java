package com.koustubh.bank.repository;

import com.koustubh.bank.domain.RepoRateChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RepoRateChangeRepository extends JpaRepository<RepoRateChange, Long> {

    List<RepoRateChange> findTop20ByOrderByIdDesc();
}
