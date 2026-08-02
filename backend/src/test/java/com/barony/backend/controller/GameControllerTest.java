package com.barony.backend.controller;

import com.barony.backend.model.GameState;
import com.barony.backend.model.RulerStats;
import com.barony.backend.service.AuthCookies;
import com.barony.backend.service.GameService;
import com.barony.backend.service.SessionService;
import com.barony.backend.service.UserAuthClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the single-game (unauthenticated, shared-state) endpoints, which {@link GameControllerAuthTest}
 * does not exercise. In particular locks in the {@code /api/decision} exception-to-HTTP-status mapping,
 * which has no other test coverage at the controller layer.
 */
@WebMvcTest(GameController.class)
@Import(AuthCookies.class)
class GameControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GameService gameService;

    @MockBean
    private SessionService sessionService;

    @MockBean
    private UserAuthClient userAuthClient;

    @Test
    void getStateReturnsCurrentGameServiceState() throws Exception {
        GameState state = new GameState(5, 5);
        state.setTickCount(7);
        when(gameService.getState()).thenReturn(state);

        mockMvc.perform(get("/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickCount").value(7));

        verifyNoInteractions(userAuthClient, sessionService);
    }

    @Test
    void tickAdvancesGameServiceThenReturnsState() throws Exception {
        GameState state = new GameState(5, 5);
        when(gameService.getState()).thenReturn(state);

        mockMvc.perform(post("/tick"))
                .andExpect(status().isOk());

        verify(gameService, times(1)).tick();
        verify(gameService, times(1)).getState();
    }

    @Test
    void commandExecutesParsedCommandAndReturnsState() throws Exception {
        GameState state = new GameState(5, 5);
        when(gameService.getState()).thenReturn(state);

        mockMvc.perform(post("/command")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"MOVE\",\"armyId\":1,\"targetX\":2,\"targetY\":3}"))
                .andExpect(status().isOk());

        verify(gameService).executeCommand(org.mockito.ArgumentMatchers.argThat(c ->
                "MOVE".equals(c.getType()) && c.getArmyId() == 1 && c.getTargetX() == 2 && c.getTargetY() == 3));
    }

    @Test
    void resetResetsGameServiceThenReturnsState() throws Exception {
        GameState state = new GameState(5, 5);
        when(gameService.getState()).thenReturn(state);

        mockMvc.perform(post("/api/reset"))
                .andExpect(status().isOk());

        verify(gameService, times(1)).resetGame();
    }

    @Test
    void rulerStatsReturnsGameServiceStats() throws Exception {
        RulerStats stats = new RulerStats();
        stats.setAverageStability(50.0);
        stats.setEconomicPolicy("BALANCED_BUDGET");
        when(gameService.getRulerStats()).thenReturn(stats);

        mockMvc.perform(get("/api/ruler-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageStability").value(50.0))
                .andExpect(jsonPath("$.economicPolicy").value("BALANCED_BUDGET"));
    }

    @Test
    void decisionRejectsMissingBody() throws Exception {
        mockMvc.perform(post("/api/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(gameService);
    }

    @Test
    void decisionRejectsMissingCategory() throws Exception {
        mockMvc.perform(post("/api/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"choice\":\"BALANCED_BUDGET\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(gameService);
    }

    @Test
    void decisionRejectsMissingChoice() throws Exception {
        mockMvc.perform(post("/api/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"ECONOMIC\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(gameService);
    }

    @Test
    void decisionAppliesValidChoiceAndReturnsState() throws Exception {
        GameState state = new GameState(5, 5);
        when(gameService.getState()).thenReturn(state);

        mockMvc.perform(post("/api/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"ECONOMIC\",\"choice\":\"BALANCED_BUDGET\"}"))
                .andExpect(status().isOk());

        verify(gameService).changePolicy(
                com.barony.backend.model.RulerDecision.PolicyCategory.ECONOMIC, "BALANCED_BUDGET");
    }

    @Test
    void decisionOnCooldownReturns409() throws Exception {
        doThrow(new IllegalStateException("2 ticks remaining"))
                .when(gameService).changePolicy(
                        com.barony.backend.model.RulerDecision.PolicyCategory.ECONOMIC, "BALANCED_BUDGET");

        mockMvc.perform(post("/api/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"ECONOMIC\",\"choice\":\"BALANCED_BUDGET\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void decisionWithInvalidChoiceReturns400() throws Exception {
        doThrow(new IllegalArgumentException("no such policy"))
                .when(gameService).changePolicy(
                        com.barony.backend.model.RulerDecision.PolicyCategory.ECONOMIC, "NOT_A_POLICY");

        mockMvc.perform(post("/api/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"ECONOMIC\",\"choice\":\"NOT_A_POLICY\"}"))
                .andExpect(status().isBadRequest());
    }
}
