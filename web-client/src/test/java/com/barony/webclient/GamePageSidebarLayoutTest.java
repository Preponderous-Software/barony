package com.barony.webclient;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the sidebar markup the layout feature (#55) depends on: the panels the player can
 * arrange, and the controls that arrange them. Markup order matters here — it is the default the
 * page reads into DEFAULT_PANEL_ORDER and the order "Reset Panel Layout" restores, it is what
 * PLAYER_GUIDE.md documents, and it is the keyboard tab order now that panels are moved in the DOM
 * rather than by CSS `order`. The arranging itself lives in the page's JavaScript, whose pure
 * rules are covered by the Node suite; these assertions guard the markup those rules act on.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GamePageSidebarLayoutTest {

    private static final List<String> DEFAULT_PANEL_ORDER = List.of(
            "status", "run-history", "armies", "policy", "settings");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void sidebarPanelsAppearInTheDocumentedDefaultOrder() throws Exception {
        String html = renderGamePage();

        int searchFrom = 0;
        for (String panelId : DEFAULT_PANEL_ORDER) {
            String marker = "data-panel-id=\"" + panelId + "\"";
            int at = html.indexOf(marker, searchFrom);
            assertTrue(at >= 0,
                    "Expected panel " + marker + " after the panels preceding it in "
                            + DEFAULT_PANEL_ORDER + ", but it was missing or out of order");
            searchFrom = at + marker.length();
        }
    }

    @Test
    void theSidebarHoldsNoPanelBeyondTheOnesThatCanBeArranged() throws Exception {
        String html = renderGamePage();

        // A panel without a data-panel-id would be left out of the layout controls entirely, so it
        // could be neither reordered nor restored once another panel was moved on top of it.
        assertEquals(DEFAULT_PANEL_ORDER.size(),
                countOccurrences(html, "<details class=\"collapsible-section"),
                "Every collapsible sidebar panel is expected to be one of " + DEFAULT_PANEL_ORDER
                        + ", each carrying a data-panel-id");
    }

    @Test
    void settingsPanelHostsTheLayoutControls() throws Exception {
        String html = renderGamePage();

        assertTrue(html.contains("id=\"layoutControls\""),
                "Expected the container renderLayoutControls() builds the per-panel rows into");
        assertTrue(html.contains("resetPanelLayout()"),
                "Expected a Reset Panel Layout control wired to resetPanelLayout()");
    }

    private int countOccurrences(String html, String needle) {
        int count = 0;
        for (int at = html.indexOf(needle); at >= 0; at = html.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
    }

    private String renderGamePage() throws Exception {
        return mockMvc.perform(get("/game"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
