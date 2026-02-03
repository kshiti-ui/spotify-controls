package com.example.spotifycontrols.spotify;

import com.example.spotifycontrols.SpotifyControlsMod;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class SpotifyAuth {
    
    // IMPORTANT: Replace these with your own Spotify App credentials
    // Get them from: https://developer.spotify.com/dashboard
    private static final String CLIENT_ID = "Client ID";
    private static final String CLIENT_SECRET = "Client Secret";
    // Using 127.0.0.1 instead of localhost per Spotify security requirements
    private static final String REDIRECT_URI = "Redirect url";
    private static final int PORT = Port;
    
    private static final String AUTH_URL = "https://accounts.spotify.com/authorize";
    private static final String TOKEN_URL = "https://accounts.spotify.com/api/token";
    
    // Scopes needed for playback control
    private static final String SCOPES = String.join(" ", 
        "user-read-playback-state",
        "user-modify-playback-state",
        "user-read-currently-playing"
    );
    
    private final TokenStorage tokenStorage;
    private HttpServer server;
    
    public SpotifyAuth(TokenStorage tokenStorage) {
        this.tokenStorage = tokenStorage;
    }
    
    public void startAuthFlow() throws IOException {
        // Start local server to receive callback
        server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/callback", this::handleCallback);
        server.setExecutor(null);
        server.start();
        
        SpotifyControlsMod.LOGGER.info("Auth server started on port " + PORT);
        
        // Build authorization URL
        String authUrl = AUTH_URL + "?" + buildQueryString(Map.of(
            "client_id", CLIENT_ID,
            "response_type", "code",
            "redirect_uri", REDIRECT_URI,
            "scope", SCOPES
        ));
        
        // Open browser
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(authUrl));
                SpotifyControlsMod.LOGGER.info("Browser opened for authentication");
            } else {
                SpotifyControlsMod.LOGGER.warn("Desktop not supported. Please open this URL manually: " + authUrl);
            }
        } catch (Exception e) {
            SpotifyControlsMod.LOGGER.error("Failed to open browser: " + e.getMessage());
        }
        
        // Store the URL so it can be sent to chat
        lastAuthUrl = authUrl;
    }
    
    public String getLastAuthUrl() {
        return lastAuthUrl;
    }
    
    private static String lastAuthUrl = null;
    
    private void handleCallback(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        Map<String, String> params = parseQueryString(query);
        
        String code = params.get("code");
        String error = params.get("error");
        
        if (error != null) {
            sendResponse(exchange, 400, "Authentication failed: " + error);
            stopServer();
            return;
        }
        
        if (code == null) {
            sendResponse(exchange, 400, "No authorization code received");
            stopServer();
            return;
        }
        
        // Exchange code for access token
        try {
            exchangeCodeForToken(code);
            sendResponse(exchange, 200, 
                "<html><body style='font-family: Arial; text-align: center; padding: 50px;'>" +
                "<h1 style='color: #1DB954;'>✓ Success!</h1>" +
                "<p>You can now close this window and return to Minecraft.</p>" +
                "</body></html>");
        } catch (Exception e) {
            SpotifyControlsMod.LOGGER.error("Failed to exchange code for token: " + e.getMessage());
            sendResponse(exchange, 500, "Failed to complete authentication: " + e.getMessage());
        } finally {
            // Stop server after a delay
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    stopServer();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }
    
    private void exchangeCodeForToken(String code) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        
        String body = buildQueryString(Map.of(
            "grant_type", "authorization_code",
            "code", code,
            "redirect_uri", REDIRECT_URI
        ));
        
        String auth = CLIENT_ID + ":" + CLIENT_SECRET;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(TOKEN_URL))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Authorization", "Basic " + encodedAuth)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            tokenStorage.saveTokenResponse(response.body());
            SpotifyControlsMod.LOGGER.info("Access token obtained successfully");
        } else {
            throw new IOException("Failed to get access token. Status: " + response.statusCode() + ", Body: " + response.body());
        }
    }
    
    public void refreshToken() throws IOException, InterruptedException {
        String refreshToken = tokenStorage.getRefreshToken();
        if (refreshToken == null) {
            throw new IOException("No refresh token available");
        }
        
        HttpClient client = HttpClient.newHttpClient();
        
        String body = buildQueryString(Map.of(
            "grant_type", "refresh_token",
            "refresh_token", refreshToken
        ));
        
        String auth = CLIENT_ID + ":" + CLIENT_SECRET;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(TOKEN_URL))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Authorization", "Basic " + encodedAuth)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            tokenStorage.saveTokenResponse(response.body());
            SpotifyControlsMod.LOGGER.info("Access token refreshed successfully");
        } else {
            throw new IOException("Failed to refresh token. Status: " + response.statusCode());
        }
    }
    
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    private void stopServer() {
        if (server != null) {
            server.stop(0);
            SpotifyControlsMod.LOGGER.info("Auth server stopped");
        }
    }
    
    private String buildQueryString(Map<String, String> params) {
        return params.entrySet().stream()
            .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + 
                     URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));
    }
    
    private Map<String, String> parseQueryString(String query) {
        Map<String, String> params = new HashMap<>();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length == 2) {
                    params.put(pair[0], pair[1]);
                }
            }
        }
        return params;
    }
}