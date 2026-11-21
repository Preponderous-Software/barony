package com.barony.webclient;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the game page's 3D map is wired as map3d-view.js expects: Strata's grid relay page
 * scripts and the scene's own scripts are linked through content-hashed URLs, the relay's module
 * (which is fetched only when the 3D map is wanted) is named by hashed URLs the relay is handed and served,
 * the 3D map's canvas sits inside the same box as the 2D map (so the tooltip and the game-over
 * banner cover both), and the 2D/3D switch is there. What the 3D map does is covered by the Node tests
 * (web-client/src/test/js/map3d-*.test.js) and the browser run in web-client/e2e/map3d.cjs.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GamePageMap3DTest {

    @Autowired
    private MockMvc mockMvc;

    private String renderGamePage() throws Exception {
        return mockMvc.perform(get("/game"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void relayAndSceneScriptsAreLinkedWithContentHashes() throws Exception {
        String html = renderGamePage();
        for (String script : new String[] {"/map3d/strata-loader", "/map3d/strata-kit", "/map3d/grid-relay",
                "/js/grid-wire", "/js/map3d-scene", "/js/map3d-view"}) {
            assertTrue(Pattern.compile("<script src=\"" + Pattern.quote(script) + "-[0-9a-f]{8,}\\.js\"></script>")
                            .matcher(html).find(),
                    "Expected " + script + ".js to be linked through its content-hashed URL");
        }
        // The page scripts come after game-logic.js, whose globals map3d-scene.js reads.
        assertTrue(html.indexOf("/js/game-logic-") < html.indexOf("/js/map3d-scene-"),
                "map3d-scene.js reads game-logic.js's globals, so it must load after it");
    }

    @Test
    void relayModuleIsNamedByHashedUrlsAndServed() throws Exception {
        String html = renderGamePage();
        Matcher assets = Pattern.compile(
                "id=\"map3dAssets\" hidden data-script=\"(/map3d/grid-relay-scene-[0-9a-f]{8,}\\.js)\" "
                        + "data-wasm=\"(/map3d/grid-relay-scene-[0-9a-f]{8,}\\.wasm)\"").matcher(html);
        assertTrue(assets.find(), "Expected the relay module's hashed URLs on #map3dAssets");
        // Served (its application/wasm type comes from the servlet container's MIME map, which the
        // mock servlet context does not have; Tomcat names it, as for the battlefield's engine).
        mockMvc.perform(get(assets.group(2)))
                .andExpect(status().isOk());
        mockMvc.perform(get(assets.group(1)))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Length"));
    }

    @Test
    void the3DMapSharesTheMapBoxAndHasItsSwitch() throws Exception {
        String html = renderGamePage();
        int box = html.indexOf("id=\"gameCanvas\"");
        int canvas2d = html.indexOf("id=\"mapCanvas\"", box);
        int stage = html.indexOf("id=\"map3dStage\"", box);
        int relay = html.indexOf("id=\"mapRelay\"", stage);
        int tooltip = html.indexOf("id=\"canvasTooltip\"", box);
        int banner = html.indexOf("id=\"gameOverBanner\"", box);
        assertTrue(box > 0 && canvas2d > box && stage > canvas2d && relay > stage && tooltip > relay && banner > tooltip,
                "Expected the 3D stage beside the 2D canvas inside #gameCanvas, before the tooltip and the game-over banner");
        assertTrue(html.contains("id=\"mapView3d\"") && html.contains("id=\"mapView2d\""),
                "Expected the 2D/3D switch");
        assertTrue(html.contains("onclick=\"setMapView(true)\"") && html.contains("onclick=\"setMapView(false)\""),
                "Expected the switch to go through setMapView");
        assertTrue(html.contains("id=\"mapViewNote\" class=\"map-view-note\" role=\"status\""),
                "Expected a status note for loading and for the 2D fallback's reason");
        assertTrue(html.contains("Map3DView.create({"), "Expected the page to create the 3D map");
        assertTrue(html.contains("onPress: handleMapPress") && html.contains("onDeselect: deselectArmy"),
                "Expected 3D presses to go through the 2D map's own handlers");
    }

    @Test
    void both3DCameraButtonsAndPageKeysAreThere() throws Exception {
        String html = renderGamePage();
        Matcher buttons = Pattern.compile("onclick=\"mapCamera\\('([a-z-]+)'\\)\"").matcher(html);
        int count = 0;
        while (buttons.find()) count++;
        assertEquals(7, count, "Expected seven camera buttons (turn, tilt, zoom, whole map)");
        assertTrue(html.contains("MAP_CAMERA_KEYS[e.key]"), "Expected the camera keys in the page's key handler");
    }
}
