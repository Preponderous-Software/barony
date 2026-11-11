package com.barony.webclient;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The game page's answer to an account save that cannot be read (the backend's 409
 * "unreadable-save"): a message that the save was kept, with a "Start a fresh game" button wired to
 * the confirmed fresh start, hidden until the state load reports it. The decision logic
 * (isUnreadableSave, readUnreadableSave, startFreshGame) is covered by game-logic.test.js; this
 * guards the markup and wiring that use it.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GamePageUnreadableSaveTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void theMessageAndFreshStartButtonAreThereButHiddenUntilNeeded() throws Exception {
        String html = render("/game");

        assertTrue(html.contains("id=\"unreadableSave\" class=\"unreadable-save\" role=\"alert\" style=\"display: none;\""),
                "Expected a hidden unreadable-save panel");
        assertTrue(html.contains("Your saved game couldn't be read"), "Expected the panel to say what happened");
        assertTrue(html.contains("It has been kept"), "Expected the panel to say the save was kept");
        assertTrue(html.contains("onclick=\"confirmStartFresh()\">Start a fresh game</button>"),
                "Expected a Start a fresh game button wired to the confirmed fresh start");
    }

    @Test
    void theStateLoadShowsThePanelOnAnUnreadableSave() throws Exception {
        String html = render("/game");

        assertTrue(html.contains("readUnreadableSave(response)"),
                "Expected the state load to check a 409 for the unreadable-save answer");
        assertTrue(html.contains("showUnreadableSave();"), "Expected the panel to be shown on that answer");
        assertTrue(html.contains("startFreshGame({"), "Expected the button to go through startFreshGame");
    }

    @Test
    void theBattlefieldSendsThePlayerBackToTheMap() throws Exception {
        String script = render("/js/battle-page.js");

        assertTrue(script.contains("body.error === 'unreadable-save'"),
                "Expected the battlefield to recognise the unreadable-save answer");
        assertTrue(script.contains("back.href = '/game'"),
                "Expected the battlefield to link back to the map, which offers the fresh start");
    }

    private String render(String path) throws Exception {
        return mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
