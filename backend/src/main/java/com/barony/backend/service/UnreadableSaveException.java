package com.barony.backend.service;

/**
 * A player's saved game exists but cannot be read. The save is left untouched; the player is told
 * and can choose to start a fresh game ({@link SessionService#startFresh}), which sets the
 * unreadable save aside first. Never carries the save's text.
 */
public class UnreadableSaveException extends RuntimeException {

    private final String username;

    public UnreadableSaveException(String username) {
        super("The saved game for '" + username + "' could not be read and has been kept unchanged");
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}
