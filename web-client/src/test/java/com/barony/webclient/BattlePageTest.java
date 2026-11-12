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
 * The 3D battlefield page (/battle) and the way into it from the game page. The battle's own
 * behaviour is covered by web-client/src/test/js/battle-*.test.js; what is guarded here is the
 * wiring: the page is served, it loads the engine and the scripts it is built from, and the game
 * page's selected-army panel links to it with the selected army.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BattlePageTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void battlePageIsServedWithTheEngineAndItsScripts() throws Exception {
        String html = mockMvc.perform(get("/battle"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        for (String script : new String[] {"strata-loader", "strata-kit", "battle-ground", "battle-sim", "battle-render",
                "battle-avatar", "battle-page"}) {
            assertTrue(html.contains(script), "the battle page must load " + script);
        }
        assertTrue(html.contains("data-engine-js=") && html.contains("battle-engine"),
                "the battle page must tell the loader where the engine is");
        assertTrue(html.contains("id=\"view\""), "the battle page must have the canvas the engine draws into");
    }

    @Test
    void engineFilesAreServed() throws Exception {
        mockMvc.perform(get("/battle3d/battle-engine.wasm")).andExpect(status().isOk());
        mockMvc.perform(get("/battle3d/battle-engine.js")).andExpect(status().isOk());
    }

    @Test
    void gamePageLinksTheSelectedArmyToTheBattlefield() throws Exception {
        String html = mockMvc.perform(get("/game"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("id=\"panelBattleLink\""), "the selected-army panel must offer the 3D battle");
        assertTrue(html.contains("'/battle?army=' + encodeURIComponent(army.id)"),
                "the battle link must carry the selected army, so the battle loads its real numbers");
        assertTrue(html.contains("Practice in 3D"),
                "the selected army's own battle is practice and must say so (#144)");
        assertTrue(html.contains("(b.side0ArmyIds || []).includes(armyId)")
                        && html.contains("battleLink.href = '/battle?pending=' + encodeURIComponent(pending.id)"),
                "an army in a waiting battle must link to that battle, the one that counts (#144)");
    }

    @Test
    void aTurnThatStartsABattleTakesThePlayerToTheBattlefield() throws Exception {
        String html = mockMvc.perform(get("/game"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("'/battle?pending=' + encodeURIComponent(battle.id)"),
                "a tick that leaves a pending battle must open it on the battlefield");
        assertTrue(html.contains("data.fieldBattles !== false"), "only while field battles are on");
        assertTrue(html.contains("id=\"battleBanner\""), "a waiting battle must be shown over the map");
        assertTrue(html.contains("result: 'AUTO'"), "the map must offer to decide the battle itself");
        assertTrue(html.contains("id=\"fieldBattlesToggle\"") && html.contains("/api/session/field-battles"),
                "field battles must be a setting the player can turn off");
    }

    @Test
    void theBattlefieldCanLetTheMapDecideAPendingBattle() throws Exception {
        String html = mockMvc.perform(get("/battle"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("id=\"autoBtn\""));
    }
}
