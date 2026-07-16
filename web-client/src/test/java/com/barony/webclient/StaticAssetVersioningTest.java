package com.barony.webclient;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that static assets are served with content-hash versioning so a changed stylesheet
 * gets a new URL and browsers don't serve a stale cached copy after a deploy. Without this, a CSS
 * fix (e.g. styling the password field) would not reach players whose browser cached the old file.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StaticAssetVersioningTest {

    // e.g. /css/style-0a1b2c3d....css  (not the bare /css/style.css)
    private static final Pattern VERSIONED = Pattern.compile("/css/style-[0-9a-fA-F]{8,}\\.css");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginPageLinksContentHashedStylesheet() throws Exception {
        String html = mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(VERSIONED.matcher(html).find(),
                "Expected a content-hashed stylesheet link (/css/style-<hash>.css) but got:\n" + html);
        assertTrue(!html.contains("\"/css/style.css\""),
                "Stylesheet should be linked via its versioned URL, not the bare /css/style.css");
    }
}
