package com.barony.backend.controller;

import com.barony.backend.model.Command;
import com.barony.backend.model.GameState;
import com.barony.backend.service.GameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = "*")
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
}
