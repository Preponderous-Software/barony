package com.barony.backend.service;

/**
 * A player's saved game exists but cannot be read. The save is left untouched; the player is told
 * and can choose to start a fresh game ({@link SessionService#startFresh} for an account,
 * {@link GuestSessionService#startFresh} for a guest), which sets the unreadable save aside first.
 * Never carries the save's text. For a guest the "username" is a label with only the guest key's
 * first 8 characters.
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
