package com.barony.backend.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class Session {
    private String sessionId;
    private String username;
    private GameState gameState;
    private LocalDateTime lastAccessed;
    
    public Session(String username, GameState gameState) {
        this.sessionId = UUID.randomUUID().toString();
        this.username = username;
        this.gameState = gameState;
        this.lastAccessed = LocalDateTime.now();
    }
    
    public void updateLastAccessed() {
        this.lastAccessed = LocalDateTime.now();
    }
}
