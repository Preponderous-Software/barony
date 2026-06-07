package com.barony.backend.service;

import com.barony.backend.model.GameState;
import com.barony.backend.model.Session;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionService {
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final MapGenerator mapGenerator = new MapGenerator();
    
    // Session timeout in minutes
    private static final int SESSION_TIMEOUT_MINUTES = 60;
    
    /**
     * Create or retrieve a session for the given username
     */
    public Session getOrCreateSession(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        
        // Clean up old sessions periodically
        cleanupExpiredSessions();
        
        // Reuse the existing session for this user, refreshing its access time so an active
        // player's game state is not reclaimed by cleanupExpiredSessions mid-play.
        return sessions.values().stream()
            .filter(s -> s.getUsername().equals(username))
            .findFirst()
            .map(existing -> {
                existing.updateLastAccessed();
                return existing;
            })
            .orElseGet(() -> {
                // Create new session with fresh game state
                GameState newGameState = mapGenerator.generate();
                Session newSession = new Session(username, newGameState);
                sessions.put(newSession.getSessionId(), newSession);
                return newSession;
            });
    }

    /**
     * Remove expired sessions
     */
    private void cleanupExpiredSessions() {
        LocalDateTime expirationTime = LocalDateTime.now().minusMinutes(SESSION_TIMEOUT_MINUTES);
        sessions.entrySet().removeIf(entry -> 
            entry.getValue().getLastAccessed().isBefore(expirationTime)
        );
    }
    
    /**
     * Get total number of active sessions
     */
    public int getActiveSessionCount() {
        cleanupExpiredSessions();
        return sessions.size();
    }
}
