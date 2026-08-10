package com.barony.backend.repository;

import com.barony.backend.model.UserPreferences;
import org.springframework.data.jpa.repository.JpaRepository;

/** Stores and loads players' interface preferences by username. */
public interface UserPreferencesRepository extends JpaRepository<UserPreferences, String> {
}
