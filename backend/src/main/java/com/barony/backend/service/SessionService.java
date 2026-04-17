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

    private static final int SESSION_TIMEOUT_MINUTES = 60;

    public Session getOrCreateSession(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        
        cleanupExpiredSessions();

        return sessions.values().stream()
            .filter(s -> s.getUsername().equals(username))
            .findFirst()
            .orElseGet(() -> {
                GameState newGameState = mapGenerator.generate();
                Session newSession = new Session(username, newGameState);
                sessions.put(newSession.getSessionId(), newSession);
                return newSession;
            });
    }

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

    private void cleanupExpiredSessions() {
        LocalDateTime expirationTime = LocalDateTime.now().minusMinutes(SESSION_TIMEOUT_MINUTES);
        sessions.entrySet().removeIf(entry -> 
            entry.getValue().getLastAccessed().isBefore(expirationTime)
        );
    }

    public int getActiveSessionCount() {
        cleanupExpiredSessions();
        return sessions.size();
    }
}
