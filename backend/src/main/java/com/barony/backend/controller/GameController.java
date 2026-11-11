package com.barony.backend.controller;

import com.barony.backend.model.BattleResult;
import com.barony.backend.model.Command;
import com.barony.backend.model.GameState;
import com.barony.backend.model.RulerDecision;
import com.barony.backend.model.RulerStats;
import com.barony.backend.model.RunHistory;
import com.barony.backend.model.Session;
import com.barony.backend.service.AuthCookies;
import com.barony.backend.service.GameService;
import com.barony.backend.service.GuestCookies;
import com.barony.backend.service.GuestSessionService;
import com.barony.backend.service.PreferencesService;
import com.barony.backend.service.SessionService;
import com.barony.backend.service.UserAuthClient;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@CrossOrigin(origins = {"http://localhost:8080", "http://127.0.0.1:8080", "http://localhost:3000", "http://127.0.0.1:3000"})
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;
    private final SessionService sessionService;
    private final UserAuthClient userAuthClient;
    private final AuthCookies authCookies;
    private final PreferencesService preferencesService;
    private final GuestSessionService guestSessionService;
    private final GuestCookies guestCookies;

    /**
     * Optional request header naming which identity the page is playing as: "account" or "guest".
     * An account page sends "account", so if its account cookie has expired it gets a 401 (and is
     * sent to log in) rather than silently being shown a guest game from the same browser.
     */
    static final String PLAYER_MODE_HEADER = "X-Barony-Player";

    /** Who a request plays as: an account's session or a guest's, never both. */
    private record Player(Session session, boolean guest) { }

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
        runCommand(command);
        return gameService.getState();
    }

    /**
     * Runs a command, turning a refusal into the matching HTTP error: 409 when the army cannot do
     * that now (in a pending battle, on the march, not on a tile where it can train), 400 when the
     * request itself is wrong (no such army, unknown unit type, a count it cannot meet).
     */
    private void runCommand(Command command) {
        if (command == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A command is required");
        }
        try {
            gameService.executeCommand(command);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
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
    // Each requires a valid UserAuth-issued JWT, read from the HttpOnly `barony_token` cookie
    // (the browser sends it automatically; JavaScript can't), or from an `Authorization: Bearer`
    // header as a fallback for CLI / direct API clients. The token is validated against UserAuth
    // on every request (so logged-out / revoked / expired tokens are refused), and the game state
    // is keyed by the authenticated username.
    //
    // GameService is a shared singleton holding a single mutable GameState. Each request swaps in
    // its own user's state via setGameState(...) and then operates on it, so the load-operate-read
    // sequence must hold the GameService monitor for its full duration; otherwise a concurrent
    // request for a different user could swap the shared state mid-sequence. We therefore
    // synchronize on `gameService` (not on the per-session state object) across each block.

    // Loading the session throws UnreadableSaveException when the account's save cannot be read;
    // UnreadableSaveHandler turns that into a 409, so no endpoint here can save over that save.
    private Session authenticate(HttpServletRequest request) {
        return sessionService.getOrCreateSession(authenticatedUsername(request));
    }

    private String authenticatedUsername(HttpServletRequest request) {
        String token = accountToken(request);
        if (token == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Authentication required. Please log in.");
        }
        return userAuthClient.validate(token).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Invalid, expired, or revoked token. Please log in again."));
    }

    private String accountToken(HttpServletRequest request) {
        return authCookies.read(request)
                .orElseGet(() -> bearerToken(request.getHeader(HttpHeaders.AUTHORIZATION)));
    }

    /**
     * Resolve the request to an account or a guest. An account token, when present, always wins and
     * is validated with UserAuth exactly as before (an invalid one is a 401, never a fall-back to
     * guest). Only a request with no account token, or one that names guest mode, is served from the
     * guest cookie; guest sessions come from {@link GuestSessionService} and never from
     * {@link SessionService}.
     */
    private Player resolvePlayer(HttpServletRequest request) {
        Player player = resolvePlayerSession(request);
        // Field battles are the default for players: a game that has never had the setting (a new
        // one, or a save from before field battles existed) takes it here.
        GameState state = player.session().getGameState();
        if (state != null && state.getFieldBattles() == null) {
            state.setFieldBattles(true);
        }
        return player;
    }

    private Player resolvePlayerSession(HttpServletRequest request) {
        String mode = request.getHeader(PLAYER_MODE_HEADER);
        boolean wantsGuest = "guest".equalsIgnoreCase(mode);
        boolean wantsAccount = "account".equalsIgnoreCase(mode);
        if (!wantsGuest && (wantsAccount || accountToken(request) != null)) {
            return new Player(authenticate(request), false);
        }
        Session guest = guestCookies.read(request)
                .flatMap(guestSessionService::find)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Authentication required. Please log in or play as a guest."));
        return new Player(guest, true);
    }

    /** Persist after a change, to the store the player belongs to. */
    private void save(Player player) {
        if (player.guest()) {
            guestSessionService.save(player.session());
        } else {
            sessionService.save(player.session());
        }
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = authorization.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    @GetMapping("/api/session/state")
    public GameState getSessionState(HttpServletRequest request) {
        Player player = resolvePlayer(request);
        Session session = player.session();
        synchronized (gameService) {
            gameService.setGameState(session.getGameState());
            return gameService.getState();
        }
    }

    @PostMapping("/api/session/tick")
    public GameState sessionTick(HttpServletRequest request) {
        Player player = resolvePlayer(request);
        Session session = player.session();
        synchronized (gameService) {
            gameService.setGameState(session.getGameState());
            gameService.tick();
            save(player);
            return gameService.getState();
        }
    }

    @PostMapping("/api/session/command")
    public GameState sessionCommand(
            HttpServletRequest request,
            @RequestBody Command command) {
        Player player = resolvePlayer(request);
        Session session = player.session();
        synchronized (gameService) {
            gameService.setGameState(session.getGameState());
            runCommand(command);
            save(player);
            return gameService.getState();
        }
    }

    /**
     * Resolves one of the paused turn's pending battles: {@code FIELD} with the result the player
     * fought on the battlefield, or {@code AUTO} to let the map's rule decide it. The turn finishes
     * once the last one is resolved.
     */
    @PostMapping("/api/session/battle")
    public GameState sessionBattle(HttpServletRequest request, @RequestBody BattleResult result) {
        Player player = resolvePlayer(request);
        Session session = player.session();
        synchronized (gameService) {
            gameService.setGameState(session.getGameState());
            try {
                gameService.resolveBattle(result);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
            }
            save(player);
            return gameService.getState();
        }
    }

    /** Turns field battles on or off for the player's game: {@code {"enabled": true|false}}. */
    @PostMapping("/api/session/field-battles")
    public GameState sessionFieldBattles(HttpServletRequest request, @RequestBody Map<String, Boolean> body) {
        Boolean enabled = body == null ? null : body.get("enabled");
        if (enabled == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body needs 'enabled': true or false");
        }
        Player player = resolvePlayer(request);
        Session session = player.session();
        synchronized (gameService) {
            gameService.setGameState(session.getGameState());
            gameService.setFieldBattles(enabled);
            save(player);
            return gameService.getState();
        }
    }

    @PostMapping("/api/session/reset")
    public GameState sessionReset(HttpServletRequest request) {
        Player player = resolvePlayer(request);
        Session session = player.session();
        synchronized (gameService) {
            Boolean fieldBattles = session.getGameState() == null ? null : session.getGameState().getFieldBattles();
            gameService.resetGame();
            // A new game keeps the player's field-battle setting (on unless they turned it off).
            gameService.getGameStateInternal().setFieldBattles(fieldBattles == null ? Boolean.TRUE : fieldBattles);
            session.setGameState(gameService.getGameStateInternal());
            save(player);
            return gameService.getState();
        }
    }

    /**
     * The player's confirmed choice to start a fresh game when their saved game could not be read
     * (the 409 "unreadable-save" answer). The unreadable save is set aside unchanged first, then
     * the fresh game replaces it. If the save is readable this returns that game unchanged, so a
     * repeated click cannot wipe a game. Accounts only: guest games are kept per browser.
     */
    @PostMapping("/api/session/start-fresh")
    public GameState sessionStartFresh(HttpServletRequest request) {
        if ("guest".equalsIgnoreCase(request.getHeader(PLAYER_MODE_HEADER))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Only an account's saved game can be set aside. Use Reset Game for a guest game.");
        }
        Session session = sessionService.startFresh(authenticatedUsername(request));
        GameState state = session.getGameState();
        if (state != null && state.getFieldBattles() == null) {
            state.setFieldBattles(true);
        }
        synchronized (gameService) {
            gameService.setGameState(state);
            return gameService.getState();
        }
    }

    @PostMapping("/api/session/decision")
    public GameState sessionDecision(
            HttpServletRequest request,
            @RequestBody RulerDecision decision) {
        Player player = resolvePlayer(request);
        Session session = player.session();
        validateDecision(decision);
        synchronized (gameService) {
            gameService.setGameState(session.getGameState());
            try {
                gameService.changePolicy(decision.getCategory(), decision.getChoice());
                save(player);
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
    public RulerStats sessionRulerStats(HttpServletRequest request) {
        Player player = resolvePlayer(request);
        Session session = player.session();
        synchronized (gameService) {
            gameService.setGameState(session.getGameState());
            return gameService.getRulerStats();
        }
    }

    // Runs are recorded by SessionService as a side effect of save() (see sessionTick), so this
    // is a plain read with no need to touch the shared GameService/gameState.
    @GetMapping("/api/session/runs")
    public RunHistory sessionRuns(HttpServletRequest request) {
        Player player = resolvePlayer(request);
        Session session = player.session();
        return player.guest()
                ? guestSessionService.getRunHistory(session)
                : sessionService.getRunHistory(session.getUsername());
    }

    // Interface preferences are stored per account so a player's sidebar arrangement and display
    // settings follow them to another browser or device. They never touch the shared
    // GameService/gameState, so unlike the endpoints above these need no synchronization.
    @GetMapping("/api/session/preferences")
    public Map<String, Object> sessionPreferences(HttpServletRequest request) {
        Session session = accountOnly(request);
        return preferencesService.load(session.getUsername());
    }

    @PutMapping("/api/session/preferences")
    public Map<String, Object> saveSessionPreferences(
            HttpServletRequest request,
            @RequestBody Map<String, Object> preferences) {
        Session session = accountOnly(request);
        try {
            return preferencesService.save(session.getUsername(), preferences);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private Session accountOnly(HttpServletRequest request) {
        Player player = resolvePlayer(request);
        if (player.guest()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Guests' preferences stay in this browser. Create an account to keep them.");
        }
        return player.session();
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
