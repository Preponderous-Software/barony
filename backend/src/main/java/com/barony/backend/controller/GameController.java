package com.barony.backend.controller;

import com.barony.backend.model.Command;
import com.barony.backend.model.GameState;
import com.barony.backend.model.RulerDecision;
import com.barony.backend.model.RulerStats;
import com.barony.backend.model.Session;
import com.barony.backend.service.GameService;
import com.barony.backend.service.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@CrossOrigin(origins = {"http://localhost:8080", "http://127.0.0.1:8080", "http://localhost:3000", "http://127.0.0.1:3000"})
public class GameController {
    
    @Autowired
    private GameService gameService;
    
    @Autowired
    private SessionService sessionService;
    
    @GetMapping("/state")
    public GameState getState() {
        return gameService.getState();
    }
    
    @PostMapping("/tick")
    public GameState tick() {
        gameService.tick();
        return gameService.getState();
    }
    
    @PostMapping("/command")
    public GameState command(@RequestBody Command command) {
        gameService.executeCommand(command);
        return gameService.getState();
    }
    
    @PostMapping("/api/reset")
    public GameState reset() {
        gameService.resetGame();
        return gameService.getState();
    }
    
    @PostMapping("/api/decision")
    public GameState decision(@RequestBody RulerDecision decision) {
        // Validate request
        if (decision == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "Request body 'decision' is required"
            );
        }
        if (decision.getCategory() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "Field 'category' is required"
            );
        }
        if (decision.getChoice() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "Field 'choice' is required"
            );
        }
        
        try {
            gameService.changePolicy(decision.getCategory(), decision.getChoice());
            return gameService.getState();
        } catch (IllegalStateException e) {
            // Policy change on cooldown
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, 
                "Policy change on cooldown: " + e.getMessage()
            );
        } catch (IllegalArgumentException e) {
            // Invalid policy choice
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, 
                "Invalid policy choice: " + e.getMessage()
            );
        }
    }
    
    @GetMapping("/api/ruler-stats")
    public RulerStats rulerStats() {
        return gameService.getRulerStats();
    }
    
    // Session-aware endpoints
    
    private Session validateAndGetSession(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Session ID is required. Please login first."
            );
        }
        
        Session session = sessionService.getSession(sessionId);
        if (session == null) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Invalid or expired session. Please login again."
            );
        }
        
        return session;
    }
    
    @GetMapping("/api/session/state")
    public GameState getSessionState(@RequestHeader("X-Session-Id") String sessionId) {
        Session session = validateAndGetSession(sessionId);
        synchronized (session.getGameState()) {
            gameService.setGameState(session.getGameState());
            return gameService.getState();
        }
    }
    
    @PostMapping("/api/session/tick")
    public GameState sessionTick(@RequestHeader("X-Session-Id") String sessionId) {
        Session session = validateAndGetSession(sessionId);
        synchronized (session.getGameState()) {
            gameService.setGameState(session.getGameState());
            gameService.tick();
            return gameService.getState();
        }
    }
    
    @PostMapping("/api/session/command")
    public GameState sessionCommand(
            @RequestHeader("X-Session-Id") String sessionId,
            @RequestBody Command command) {
        Session session = validateAndGetSession(sessionId);
        synchronized (session.getGameState()) {
            gameService.setGameState(session.getGameState());
            gameService.executeCommand(command);
            return gameService.getState();
        }
    }
    
    @PostMapping("/api/session/reset")
    public GameState sessionReset(@RequestHeader("X-Session-Id") String sessionId) {
        Session session = validateAndGetSession(sessionId);
        synchronized (session.getGameState()) {
            gameService.resetGame();
            session.setGameState(gameService.getGameStateInternal());
            return gameService.getState();
        }
    }
    
    @PostMapping("/api/session/decision")
    public GameState sessionDecision(
            @RequestHeader("X-Session-Id") String sessionId,
            @RequestBody RulerDecision decision) {
        Session session = validateAndGetSession(sessionId);
        
        // Validate request
        if (decision == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Request body 'decision' is required"
            );
        }
        if (decision.getCategory() == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Field 'category' is required"
            );
        }
        if (decision.getChoice() == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Field 'choice' is required"
            );
        }
        
        synchronized (session.getGameState()) {
            gameService.setGameState(session.getGameState());
            
            try {
                gameService.changePolicy(decision.getCategory(), decision.getChoice());
                return gameService.getState();
            } catch (IllegalStateException e) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT, 
                    "Policy change on cooldown: " + e.getMessage()
                );
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, 
                    "Invalid policy choice: " + e.getMessage()
                );
            }
        }
    }
    
    @GetMapping("/api/session/ruler-stats")
    public RulerStats sessionRulerStats(@RequestHeader("X-Session-Id") String sessionId) {
        Session session = validateAndGetSession(sessionId);
        synchronized (session.getGameState()) {
            gameService.setGameState(session.getGameState());
            return gameService.getRulerStats();
        }
    }
}
