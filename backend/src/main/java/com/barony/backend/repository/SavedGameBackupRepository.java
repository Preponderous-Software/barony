package com.barony.backend.repository;

import com.barony.backend.model.SavedGameBackup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Unreadable saved games set aside before their players started fresh. Rows are only inserted. */
public interface SavedGameBackupRepository extends JpaRepository<SavedGameBackup, Long> {

    List<SavedGameBackup> findByUsernameOrderByBackedUpAtDesc(String username);
}
