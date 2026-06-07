package com.barony.backend.controller;

import com.barony.backend.model.Command;
import com.barony.backend.model.GameState;
import com.barony.backend.model.RulerDecision;
import com.barony.backend.model.RulerStats;
import com.barony.backend.model.Session;
import com.barony.backend.service.GameService;
import com.barony.backend.service.SessionService;
import com.barony.backend.service.UserAuthClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@CrossOrigin(origins = {"http://localhost:8080", "http://127.0.0.1:8080", "http://localhost:3000", "http://127.0.0.1:3000"})
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;
    private final SessionService sessionService;
    private final UserAuthClient userAuthClient;

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
        validateDecision(decision);
        try {
            gameService.changePolicy(decision.getCategory(), decision.getChoice());
            return gameService.getState();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Policy change on cooldown: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Invalid policy choice: " + e.getMessage());
        }
    }

    @GetMapping("/api/ruler-stats")
    public RulerStats rulerStats() {
        return gameService.getRulerStats();
    }

    // Authenticated, per-user endpoints
    //
    // Each requires a valid UserAuth-issued JWT in the `Authorization: Bearer <token>` header.
    // The token is validated against UserAuth on every request (so logged-out / revoked / expired
    // tokens are refused), and the game state is keyed by the authenticated username.
    //
    // GameService is a shared singleton holding a single mutable GameState. Each request swaps in
    // its own user's state via setGameState(...) and then operates on it, so the load-operate-read
    // sequence must hold the GameService monitor for its full duration; otherwise a concurrent
    // request for a different user could swap the shared state mid-sequence. We therefore
    // synchronize on `gameService` (not on the per-session state object) across each block.

    private Session authenticate(String authorization) {
        String token = bearerToken(authorization);
        if (token == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Authentication required. Please log in.");
        }
        String username = userAuthClient.validate(token).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Invalid, expired, or revoked token. Please log in again."));
        return sessionService.getOrCreateSession(username);
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = authorization.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    @GetMapping("/api/session/state")
    public GameState getSessionState(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Session session = authenticate(authorization);
        synchronized (gameService) {
            gameService.setGameState(session.getGameState());
            return gameService.getState();
        }
    }

    @PostMapping("/api/session/tick")
    public GameState sessionTick(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Session session = authenticate(authorization);
        synchronized (gameService) {
            gameService.setGameState(session.getGameState());
            gameService.tick();
            return gameService.getState();
        }
    }

    @PostMapping("/api/session/command")
    public GameState sessionCommand(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Command command) {
        Session session = authenticate(authorization);
        synchronized (gameService) {
            gameService.setGameState(session.getGameState());
            gameService.executeCommand(command);
            return gameService.getState();
        }
    }

    @PostMapping("/api/session/reset")
    public GameState sessionReset(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Session session = authenticate(authorization);
        synchronized (gameService) {
            gameService.resetGame();
            session.setGameState(gameService.getGameStateInternal());
            return gameService.getState();
        }
    }

    @PostMapping("/api/session/decision")
    public GameState sessionDecision(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody RulerDecision decision) {
        Session session = authenticate(authorization);
        validateDecision(decision);
        synchronized (gameService) {
            gameService.setGameState(session.getGameState());
            try {
                gameService.changePolicy(decision.getCategory(), decision.getChoice());
                return gameService.getState();
            } catch (IllegalStateException e) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Policy change on cooldown: " + e.getMessage());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid policy choice: " + e.getMessage());
            }
        }
    }

    @GetMapping("/api/session/ruler-stats")
    public RulerStats sessionRulerStats(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Session session = authenticate(authorization);
        synchronized (gameService) {
            gameService.setGameState(session.getGameState());
            return gameService.getRulerStats();
        }
    }

    private void validateDecision(RulerDecision decision) {
        if (decision == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Request body 'decision' is required");
        }
        if (decision.getCategory() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Field 'category' is required");
        }
        if (decision.getChoice() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Field 'choice' is required");
        }
    }
}
