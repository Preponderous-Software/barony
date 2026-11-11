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
import org.springframework.test.web.servlet.ResultActions;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The TRAIN command and the unit mix in the state and in pending battles, through the session
 * endpoints with a real {@link GameService} and a guest session.
 */
@WebMvcTest(GameController.class)
@Import({AuthCookies.class, RefreshSessions.class, GuestCookies.class, GameService.class})
class GameControllerUnitTypesTest {

    private static final String GUEST_TOKEN = "c".repeat(43);

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
    private Army atVillage;

    @BeforeEach
    void guestWithAnArmyOnItsVillage() {
        GameState state = new GameState(6, 6);
        state.setAiEnabled(false);
        state.getGrid()[0][0].setType(TileType.CASTLE);
        state.getGrid()[0][0].setOwnerId(1);
        state.getGrid()[5][5].setType(TileType.CASTLE);
        state.getGrid()[5][5].setOwnerId(2);
        state.getGrid()[2][2].setType(TileType.VILLAGE);
        state.getGrid()[2][2].setOwnerId(1);
        state.getGrid()[2][2].setPopulation(0);
        atVillage = new Army(2, 2, 10, 1);
        state.getArmiesInternal().add(atVillage);
        state.getArmiesInternal().add(new Army(5, 5, 10, 2));
        session = new Session(null, state);
        when(guestSessionService.find(GUEST_TOKEN)).thenReturn(Optional.of(session));
    }

    private ResultActions command(String json) throws Exception {
        return mockMvc.perform(post("/api/session/command").cookie(new Cookie(GuestCookies.COOKIE_NAME, GUEST_TOKEN))
                .header("X-Barony-Player", "guest")
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void trainingArchersShowsTheOrderThenTheMix() throws Exception {
        command("{\"type\":\"TRAIN\",\"armyId\":" + atVillage.getId() + ",\"unitType\":\"ARCHERS\",\"count\":3}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.armies[0].trainingType").value("ARCHERS"))
                .andExpect(jsonPath("$.armies[0].trainingRemaining").value(3))
                .andExpect(jsonPath("$.armies[0].infantry").value(10));

        mockMvc.perform(post("/api/session/tick").cookie(new Cookie(GuestCookies.COOKIE_NAME, GUEST_TOKEN))
                        .header("X-Barony-Player", "guest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.armies[0].soldiers").value(10))
                .andExpect(jsonPath("$.armies[0].archers").value(2))
                .andExpect(jsonPath("$.armies[0].cavalry").value(0))
                .andExpect(jsonPath("$.armies[0].infantry").value(8))
                .andExpect(jsonPath("$.armies[0].trainingRemaining").value(1));
    }

    @Test
    void trainingInTheWrongPlaceIsAConflict() throws Exception {
        command("{\"type\":\"TRAIN\",\"armyId\":" + atVillage.getId() + ",\"unitType\":\"CAVALRY\",\"count\":1}")
                .andExpect(status().isConflict());
        assertFalse(atVillage.isTraining());
    }

    @Test
    void aBadTrainingRequestIsABadRequest() throws Exception {
        command("{\"type\":\"TRAIN\",\"armyId\":" + atVillage.getId() + ",\"unitType\":\"KNIGHTS\",\"count\":1}")
                .andExpect(status().isBadRequest());
        command("{\"type\":\"TRAIN\",\"armyId\":" + atVillage.getId() + ",\"unitType\":\"ARCHERS\",\"count\":11}")
                .andExpect(status().isBadRequest());
        command("{\"type\":\"TRAIN\",\"armyId\":424242,\"unitType\":\"ARCHERS\",\"count\":1}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void aPendingBattleCarriesEachSidesMix() throws Exception {
        session.getGameState().setFieldBattles(true);
        atVillage.setArchers(4);
        Army enemy = new Army(2, 2, 6, 2);
        enemy.setCavalry(2);
        session.getGameState().getArmiesInternal().add(enemy);
        mockMvc.perform(post("/api/session/tick").cookie(new Cookie(GuestCookies.COOKIE_NAME, GUEST_TOKEN))
                        .header("X-Barony-Player", "guest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingBattles[0].side0Soldiers").value(10))
                .andExpect(jsonPath("$.pendingBattles[0].side0Archers").value(4))
                .andExpect(jsonPath("$.pendingBattles[0].side0Cavalry").value(0))
                .andExpect(jsonPath("$.pendingBattles[0].side1Archers").value(0))
                .andExpect(jsonPath("$.pendingBattles[0].side1Cavalry").value(2));
        int id = session.getGameState().getPendingBattlesInternal().get(0).getId();

        mockMvc.perform(post("/api/session/battle").cookie(new Cookie(GuestCookies.COOKIE_NAME, GUEST_TOKEN))
                        .header("X-Barony-Player", "guest").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"battleId\":" + id + ",\"result\":\"FIELD\",\"winner\":0,\"survivors\":[7,0],"
                                + "\"survivorsByType\":[[3,4,0],[0,0,0]]}"))
                .andExpect(status().isOk());
        assertEquals(7, atVillage.getSoldiers());
        assertEquals(4, atVillage.getArchers());

    }

    @Test
    void survivorsByTypeThatDoNotAddUpAreABadRequest() throws Exception {
        session.getGameState().setFieldBattles(true);
        session.getGameState().getArmiesInternal().add(new Army(2, 2, 6, 2));
        mockMvc.perform(post("/api/session/tick").cookie(new Cookie(GuestCookies.COOKIE_NAME, GUEST_TOKEN))
                .header("X-Barony-Player", "guest"));
        int id = session.getGameState().getPendingBattlesInternal().get(0).getId();
        mockMvc.perform(post("/api/session/battle").cookie(new Cookie(GuestCookies.COOKIE_NAME, GUEST_TOKEN))
                        .header("X-Barony-Player", "guest").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"battleId\":" + id + ",\"result\":\"FIELD\",\"winner\":0,\"survivors\":[7,0],"
                                + "\"survivorsByType\":[[6,0,0],[0,0,0]]}"))
                .andExpect(status().isBadRequest());
        assertEquals(1, session.getGameState().getPendingBattlesInternal().size());
    }
}
