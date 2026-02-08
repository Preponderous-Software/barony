package com.barony.frontend.client;

import com.barony.frontend.model.Command;
import com.barony.frontend.model.GameState;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class GameClient {
    private final String baseUrl;
    private final Gson gson;
    
    public GameClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.gson = new Gson();
    }
    
    public GameState getState() {
        try {
            URL url = new URL(baseUrl + "/state");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            in.close();
            
            return gson.fromJson(response.toString(), GameState.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public GameState tick() {
        try {
            URL url = new URL(baseUrl + "/tick");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            in.close();
            
            return gson.fromJson(response.toString(), GameState.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public GameState sendCommand(Command command) {
        try {
            URL url = new URL(baseUrl + "/command");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            
            String jsonCommand = gson.toJson(command);
            OutputStream os = conn.getOutputStream();
            os.write(jsonCommand.getBytes());
            os.flush();
            os.close();
            
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            in.close();
            
            return gson.fromJson(response.toString(), GameState.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
