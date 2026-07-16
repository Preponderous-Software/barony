package com.barony.backend.repository;

import com.barony.backend.model.SavedGame;
import org.springframework.data.jpa.repository.JpaRepository;

/** Stores and loads players' saved games by username. */
public interface SavedGameRepository extends JpaRepository<SavedGame, String> {
}
