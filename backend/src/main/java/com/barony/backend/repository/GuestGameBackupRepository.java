package com.barony.backend.repository;

import com.barony.backend.model.GuestGameBackup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Unreadable guest games set aside before their guests started fresh. Rows are only inserted. */
public interface GuestGameBackupRepository extends JpaRepository<GuestGameBackup, Long> {

    List<GuestGameBackup> findByGuestKeyOrderByBackedUpAtDesc(String guestKey);
}
