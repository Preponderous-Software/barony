package com.barony.backend.service;

import com.barony.backend.model.UserPreferences;
import com.barony.backend.repository.UserPreferencesRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Owns each player's stored interface preferences, keyed by username. The stored payload is
 * whatever object the game page saves — the sidebar arrangement, each panel's open/closed state
 * and the display settings — held as JSON so a preference added to the page later needs no change
 * here. Storing them against the account is what lets the arrangement follow a player to another
 * browser or device, rather than staying in the one browser's {@code localStorage}.
 */
@Service
public class PreferencesService {

    private static final Logger log = LoggerFactory.getLogger(PreferencesService.class);

    /**
     * Cap on the stored JSON. The payload is opaque, so a limit is what keeps an account from
     * being used as unbounded storage; it is far above what the page's own preferences need.
     */
    static final int MAX_PREFERENCES_LENGTH = 8192;

    private final UserPreferencesRepository userPreferencesRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public PreferencesService(UserPreferencesRepository userPreferencesRepository) {
        this.userPreferencesRepository = userPreferencesRepository;
    }

    /**
     * Return the player's stored preferences, or an empty map when they have never saved any (so
     * a first-time player is told "nothing stored" rather than being handed a server-side default
     * the page would have to distinguish from a real choice).
     */
    public Map<String, Object> load(String username) {
        return userPreferencesRepository.findById(username)
                .map(this::readStored)
                .orElseGet(Collections::emptyMap);
    }

    /**
     * Replace the player's stored preferences with the given object, returning what was stored.
     *
     * @throws IllegalArgumentException if the body is missing, unserializable, or over the size cap
     */
    public Map<String, Object> save(String username, Map<String, Object> preferences) {
        if (preferences == null) {
            throw new IllegalArgumentException("Request body 'preferences' is required");
        }

        String json = serialize(preferences);
        if (json.length() > MAX_PREFERENCES_LENGTH) {
            throw new IllegalArgumentException("Preferences are too large: " + json.length()
                    + " characters, limit " + MAX_PREFERENCES_LENGTH);
        }

        UserPreferences stored = userPreferencesRepository.findById(username)
                .orElseGet(() -> new UserPreferences(username));
        stored.setPreferences(json);
        stored.setUpdatedAt(Instant.now());
        userPreferencesRepository.save(stored);
        return preferences;
    }

    // Preferences that can no longer be read (hand-edited, or written by an older shape) are
    // reported as unset rather than raised: the page can rebuild them from its own defaults, and
    // failing the request would leave the player unable to load the game page at all.
    private Map<String, Object> readStored(UserPreferences stored) {
        try {
            return objectMapper.readValue(stored.getPreferences(),
                    new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Could not read stored preferences for '{}' — treating them as unset: {}",
                    stored.getUsername(), e.getMessage());
            return Collections.emptyMap();
        }
    }

    private String serialize(Map<String, Object> preferences) {
        try {
            return objectMapper.writeValueAsString(preferences);
        } catch (Exception e) {
            throw new IllegalArgumentException("Preferences could not be stored: " + e.getMessage());
        }
    }
}
