package com.barony.backend.service;

import com.barony.backend.model.UserPreferences;
import com.barony.backend.repository.UserPreferencesRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers storing and restoring a player's interface preferences against the real (in-memory H2)
 * repository. A second {@link PreferencesService} instance stands in for the player arriving from
 * another browser or device — the point of storing preferences per account rather than in one
 * browser's storage.
 */
@SpringBootTest
class PreferencesServiceTest {

    @Autowired
    private UserPreferencesRepository repository;

    @Test
    void loadsNothingForAPlayerWhoHasSavedNoPreferences() {
        PreferencesService service = new PreferencesService(repository);

        assertTrue(service.load("prefs-unknown-user").isEmpty(),
                "a player who has saved nothing should get an empty map, not a default");
    }

    @Test
    void preferencesFollowThePlayerToAnotherBrowser() {
        PreferencesService saving = new PreferencesService(repository);
        saving.save("prefs-roaming-user", Map.of(
                "settings", Map.of("theme", "high-contrast"),
                "panelLayout", Map.of("order", java.util.List.of("armies", "status"))));

        // A separate instance holds no state of its own, as a request from another device would.
        Map<String, Object> loaded = new PreferencesService(repository).load("prefs-roaming-user");

        assertEquals("high-contrast", ((Map<?, ?>) loaded.get("settings")).get("theme"));
        assertEquals(java.util.List.of("armies", "status"),
                ((Map<?, ?>) loaded.get("panelLayout")).get("order"));
    }

    @Test
    void savingReplacesThePreviousPreferences() {
        PreferencesService service = new PreferencesService(repository);
        service.save("prefs-replaced-user", Map.of("settings", Map.of("theme", "classic")));

        service.save("prefs-replaced-user", Map.of("panelState", Map.of("armies", false)));

        Map<String, Object> loaded = service.load("prefs-replaced-user");
        assertFalse(loaded.containsKey("settings"), "a save replaces the stored preferences wholesale");
        assertEquals(Map.of("armies", false), loaded.get("panelState"));
    }

    @Test
    void rejectsAMissingBody() {
        PreferencesService service = new PreferencesService(repository);

        assertThrows(IllegalArgumentException.class, () -> service.save("prefs-null-user", null));
    }

    @Test
    void rejectsPreferencesOverTheSizeCap() {
        PreferencesService service = new PreferencesService(repository);
        String oversized = "x".repeat(PreferencesService.MAX_PREFERENCES_LENGTH + 1);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> service.save("prefs-oversized-user", Map.of("settings", oversized)));

        assertTrue(thrown.getMessage().contains("too large"), thrown.getMessage());
        assertTrue(repository.findById("prefs-oversized-user").isEmpty(),
                "a rejected save should store nothing");
    }

    @Test
    void unreadableStoredPreferencesAreTreatedAsUnset() {
        UserPreferences corrupt = new UserPreferences("prefs-corrupt-user");
        corrupt.setPreferences("{not valid json");
        repository.save(corrupt);

        assertTrue(new PreferencesService(repository).load("prefs-corrupt-user").isEmpty(),
                "unreadable preferences should read as unset so the page can fall back to its defaults");
    }
}
