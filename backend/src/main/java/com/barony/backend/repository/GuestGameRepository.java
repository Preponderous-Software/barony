package com.barony.backend.repository;

import com.barony.backend.model.GuestGame;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Stores guests' games. This repository only ever touches the {@code guest_game} table; nothing here
 * can read or delete a real account's rows.
 */
public interface GuestGameRepository extends JpaRepository<GuestGame, String> {

    /**
     * Delete every guest idle since before {@code cutoff}. A bulk delete on the guest entity, so it
     * can only remove guest rows; returns how many were removed.
     */
    @Modifying
    @Transactional
    @Query("delete from GuestGame g where g.lastSeenAt < :cutoff")
    int deleteIdleSince(@Param("cutoff") Instant cutoff);
}
