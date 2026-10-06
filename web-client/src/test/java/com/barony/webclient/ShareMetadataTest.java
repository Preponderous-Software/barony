package com.barony.webclient;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The login page is where barony.preponderous.org lands ("/" redirects to it), so it carries the
 * search and link-preview metadata. Every absolute URL must name the production origin.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ShareMetadataTest {

    private static final String ORIGIN = "https://barony.preponderous.org";
    private static final String OG_IMAGE = "/images/og.png";

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
        assertTrue(html.contains("<meta property=\"og:image\" content=\"" + ORIGIN + OG_IMAGE + "\">"), "Expected og:image");
        assertTrue(html.contains("<meta property=\"og:image:type\" content=\"image/png\">"), "Expected og:image:type");
        assertTrue(html.contains("<meta property=\"og:image:width\" content=\"1200\">"), "Expected og:image:width");
        assertTrue(html.contains("<meta property=\"og:image:height\" content=\"630\">"), "Expected og:image:height");
        assertTrue(html.contains("<meta property=\"og:image:alt\" content=\"Barony is a free"), "Expected og:image:alt");
        assertTrue(html.contains("<meta name=\"twitter:card\" content=\"summary_large_image\">"), "Expected twitter:card");
        assertTrue(html.contains("<meta name=\"twitter:image\" content=\"" + ORIGIN + OG_IMAGE + "\">"), "Expected twitter:image");
        assertFalse(html.contains("localhost"), "No absolute URL may point at localhost");
    }

    @Test
    void shareImageIsServedAsA1200x630Png() throws Exception {
        byte[] png = mockMvc.perform(get(OG_IMAGE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn().getResponse().getContentAsByteArray();

        assertArrayEquals(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'},
                Arrays.copyOf(png, 8), "Expected a PNG signature");
        // IHDR follows the signature: big-endian width and height at bytes 16 and 20.
        ByteBuffer header = ByteBuffer.wrap(png, 16, 8);
        assertEquals(1200, header.getInt(), "og:image:width says 1200");
        assertEquals(630, header.getInt(), "og:image:height says 630");
    }

    @Test
    void registerPageHasTitleAndDescription() throws Exception {
        String html = render("/register");

        assertTrue(html.contains("<title>Barony — create an account</title>"), "Expected the register title");
        assertTrue(html.contains("<meta name=\"description\" content=\""), "Expected a meta description");
        assertTrue(html.contains("<link rel=\"canonical\" href=\"" + ORIGIN + "/register\">"), "Expected the canonical link");
    }
}
