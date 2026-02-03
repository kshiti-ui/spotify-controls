package com.example.spotifycontrols;

import com.example.spotifycontrols.command.SpotifyCommand;
import com.example.spotifycontrols.spotify.SpotifyAPI;
import com.example.spotifycontrols.spotify.SpotifyAuth;
import com.example.spotifycontrols.spotify.TokenStorage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicReference;

@Environment(EnvType.CLIENT)
public class SpotifyControlsMod implements ClientModInitializer {
    public static final String MOD_ID = "spotifycontrols";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /* ── singletons ───────────────────────────────────────────────── */
    private static SpotifyAPI    spotifyAPI;
    private static TokenStorage  tokenStorage;
    private static SpotifyAuth   spotifyAuth;

    /* ── track-change detection ───────────────────────────────────── */
    private static String  lastTrackName  = "";
    private static int     tickCounter    = 0;
    private static final int CHECK_INTERVAL = 60;   // 3 seconds @ 20 tps

    /* ── XP-bar progress (singleplayer only) ─────────────────────── */
    private static volatile float  currentProgress = -1f;   // -1 = not playing
    private static float           savedXpProgress = 0f;
    private static int             savedXpLevel    = 0;
    private static boolean         xpSaved         = false;

    /* ── album-art colour (hex or null → green) ──────────────────── */
    private static volatile String albumColourHex = null;

    /* ── toast scheduled from background thread ──────────────────── */
    private static final AtomicReference<Runnable> pendingToast = new AtomicReference<>(null);

    /* ════════════════════════════════════════════════════════════════ */
    @Override
    public void onInitializeClient() {
        LOGGER.info("[SpotifyControls] Initialising (client-side mod)");

        tokenStorage = new TokenStorage();
        spotifyAuth  = new SpotifyAuth(tokenStorage);
        spotifyAPI   = new SpotifyAPI(tokenStorage);

        // client commands — works in BOTH singleplayer & multiplayer
        SpotifyCommand.register();

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        LOGGER.info("[SpotifyControls] Ready");
    }

    /* ── tick ─────────────────────────────────────────────────────── */
    private void onClientTick(MinecraftClient client) {
        // flush any toast that a background thread prepared
        Runnable t = pendingToast.getAndSet(null);
        if (t != null) t.run();

        // periodic Spotify poll
        tickCounter++;
        if (tickCounter >= CHECK_INTERVAL) {
            tickCounter = 0;
            if (tokenStorage.hasToken()) pollCurrentTrack(client);
        }

        // keep the XP bar faked every tick (singleplayer only)
        updateXpBar(client);
    }

    /* ── poll ─────────────────────────────────────────────────────── */
    private void pollCurrentTrack(MinecraftClient client) {
        new Thread(() -> {
            try {
                SpotifyAPI.TrackData data = spotifyAPI.getCurrentTrackData();

                if (data == null) {
                    currentProgress = -1f;
                    albumColourHex  = null;
                    return;
                }

                currentProgress = data.progressRatio;

                if (!data.displayName.equals(lastTrackName)) {
                    lastTrackName = data.displayName;

                    // extract dominant colour from album art
                    albumColourHex = (data.albumImageUrl != null && !data.albumImageUrl.isEmpty())
                            ? extractDominantColour(data.albumImageUrl)
                            : null;

                    // schedule the toast on the main thread
                    String name = data.displayName;
                    pendingToast.set(() -> showToast(client, name));
                }
            } catch (Exception e) {
                LOGGER.error("[SpotifyControls] poll error: " + e.getMessage());
            }
        }).start();
    }

    /* ── SystemToast ──────────────────────────────────────────────── */
    /**
     * Shows a system toast (top-right popup).  MUST be called on the
     * main / client thread.
     *
     * client.getToastManager() is the correct accessor in 1.21 — it is
     * a method, not a public field.
     */
    public static void showToast(MinecraftClient client, String trackDisplayName) {
        SystemToast.show(
                client.getToastManager(),                        // method, not field
                SystemToast.Type.PERIODIC_NOTIFICATION,          // repeatable, no side-effects
                Text.literal("♪ Now Playing"),
                Text.literal(trackDisplayName));
    }

