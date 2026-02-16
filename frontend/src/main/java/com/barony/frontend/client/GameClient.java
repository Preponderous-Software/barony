package com.barony.frontend.client;

import com.barony.frontend.model.Command;
import com.barony.frontend.model.GameState;
import com.barony.frontend.model.RulerDecision;
import com.barony.frontend.model.RulerStats;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class GameClient {
    private final String baseUrl;
    private final Gson gson;
    private static final int CONNECT_TIMEOUT = 5000; // 5 seconds
    private static final int READ_TIMEOUT = 10000; // 10 seconds
    
    public GameClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.gson = new Gson();
    }
    
    public GameState getState() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(baseUrl + "/state");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            
            return readResponse(conn);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
    
    public GameState tick() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(baseUrl + "/tick");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            
            return readResponse(conn);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
    
    public GameState sendCommand(Command command) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(baseUrl + "/command");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setDoOutput(true);
            
            String jsonCommand = gson.toJson(command);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonCommand.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            
            return readResponse(conn);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
    
    public GameState reset() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(baseUrl + "/api/reset");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            
            return readResponse(conn);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
    
    public RulerStats getRulerStats() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(baseUrl + "/api/ruler-stats");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            
            return readRulerStatsResponse(conn);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
    
    public GameState changePolicy(String category, String choice) {
        HttpURLConnection conn = null;
        try {
            // Validate category before creating enum
            RulerDecision.PolicyCategory policyCategory;
            try {
                policyCategory = RulerDecision.PolicyCategory.valueOf(category);
            } catch (IllegalArgumentException e) {
                System.err.println("Invalid policy category: " + category);
                return null;
            }
            
            URL url = new URL(baseUrl + "/api/decision");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setDoOutput(true);
            
            // Create RulerDecision object
            RulerDecision decision = new RulerDecision(policyCategory, choice);
            
            // Serialize to JSON
            String jsonRequest = gson.toJson(decision);
            
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonRequest.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            
            return readResponse(conn);
        } catch (Exception e) {
            System.err.println("Failed to change policy: " + e.getMessage());
            e.printStackTrace();
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
    
    private GameState readResponse(HttpURLConnection conn) throws Exception {
        return readJsonResponse(conn, GameState.class);
    }
    
    private RulerStats readRulerStatsResponse(HttpURLConnection conn) throws Exception {
        return readJsonResponse(conn, RulerStats.class);
    }
    
    private <T> T readJsonResponse(HttpURLConnection conn, Class<T> responseType) throws Exception {
        int responseCode = conn.getResponseCode();
        InputStream inputStream;
        
        if (responseCode >= 200 && responseCode < 300) {
            inputStream = conn.getInputStream();
        } else {
            inputStream = conn.getErrorStream();
            if (inputStream == null) {
                throw new Exception("HTTP error code: " + responseCode);
            }
        }
        
        StringBuilder response = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
        }
        
        if (responseCode >= 200 && responseCode < 300) {
            return gson.fromJson(response.toString(), responseType);
        } else {
            throw new Exception("Server returned error: " + response.toString());
        }
    }
}
