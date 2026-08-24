package com.barony.webclient;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the game page draws armies and builds tooltips through the pure logic that is actually
 * tested, rather than through a copy of its own. Canvas drawing has no test runner here, so what is
 * guarded is the wiring: the placements from {@code layoutArmiesOnTiles} being applied rather than
 * computed and ignored (which would put co-located armies back on top of one another), and the
 * tooltip text coming from the shared {@code getTooltipText} (which is what carries a castle's
 * capture progress while an army stands on it). Both behaviours themselves are covered by
 * web-client/src/test/js/game-logic.test.js.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GamePageArmyRenderingTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void armiesAreDrawnFromTheSharedPlacementRule() throws Exception {
        String html = renderGamePage();

        assertTrue(html.contains("layoutArmiesOnTiles(gameState.armies)"),
                "drawMap must place armies through layoutArmiesOnTiles, or armies sharing a tile "
                        + "are drawn on top of one another again");
        assertFalse(html.contains("gameState.armies.forEach"),
                "drawMap must not iterate the raw army list, which carries no placement");
    }

    @Test
    void drawnArmyPositionsApplyTheirOffsets() throws Exception {
        String html = renderGamePage();

        assertTrue(html.contains("placement.offsetX") && html.contains("placement.offsetY"),
                "The drawn position must apply each placement's offset; computing the fan and then "
                        + "drawing at the cell centre would hide co-located armies just as before");
    }

    @Test
    void tooltipTextComesFromTheSharedPureFunction() throws Exception {
        String html = renderGamePage();

        assertTrue(html.contains("getTooltipText(gridX, gridY, gameState, CASTLE_CAPTURE_TURNS)"),
                "The page must build tooltip text with the tested getTooltipText, passing the "
                        + "capture requirement it owns");
    }

    private String renderGamePage() throws Exception {
        return mockMvc.perform(get("/game"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
