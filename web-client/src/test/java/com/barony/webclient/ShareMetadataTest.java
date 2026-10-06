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
 * The login page is where barony.preponderous.org lands ("/" redirects to it), so it carries the
 * search and link-preview metadata. Every absolute URL must name the production origin.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ShareMetadataTest {

    private static final String ORIGIN = "https://barony.preponderous.org";

    @Autowired
    private MockMvc mockMvc;

    private String render(String path) throws Exception {
        return mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void landingPageHasTitleDescriptionAndOpenGraphTags() throws Exception {
        String html = render("/login");

        assertTrue(html.contains("<title>Barony</title>"), "Expected the plain Barony title");
        assertTrue(html.contains("<meta name=\"description\" content=\""), "Expected a meta description");
        assertTrue(html.contains("<link rel=\"canonical\" href=\"" + ORIGIN + "/\">"), "Expected the canonical link");
        assertTrue(html.contains("<meta property=\"og:type\" content=\"website\">"), "Expected og:type");
        assertTrue(html.contains("<meta property=\"og:title\" content=\"Barony\">"), "Expected og:title");
        assertTrue(html.contains("<meta property=\"og:description\" content=\""), "Expected og:description");
        assertTrue(html.contains("<meta property=\"og:url\" content=\"" + ORIGIN + "/\">"), "Expected og:url");
        assertTrue(html.contains("<meta name=\"twitter:card\" content=\"summary\">"), "Expected twitter:card");
        assertFalse(html.contains("localhost"), "No absolute URL may point at localhost");
    }

    @Test
    void registerPageHasTitleAndDescription() throws Exception {
        String html = render("/register");

        assertTrue(html.contains("<title>Barony — create an account</title>"), "Expected the register title");
        assertTrue(html.contains("<meta name=\"description\" content=\""), "Expected a meta description");
        assertTrue(html.contains("<link rel=\"canonical\" href=\"" + ORIGIN + "/register\">"), "Expected the canonical link");
    }
}
