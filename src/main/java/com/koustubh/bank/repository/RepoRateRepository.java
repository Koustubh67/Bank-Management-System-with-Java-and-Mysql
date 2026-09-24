package com.koustubh.bank.repository;

import com.koustubh.bank.domain.RepoRate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface RepoRateRepository extends JpaRepository<RepoRate, Long> {

    /** Locks the repo rate row: taken first by loan approvals and repo rate changes, so they never interleave. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RepoRate r where r.id = " + RepoRate.ID)
    Optional<RepoRate> lockCurrent();
}
