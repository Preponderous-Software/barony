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
 * Verifies the game page ships the Run History panel (#70): the markup the panel renders into,
 * and the wiring that fetches and refreshes it. The rendering logic itself lives in the page's
 * JavaScript, which has no test runner here — these assertions guard against the markup or the
 * calls being dropped, which would silently leave the panel stuck on its placeholder values.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GamePageRunHistoryTest {

    private static final List<String> RUN_HISTORY_FIELD_IDS = List.of(
            "runHistoryWins", "runHistoryLosses", "runHistoryList");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void runHistoryPanelRendersEveryField() throws Exception {
        String html = renderGamePage();

        for (String id : RUN_HISTORY_FIELD_IDS) {
            assertTrue(html.contains("id=\"" + id + "\""),
                    "Expected the Run History panel to include an element with id=\"" + id + "\"");
        }
    }

    @Test
    void runHistoryIsFetchedOnLoadAndAfterEachFinishedRun() throws Exception {
        String html = renderGamePage();

        assertTrue(html.contains("fetch('/api/session/runs'"),
                "Expected the page to fetch /api/session/runs, the endpoint that serves run history");
        assertTrue(html.contains("loadRunHistory();"),
                "Expected loadRunHistory() to be called, both on page load and when a run just finished");
    }

    private String renderGamePage() throws Exception {
        return mockMvc.perform(get("/game"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
