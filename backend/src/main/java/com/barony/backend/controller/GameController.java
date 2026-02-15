package com.barony.backend.controller;

import com.barony.backend.model.Command;
import com.barony.backend.model.GameState;
import com.barony.backend.model.RulerDecision;
import com.barony.backend.model.RulerStats;
import com.barony.backend.service.GameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = {"http://localhost:8080", "http://127.0.0.1:8080", "http://localhost:3000", "http://127.0.0.1:3000", "http://backend:8080", "http://web-client:3000"})
public class GameController {
    
    @Autowired
    private GameService gameService;
    
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
}
