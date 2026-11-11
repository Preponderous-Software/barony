package com.barony.backend.controller;

import com.barony.backend.model.Army;
import com.barony.backend.model.GameState;
import com.barony.backend.model.Session;
import com.barony.backend.model.TileType;
import com.barony.backend.service.AuthCookies;
import com.barony.backend.service.GameService;
import com.barony.backend.service.GuestCookies;
import com.barony.backend.service.GuestSessionService;
import com.barony.backend.service.PreferencesService;
import com.barony.backend.service.RefreshSessions;
import com.barony.backend.service.SessionService;
import com.barony.backend.service.UserAuthClient;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The field-battle endpoints, with a real {@link GameService} and a guest session holding a game
 * in which the player's army and the enemy's stand on one tile.
 */
@WebMvcTest(GameController.class)
@Import({AuthCookies.class, RefreshSessions.class, GuestCookies.class, GameService.class})
class GameControllerFieldBattleTest {

    private static final String GUEST_TOKEN = "b".repeat(43);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SessionService sessionService;

    @MockBean
    private UserAuthClient userAuthClient;

    @MockBean
    private PreferencesService preferencesService;

    @MockBean
    private GuestSessionService guestSessionService;

    private Session session;

    @BeforeEach
    void guestWithAnEngagement() {
        GameState state = new GameState(6, 6);
        state.setAiEnabled(false);
        state.getGrid()[0][0].setType(TileType.CASTLE);
        state.getGrid()[0][0].setOwnerId(1);
        state.getGrid()[5][5].setType(TileType.CASTLE);
        state.getGrid()[5][5].setOwnerId(2);
        state.getArmiesInternal().add(new Army(2, 2, 30, 1));
        state.getArmiesInternal().add(new Army(2, 2, 20, 2));
        // fieldBattles left null, as in a save from before field battles existed
        session = new Session(null, state);
        when(guestSessionService.find(GUEST_TOKEN)).thenReturn(Optional.of(session));
    }

    private static Cookie guest() {
        return new Cookie(GuestCookies.COOKIE_NAME, GUEST_TOKEN);
    }

    @Test
    void fieldBattlesAreOnByDefaultForAPlayerAndATickPausesForTheBattle() throws Exception {
        mockMvc.perform(get("/api/session/state").cookie(guest()).header("X-Barony-Player", "guest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fieldBattles").value(true));

        mockMvc.perform(post("/api/session/tick").cookie(guest()).header("X-Barony-Player", "guest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingBattles.length()").value(1))
                .andExpect(jsonPath("$.pendingBattles[0].side0Soldiers").value(30))
                .andExpect(jsonPath("$.pendingBattles[0].side1Soldiers").value(20));
        verify(guestSessionService, atLeastOnce()).save(session);
    }

    @Test
    void aFieldResultDecidesTheBattle() throws Exception {
        mockMvc.perform(post("/api/session/tick").cookie(guest()).header("X-Barony-Player", "guest"));
        int id = session.getGameState().getPendingBattlesInternal().get(0).getId();

        mockMvc.perform(post("/api/session/battle").cookie(guest()).header("X-Barony-Player", "guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"battleId\":" + id + ",\"result\":\"FIELD\",\"winner\":0,\"survivors\":[25,4]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingBattles.length()").value(0));

        assertEquals(25, session.getGameState().getArmiesInternal().stream()
                .filter(a -> a.getPlayerId() == 1).mapToInt(Army::getSoldiers).sum());
    }

    @Test
    void anImpossibleResultIsABadRequest() throws Exception {
        mockMvc.perform(post("/api/session/tick").cookie(guest()).header("X-Barony-Player", "guest"));
        int id = session.getGameState().getPendingBattlesInternal().get(0).getId();

        mockMvc.perform(post("/api/session/battle").cookie(guest()).header("X-Barony-Player", "guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"battleId\":" + id + ",\"result\":\"FIELD\",\"winner\":0,\"survivors\":[99,0]}"))
                .andExpect(status().isBadRequest());
        assertEquals(1, session.getGameState().getPendingBattlesInternal().size());
    }

    @Test
    void splittingAnArmyInAPendingBattleIsAConflict() throws Exception {
        mockMvc.perform(post("/api/session/tick").cookie(guest()).header("X-Barony-Player", "guest"));
        int armyId = session.getGameState().getPendingBattlesInternal().get(0).getSide0ArmyIds().get(0);
        mockMvc.perform(post("/api/session/command").cookie(guest()).header("X-Barony-Player", "guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SPLIT\",\"armyId\":" + armyId + ",\"splitAmount\":5}"))
                .andExpect(status().isConflict());
    }

    @Test
    void thePlayerCanTurnFieldBattlesOffAndAResetKeepsTheirChoice() throws Exception {
        mockMvc.perform(post("/api/session/field-battles").cookie(guest()).header("X-Barony-Player", "guest")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fieldBattles").value(false));

        mockMvc.perform(post("/api/session/reset").cookie(guest()).header("X-Barony-Player", "guest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fieldBattles").value(false));

        mockMvc.perform(post("/api/session/field-battles").cookie(guest()).header("X-Barony-Player", "guest")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
