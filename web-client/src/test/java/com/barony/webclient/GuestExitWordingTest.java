package com.barony.webclient;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Exit guest game" sends the player to the login page with a notice telling them which button
 * resumes their game. By then the browser has a guest game, so login.html relabels the guest button
 * "Continue guest game"; the notice must name that label, not the first-visit "Play as guest", or it
 * points at a button the player cannot see. The label lives in login.html and the notice in
 * game.html, so this test reads both pages and keeps them in step.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GuestExitWordingTest {

    private static final Pattern NOTICE = Pattern.compile(
            "Your guest game is saved in this browser\\. Choose \"([^\"]+)\" to carry on\\.");
    private static final Pattern RESUME_LABEL = Pattern.compile(
            "getElementById\\('guestButton'\\)\\.textContent = '([^']+)'");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exitNoticeNamesTheLabelTheGuestButtonShowsWhenAGuestGameExists() throws Exception {
        Matcher notice = NOTICE.matcher(render("/game"));
        assertTrue(notice.find(), "game.html must leave an exit-guest notice for the login page");

        Matcher label = RESUME_LABEL.matcher(render("/login"));
        assertTrue(label.find(), "login.html must relabel the guest button when a guest game exists");

        assertEquals(label.group(1), notice.group(1),
                "The exit-guest notice must name the guest button's resume label");
        assertEquals("Continue guest game", notice.group(1));
    }

    @Test
    void exitNoticeNoLongerNamesTheFirstVisitLabel() throws Exception {
        assertFalse(render("/game").contains("Choose \"Play as guest\""),
                "After exiting, the button reads \"Continue guest game\", not \"Play as guest\"");
    }

    private String render(String path) throws Exception {
        return mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
