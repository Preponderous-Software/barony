package com.barony.backend.controller;

import com.barony.backend.service.UnreadableSaveException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Answers any request that needed a player's saved game when that save cannot be read: 409 with
 * {@code {"error": "unreadable-save", "message": ..., "status": 409}}. The game page recognises it
 * and shows a message with a "Start a fresh game" button (POST /api/session/start-fresh) instead of
 * a fresh game.
 */
@RestControllerAdvice
public class UnreadableSaveHandler {

    public static final String ERROR_CODE = "unreadable-save";

    static final String MESSAGE = "Your saved game could not be read. It has been kept exactly as it was, "
            + "not replaced. You can start a fresh game; the unreadable save is set aside first, not deleted.";

    @ExceptionHandler(UnreadableSaveException.class)
    public ResponseEntity<Map<String, Object>> unreadableSave(UnreadableSaveException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ERROR_CODE);
        body.put("message", MESSAGE);
        body.put("status", HttpStatus.CONFLICT.value());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}
