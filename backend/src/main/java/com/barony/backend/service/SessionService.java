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
        
        // Use computeIfAbsent for thread-safe session creation/retrieval
        return sessions.values().stream()
            .filter(s -> s.getUsername().equals(username))
            .findFirst()
            .orElseGet(() -> {
                // Create new session with fresh game state
                GameState newGameState = mapGenerator.generate();
                Session newSession = new Session(username, newGameState);
                sessions.put(newSession.getSessionId(), newSession);
                return newSession;
            });
    }
    
    /**
     * Get session by session ID
     */
    public Session getSession(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        
        Session session = sessions.get(sessionId);
        if (session != null) {
            session.updateLastAccessed();
        }
        return session;
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
