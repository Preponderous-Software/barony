package com.barony.webclient;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every page carries the shared footer fragment with the backlink to danielstephenson.dev,
 * so the login and register screens show it as well as the game itself.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SiteFooterTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {"/login", "/register", "/game"})
    void pageRendersTheSiteFooterBacklink(String path) throws Exception {
        String html = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("<footer class=\"site-footer\">"),
                "Expected the shared site footer on " + path);
        assertTrue(html.contains("More by Daniel Stephenson → <a href=\"https://danielstephenson.dev\">danielstephenson.dev</a>"),
                "Expected the danielstephenson.dev backlink on " + path);
    }
}
