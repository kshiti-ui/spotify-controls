package com.example.spotifycontrols.spotify;

import com.example.spotifycontrols.SpotifyControlsMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class TokenStorage {
    
    private static final String CONFIG_DIR = "config/spotifycontrols";
    private static final String TOKEN_FILE = "spotify.json";
    
    private String accessToken;
    private String refreshToken;
    private long expiresAt;
    
    private final Gson gson;
    private final File configFile;
    
    public TokenStorage() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        
        // Ensure config directory exists
        File configDir = new File(CONFIG_DIR);
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        
        this.configFile = new File(configDir, TOKEN_FILE);
        
        // Load existing token if available
        loadToken();
    }
    
    public void saveTokenResponse(String jsonResponse) {
        try {
            JsonObject json = JsonParser.parseString(jsonResponse).getAsJsonObject();
            
            this.accessToken = json.get("access_token").getAsString();
            
            if (json.has("refresh_token")) {
                this.refreshToken = json.get("refresh_token").getAsString();
            }
            
            int expiresIn = json.get("expires_in").getAsInt();
            this.expiresAt = System.currentTimeMillis() + (expiresIn * 1000L);
            
            saveToken();
            
        } catch (Exception e) {
            SpotifyControlsMod.LOGGER.error("Failed to parse token response: " + e.getMessage());
        }
    }
    
    private void saveToken() {
        try (FileWriter writer = new FileWriter(configFile)) {
            JsonObject json = new JsonObject();
            json.addProperty("access_token", accessToken);
            json.addProperty("refresh_token", refreshToken);
            json.addProperty("expires_at", expiresAt);
            
            gson.toJson(json, writer);
            
            SpotifyControlsMod.LOGGER.info("Token saved successfully");
            
        } catch (IOException e) {
            SpotifyControlsMod.LOGGER.error("Failed to save token: " + e.getMessage());
        }
    }
    
    private void loadToken() {
        if (!configFile.exists()) {
            SpotifyControlsMod.LOGGER.info("No existing token file found");
            return;
        }
        
        try (FileReader reader = new FileReader(configFile)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            
            this.accessToken = json.has("access_token") ? json.get("access_token").getAsString() : null;
            this.refreshToken = json.has("refresh_token") ? json.get("refresh_token").getAsString() : null;
            this.expiresAt = json.has("expires_at") ? json.get("expires_at").getAsLong() : 0;
            
            // Check if token is expired
            if (isTokenExpired()) {
                SpotifyControlsMod.LOGGER.info("Token loaded but expired");
            } else {
                SpotifyControlsMod.LOGGER.info("Token loaded successfully");
            }
            
        } catch (Exception e) {
            SpotifyControlsMod.LOGGER.error("Failed to load token: " + e.getMessage());
        }
    }
    
    public String getAccessToken() {
        if (isTokenExpired()) {
            return null;
        }
        return accessToken;
    }
    
    public String getRefreshToken() {
        return refreshToken;
    }
    
    public boolean hasToken() {
        return accessToken != null && !isTokenExpired();
    }
    
    private boolean isTokenExpired() {
        if (accessToken == null) {
            return true;
        }
        // Add 5 minute buffer before actual expiration
        return System.currentTimeMillis() >= (expiresAt - 300000);
    }
    
    public void clearToken() {
        this.accessToken = null;
        this.refreshToken = null;
        this.expiresAt = 0;
        
        if (configFile.exists()) {
            configFile.delete();
        }
        
        SpotifyControlsMod.LOGGER.info("Token cleared");
    }
}
