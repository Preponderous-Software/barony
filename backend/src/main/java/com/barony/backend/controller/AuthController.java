package com.barony.backend.controller;

import com.barony.backend.model.Session;
import com.barony.backend.service.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@RestController
@CrossOrigin(origins = {"http://localhost:8080", "http://127.0.0.1:8080", "http://localhost:3000", "http://127.0.0.1:3000"})
@RequestMapping("/api/auth")
public class AuthController {
    
    @Autowired
    private SessionService sessionService;
    
    @PostMapping("/login")
    public Map<String, String> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        
        if (username == null || username.trim().isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Username is required"
            );
        }
        
        Session session = sessionService.getOrCreateSession(username.trim());
        
        Map<String, String> response = new HashMap<>();
        response.put("sessionId", session.getSessionId());
        response.put("username", session.getUsername());
        
        return response;
    }
}
