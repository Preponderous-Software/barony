package com.barony.webclient;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the game page ships the progression feedback the player relies on at the end of a run:
 * the end-of-game summary fields inside the game-over banner, and the wiring that fills them in and
 * announces milestones. The behaviour itself lives in the page's JavaScript, which has no test
 * runner here — these assertions guard against the markup or the calls being dropped, which would
 * silently leave the summary stuck on its placeholder values.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GamePageProgressionTest {

    private static final List<String> SUMMARY_FIELD_IDS = List.of(
            "summaryTurns", "summaryCastles", "summaryVillages", "summaryArmies", "summarySoldiers");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void gameOverBannerRendersEveryRunSummaryField() throws Exception {
        String html = renderGamePage();

        for (String id : SUMMARY_FIELD_IDS) {
            assertTrue(html.contains("id=\"" + id + "\""),
                    "Expected the game-over summary to include an element with id=\"" + id + "\"");
        }
    }

    @Test
    void updateUiFillsTheSummaryAndAnnouncesMilestones() throws Exception {
        String html = renderGamePage();

        assertTrue(html.contains("updateRunSummary(data)"),
                "updateUI must fill the run summary, or the banner shows placeholder values");
        assertTrue(html.contains("announceMilestones(previous, data)"),
                "updateUI must compare against the previous state to announce milestones");
    }

    @Test
    void gamePageLoadsTheTestedPureLogicScript() throws Exception {
        String html = renderGamePage();

        assertTrue(html.matches("(?s).*<script src=\"/js/game-logic(-[0-9a-fA-F]{8,})?\\.js\"></script>.*"),
                "Expected the game page to load /js/game-logic.js, whose behaviour is covered by "
                        + "web-client/src/test/js/game-logic.test.js");
    }

    private String renderGamePage() throws Exception {
        return mockMvc.perform(get("/game"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