    /* ── XP bar (singleplayer only) ───────────────────────────────── */
    /**
     * In singleplayer (integrated server present) we overwrite the
     * client-local XP fields every tick to show song progress.
     *
     * experienceProgress and experienceLevel are PUBLIC FIELDS on
     * PlayerEntity — there are no setter methods in 1.21.  We assign
     * them directly.
     *
     * In multiplayer we skip entirely so real XP is never touched.
     */
    private void updateXpBar(MinecraftClient client) {
        if (client.player == null) return;

        // singleplayer = integrated server is non-null
        if (client.getServer() == null) return;   // multiplayer → do nothing

        if (currentProgress < 0f) {
            restoreXp(client);
            return;
        }

        // save the real values once, before we start overwriting
        if (!xpSaved) {
            savedXpProgress = client.player.experienceProgress;   // float field
            savedXpLevel    = client.player.experienceLevel;      // int  field
            xpSaved         = true;
        }

        // overwrite with song progress (0.0 – 1.0)
        client.player.experienceProgress = currentProgress;
        client.player.experienceLevel    = 0;   // hide the level number
    }

    /** Restore real XP when music stops / pauses. */
    private void restoreXp(MinecraftClient client) {
        if (!xpSaved || client.player == null) return;
        client.player.experienceProgress = savedXpProgress;
        client.player.experienceLevel    = savedXpLevel;
        xpSaved = false;
    }

    /* ── album-art colour extraction ──────────────────────────────── */
    /**
     * Downloads the album thumbnail, samples a 16×16 grid, and returns
     * the most-saturated pixel as "#RRGGBB".  Returns null on any failure
     * (caller falls back to green).
     */
    private static String extractDominantColour(String imageUrl) {
        try {
            HttpClient http = HttpClient.newHttpClient();
            HttpRequest req  = HttpRequest.newBuilder(URI.create(imageUrl)).GET().build();
            HttpResponse<InputStream> resp =
                    http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) return null;

            BufferedImage img = ImageIO.read(resp.body());
            if (img == null) return null;

            int w = img.getWidth(), h = img.getHeight();
            int step = Math.max(1, Math.min(w, h) / 16);

            float bestSat = -1f;
            int   bestRgb  = 0x1DB954;          // Spotify green fallback

            for (int y = 0; y < h; y += step) {
                for (int x = 0; x < w; x += step) {
                    int rgb = img.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF,
                        g = (rgb >>  8) & 0xFF,
                        b =  rgb        & 0xFF;

                    float brightness = (r + g + b) / 765f;
                    if (brightness < 0.08f || brightness > 0.92f) continue;

                    float sat = hslSaturation(r, g, b);
                    if (sat > bestSat) {
                        bestSat = sat;
                        bestRgb = (r << 16) | (g << 8) | b;
                    }
                }
            }
            return String.format("#%06X", bestRgb);

        } catch (Exception e) {
            LOGGER.warn("[SpotifyControls] album-art fetch: " + e.getMessage());
            return null;
        }
    }

    /** HSL saturation 0–1 from RGB 0–255. */
    private static float hslSaturation(int r, int g, int b) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        if (max == min) return 0f;
        float l = (max + min) / 2f;
        float d = max - min;
        return d / (l > 0.5f ? 2f - max - min : max + min);
    }

    /* ── public accessors ─────────────────────────────────────────── */
    public static String        getAlbumColourHex()  { return albumColourHex;  }
    public static SpotifyAPI    getSpotifyAPI()      { return spotifyAPI;      }
    public static TokenStorage  getTokenStorage()    { return tokenStorage;    }
    public static SpotifyAuth   getSpotifyAuth()     { return spotifyAuth;     }

    /** Called by SpotifyCommand.pause so the XP bar restores immediately. */
    public static void notifyPaused() { currentProgress = -1f; }
}
