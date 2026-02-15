package com.barony.webclient.controller;

import com.barony.webclient.model.*;
import com.barony.webclient.service.BackendService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class WebController {
    
    @Autowired
    private BackendService backendService;
    
    @GetMapping("/")
    public String index(Model model) {
        try {
            GameState state = backendService.getState();
            RulerStats stats = backendService.getRulerStats();
            model.addAttribute("gameState", state);
            model.addAttribute("rulerStats", stats);
        } catch (Exception e) {
            model.addAttribute("error", "Could not connect to backend: " + e.getMessage());
        }
        return "index";
    }
    
    @PostMapping("/api/tick")
    @ResponseBody
    public GameState tick() {
        return backendService.tick();
    }
    
    @PostMapping("/api/command")
    @ResponseBody
    public GameState command(@RequestBody Command command) {
        return backendService.sendCommand(command);
    }
    
    @PostMapping("/api/reset")
    @ResponseBody
    public GameState reset() {
        return backendService.reset();
    }
    
    @PostMapping("/api/decision")
    @ResponseBody
    public GameState decision(@RequestBody RulerDecision decision) {
        return backendService.changePolicy(decision);
    }
    
    @GetMapping("/api/state")
    @ResponseBody
    public GameState getState() {
        return backendService.getState();
    }
    
    @GetMapping("/api/ruler-stats")
    @ResponseBody
    public RulerStats getRulerStats() {
        return backendService.getRulerStats();
    }
}
