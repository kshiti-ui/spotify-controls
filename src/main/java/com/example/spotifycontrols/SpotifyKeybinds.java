package com.example.spotifycontrols;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Manages keyboard shortcuts for Spotify playback control.
 * 
 * Default bindings:
 * - P: Play/Pause toggle
 * - N: Next track
 * - B: Previous (Back) track
 * - +: Volume up
 * - -: Volume down
 */
public class SpotifyKeybinds {
    
    // Category for 1.21.9+ - uses KeyBinding.Category instead of String
    private static final KeyBinding.Category CATEGORY = 
            KeyBinding.Category.create(Identifier.of("spotifycontrols", "main"));
    
    // Keybindings
    private static KeyBinding playPauseKey;
    private static KeyBinding nextKey;
    private static KeyBinding previousKey;
    private static KeyBinding volumeUpKey;
    private static KeyBinding volumeDownKey;
    
    // State tracking for play/pause toggle
    private static boolean isPlaying = false;
    
    // Volume control
    private static int currentVolume = 50;  // Default 50%
    private static final int VOLUME_STEP = 5;
    
    public static void register() {
        // Register keybindings - 1.21.9+ uses KeyBinding.Category object
        playPauseKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spotifycontrols.playpause",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                CATEGORY
        ));
        
        nextKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spotifycontrols.next",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                CATEGORY
        ));
        
        previousKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spotifycontrols.previous",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                CATEGORY
        ));
        
        volumeUpKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spotifycontrols.volumeup",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_EQUAL,  // + key (same key as =)
                CATEGORY
        ));
        
        volumeDownKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spotifycontrols.volumedown",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_MINUS,
                CATEGORY
        ));
        
        // Register tick handler to check for key presses
        ClientTickEvents.END_CLIENT_TICK.register(SpotifyKeybinds::onClientTick);
        
        SpotifyControlsMod.LOGGER.info("[SpotifyControls] Keybinds registered");
    }
    
    private static void onClientTick(MinecraftClient client) {
        // Check if not logged in and show message if keybind is pressed
        if (!SpotifyControlsMod.getTokenStorage().hasToken()) {
            // Check if any Spotify keybind was pressed
            if (playPauseKey.wasPressed() || nextKey.wasPressed() || previousKey.wasPressed() ||
                volumeUpKey.wasPressed() || volumeDownKey.wasPressed()) {
                sendMessage(client, "§cSpotify not connected — run /spotify login");
            }
            return;  // Don't process keys if not logged in
        }
        
        // Play/Pause toggle
        if (playPauseKey.wasPressed()) {
            handlePlayPause(client);
        }
        
        // Next track
        if (nextKey.wasPressed()) {
            handleNext(client);
        }
        
        // Previous track
        if (previousKey.wasPressed()) {
            handlePrevious(client);
        }
        
        // Volume up
        if (volumeUpKey.wasPressed()) {
            handleVolumeUp(client);
        }
        
        // Volume down
        if (volumeDownKey.wasPressed()) {
            handleVolumeDown(client);
        }
    }
    
    /* ── Keybind handlers ──────────────────────────────────────────── */
    
    private static void handlePlayPause(MinecraftClient client) {
        new Thread(() -> {
            try {
                if (isPlaying) {
                    SpotifyControlsMod.getSpotifyAPI().pause();
                    SpotifyControlsMod.notifyPaused();
                    isPlaying = false;
                    client.execute(() -> sendMessage(client, "§e⏸ Paused"));
                } else {
                    SpotifyControlsMod.getSpotifyAPI().play();
                    isPlaying = true;
                    client.execute(() -> sendMessage(client, "§a▶ Playing"));
                }
            } catch (Exception e) {
                client.execute(() -> sendMessage(client, "§cPlayback failed: " + e.getMessage()));
            }
        }).start();
    }
    
    private static void handleNext(MinecraftClient client) {
        new Thread(() -> {
            try {
                SpotifyControlsMod.getSpotifyAPI().skip();
                isPlaying = true;  // Skipping auto-resumes
                client.execute(() -> sendMessage(client, "§a⏭ Skipped"));
            } catch (Exception e) {
                client.execute(() -> sendMessage(client, "§cSkip failed: " + e.getMessage()));
            }
        }).start();
    }
    
    private static void handlePrevious(MinecraftClient client) {
        new Thread(() -> {
            try {
                SpotifyControlsMod.getSpotifyAPI().previous();
                isPlaying = true;
                client.execute(() -> sendMessage(client, "§a⏮ Previous"));
            } catch (Exception e) {
                client.execute(() -> sendMessage(client, "§cPrevious failed: " + e.getMessage()));
            }
        }).start();
    }
    
    private static void handleVolumeUp(MinecraftClient client) {
        new Thread(() -> {
            try {
                currentVolume = Math.min(100, currentVolume + VOLUME_STEP);
                SpotifyControlsMod.getSpotifyAPI().setVolume(currentVolume);
                client.execute(() -> sendMessage(client, "§a🔊 Volume: " + currentVolume + "%"));
            } catch (Exception e) {
                client.execute(() -> sendMessage(client, "§cVolume failed: " + e.getMessage()));
            }
        }).start();
    }
    
    private static void handleVolumeDown(MinecraftClient client) {
        new Thread(() -> {
            try {
                currentVolume = Math.max(0, currentVolume - VOLUME_STEP);
                SpotifyControlsMod.getSpotifyAPI().setVolume(currentVolume);
                client.execute(() -> sendMessage(client, "§a🔉 Volume: " + currentVolume + "%"));
            } catch (Exception e) {
                client.execute(() -> sendMessage(client, "§cVolume failed: " + e.getMessage()));
            }
        }).start();
    }
    
    /* ── Utility ───────────────────────────────────────────────────── */
    
    private static void sendMessage(MinecraftClient client, String message) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message), false);
        }
    }
    
    /**
     * Updates play state when track changes or pause is triggered externally.
     * Called by SpotifyControlsMod when needed.
     */
    public static void setPlayState(boolean playing) {
        isPlaying = playing;
    }
}
