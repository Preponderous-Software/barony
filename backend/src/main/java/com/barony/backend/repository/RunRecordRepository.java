package com.barony.backend.repository;

import com.barony.backend.model.RunRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Stores each player's finished-run history, newest first. */
public interface RunRecordRepository extends JpaRepository<RunRecord, Long> {
    List<RunRecord> findByUsernameOrderByFinishedAtDesc(String username);
}
