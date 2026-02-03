package com.example.spotifycontrols.spotify;

import com.example.spotifycontrols.SpotifyControlsMod;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class SpotifyAPI {

    private static final String BASE = "https://api.spotify.com/v1";

    private final TokenStorage tokenStorage;
    private final HttpClient   client;

    public SpotifyAPI(TokenStorage tokenStorage) {
        this.tokenStorage = tokenStorage;
        this.client       = HttpClient.newHttpClient();
    }

    /* ── simple playback commands ───────────────────────────────────── */
    public void play()     throws IOException, InterruptedException { request("PUT",  "/me/player/play",     null); }
    public void pause()    throws IOException, InterruptedException { request("PUT",  "/me/player/pause",    null); }
    public void skip()     throws IOException, InterruptedException { request("POST", "/me/player/next",     null); }
    public void previous() throws IOException, InterruptedException { request("POST", "/me/player/previous", null); }

    public void setVolume(int pct) throws IOException, InterruptedException {
        request("PUT", "/me/player/volume?volume_percent=" + pct, null);
    }

    public void setRepeatMode(String mode) throws IOException, InterruptedException {
        if (!"track".equals(mode) && !"context".equals(mode) && !"off".equals(mode))
            throw new IllegalArgumentException("Use: track | context | off");
        request("PUT", "/me/player/repeat?state=" + mode, null);
    }

    /* ── search + play ──────────────────────────────────────────────── */
    public String searchAndPlay(String query) throws IOException, InterruptedException {
        String enc = URLEncoder.encode(query, StandardCharsets.UTF_8);
        // NOTE: path is /search not /v1/search — BASE already contains /v1
        String resp = request("GET", "/search?q=" + enc + "&type=track&limit=1", null);
        if (resp == null || resp.isBlank()) return null;

        JsonObject tracks = JsonParser.parseString(resp).getAsJsonObject().getAsJsonObject("tracks");
        if (!tracks.has("items") || tracks.getAsJsonArray("items").isEmpty()) return null;

        JsonObject track = tracks.getAsJsonArray("items").get(0).getAsJsonObject();
        String uri  = track.get("uri").getAsString();
        String name = track.get("name").getAsString();
        String artists = artistNames(track);

        request("PUT", "/me/player/play", "{\"uris\":[\"" + uri + "\"]}");
        return name + " - " + artists;
    }

    /* ── current track (display string only) ───────────────────────── */
    public String getCurrentTrackInfo() throws IOException, InterruptedException {
        TrackData d = getCurrentTrackData();
        return d != null ? d.displayName : null;
    }

    /* ── current track (full data needed by the mod) ───────────────── */
    /**
     * Returned by {@link #getCurrentTrackData()}.  All fields are populated
     * when a track is playing; the method returns {@code null} when nothing plays.
     */
    public static class TrackData {
        public final String displayName;      // "Song – Artist"
        public final float  progressRatio;    // 0.0 – 1.0
        public final String albumImageUrl;    // smallest album-art URL (64×64) or null

        public TrackData(String displayName, float progressRatio, String albumImageUrl) {
            this.displayName     = displayName;
            this.progressRatio   = progressRatio;
            this.albumImageUrl   = albumImageUrl;
        }
    }

    public TrackData getCurrentTrackData() throws IOException, InterruptedException {
        String resp = request("GET", "/me/player/currently-playing", null);
        if (resp == null || resp.isBlank()) return null;

        JsonObject json = JsonParser.parseString(resp).getAsJsonObject();
        if (!json.has("item") || json.get("item").isJsonNull()) return null;

        JsonObject item = json.getAsJsonObject("item");

        // --- display name ---
        String name    = item.get("name").getAsString();
        String artists = artistNames(item);
        String display = name + " - " + artists;

        // --- progress ratio ---
        long progressMs  = json.has("progress_ms") ? json.get("progress_ms").getAsLong() : 0;
        long durationMs  = item.has("duration_ms") ? item.get("duration_ms").getAsLong() : 1;
        float ratio      = (float) progressMs / durationMs;

        // --- album image (pick smallest available, usually 64×64) ---
        String imgUrl = null;
        if (item.has("album")) {
            JsonObject album = item.getAsJsonObject("album");
            if (album.has("images") && album.getAsJsonArray("images").size() > 0) {
                // images are ordered largest → smallest; grab the last one
                var images = album.getAsJsonArray("images");
                imgUrl = images.get(images.size() - 1).getAsJsonObject().get("url").getAsString();
            }
        }

        return new TrackData(display, ratio, imgUrl);
    }

    /* ── low-level HTTP ─────────────────────────────────────────────── */
    private String request(String method, String path, String jsonBody)
            throws IOException, InterruptedException {

        String token = tokenStorage.getAccessToken();
        if (token == null) throw new IOException("No access token — run /spotify login");

        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .header("Authorization", "Bearer " + token);

        switch (method) {
            case "GET"  -> b.GET();
            case "PUT"  -> {
                if (jsonBody != null) { b.header("Content-Type", "application/json"); b.PUT(HttpRequest.BodyPublishers.ofString(jsonBody)); }
                else                   b.PUT(HttpRequest.BodyPublishers.noBody());
            }
            case "POST" -> {
                if (jsonBody != null) { b.header("Content-Type", "application/json"); b.POST(HttpRequest.BodyPublishers.ofString(jsonBody)); }
                else                   b.POST(HttpRequest.BodyPublishers.noBody());
            }
            default -> throw new IllegalArgumentException("Unsupported method: " + method);
        }

        HttpResponse<String> resp = client.send(b.build(), HttpResponse.BodyHandlers.ofString());

        /* auto-refresh on 401 */
        if (resp.statusCode() == 401) {
            SpotifyControlsMod.LOGGER.info("[SpotifyControls] token expired — refreshing");
            SpotifyControlsMod.getSpotifyAuth().refreshToken();

            token = tokenStorage.getAccessToken();
            b.header("Authorization", "Bearer " + token);
            resp = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
        }

        int code = resp.statusCode();
        if (code == 204) return "";                           // No Content — success
        if (code >= 200 && code < 300) return resp.body();
        throw new IOException("Spotify API " + code + ": " + resp.body());
    }

    /* ── util ───────────────────────────────────────────────────────── */
    private static String artistNames(JsonObject trackOrItem) {
        if (!trackOrItem.has("artists")) return "Unknown";
        var sb = new StringBuilder();
        for (var el : trackOrItem.getAsJsonArray("artists")) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(el.getAsJsonObject().get("name").getAsString());
        }
        return sb.toString();
    }
}
