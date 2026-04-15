package com.example.spotifycontrols.command;

import com.example.spotifycontrols.SpotifyControlsMod;
import com.example.spotifycontrols.SpotifyKeybinds;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;

public class SpotifyCommand {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                registerCommands(dispatcher));
    }

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("spotify")
                .then(ClientCommandManager.literal("login")
                        .executes(SpotifyCommand::login))
                .then(ClientCommandManager.literal("logout")
                        .executes(SpotifyCommand::logout))
                .then(ClientCommandManager.literal("resume")
                        .executes(SpotifyCommand::resume))
                .then(ClientCommandManager.literal("play")
                        .then(ClientCommandManager.argument("query", StringArgumentType.greedyString())
                                .executes(SpotifyCommand::playSearch)))
                .then(ClientCommandManager.literal("pause")
                        .executes(SpotifyCommand::pause))
                .then(ClientCommandManager.literal("skip")
                        .executes(SpotifyCommand::skip))
                .then(ClientCommandManager.literal("previous")
                        .executes(SpotifyCommand::previous))
                .then(ClientCommandManager.literal("loop")
                        .then(ClientCommandManager.argument("mode", StringArgumentType.word())
                                .executes(SpotifyCommand::loop)))
                .then(ClientCommandManager.literal("volume")
                        .then(ClientCommandManager.argument("percent", IntegerArgumentType.integer(0, 100))
                                .executes(SpotifyCommand::volume)))
                .then(ClientCommandManager.literal("current")
                        .executes(SpotifyCommand::current))
                .then(ClientCommandManager.literal("status")
                        .executes(SpotifyCommand::status))
        );
    }

    /* ── login / logout ─────────────────────────────────────────── */
    private static int login(CommandContext<FabricClientCommandSource> ctx) {
        MinecraftClient client = ctx.getSource().getClient();
        
        sendChatMessage(client, Text.literal("§eStarting Spotify authentication…"));

        new Thread(() -> {
            try {
                SpotifyControlsMod.getSpotifyAuth().startAuthFlow();

                String url;
                while ((url = SpotifyControlsMod.getSpotifyAuth().getLastAuthUrl()) == null) {
                    Thread.sleep(10);
                }

                final String finalUrl = url;
                
                client.execute(() -> {
                    if (client.keyboard != null) {
                        client.keyboard.setClipboard(finalUrl);
                    }
                sendChatMessage(client, Text.literal("§aSpotify login link copied to clipboard!"));
                sendChatMessage(client, Text.literal("§7Paste it into your browser to continue."));
                });


            } catch (Exception e) {
                client.execute(() ->
                sendChatMessage(client, Text.literal("§cAuth failed: " + e.getMessage())));
            }
        }).start();

        return 1;
    }

    private static int logout(CommandContext<FabricClientCommandSource> ctx) {
        MinecraftClient client = ctx.getSource().getClient();
        SpotifyControlsMod.getTokenStorage().clearToken();
        sendChatMessage(client, Text.literal("§aLogged out of Spotify ✓"));
        return 1;
    }

    /* ── playback ───────────────────────────────────────────────── */
    private static int resume(CommandContext<FabricClientCommandSource> ctx) {
        if (!checkAuth(ctx)) return 0;
        run(ctx, () -> {
            SpotifyControlsMod.getSpotifyAPI().play();
            SpotifyKeybinds.setPlayState(true);  // Sync keybind state
            sendChatMessage(ctx.getSource().getClient(), Text.literal("§a▶ Resumed"));
        }, "resume");
        return 1;
    }

    private static int playSearch(CommandContext<FabricClientCommandSource> ctx) {
        if (!checkAuth(ctx)) return 0;
        String query = StringArgumentType.getString(ctx, "query");
        run(ctx, () -> {
            String info = SpotifyControlsMod.getSpotifyAPI().searchAndPlay(query);
            SpotifyKeybinds.setPlayState(true);  // Sync keybind state
            if (info != null)
                sendChatMessage(ctx.getSource().getClient(), Text.literal("§a♪ Now playing: §f" + info));
            else
                sendChatMessage(ctx.getSource().getClient(), Text.literal("§cNo results for: " + query));
        }, "play");
        return 1;
    }

    private static int pause(CommandContext<FabricClientCommandSource> ctx) {
        if (!checkAuth(ctx)) return 0;
        run(ctx, () -> {
            SpotifyControlsMod.getSpotifyAPI().pause();
            SpotifyControlsMod.notifyPaused();
            SpotifyKeybinds.setPlayState(false);  // Sync keybind state
            sendChatMessage(ctx.getSource().getClient(), Text.literal("§e⏸ Paused"));
        }, "pause");
        return 1;
    }

    private static int skip(CommandContext<FabricClientCommandSource> ctx) {
        if (!checkAuth(ctx)) return 0;
        run(ctx, () -> {
            SpotifyControlsMod.getSpotifyAPI().skip();
            sendChatMessage(ctx.getSource().getClient(), Text.literal("§a⏭ Skipped"));
        }, "skip");
        return 1;
    }

    private static int previous(CommandContext<FabricClientCommandSource> ctx) {
        if (!checkAuth(ctx)) return 0;
        run(ctx, () -> {
            SpotifyControlsMod.getSpotifyAPI().previous();
            sendChatMessage(ctx.getSource().getClient(), Text.literal("§a⏮ Previous"));
        }, "previous");
        return 1;
    }

    /* ── settings ───────────────────────────────────────────────── */
    private static int loop(CommandContext<FabricClientCommandSource> ctx) {
        if (!checkAuth(ctx)) return 0;
        String mode = StringArgumentType.getString(ctx, "mode");
        run(ctx, () -> {
            SpotifyControlsMod.getSpotifyAPI().setRepeatMode(mode);
            sendChatMessage(ctx.getSource().getClient(), Text.literal("§a🔁 Loop → " + mode));
        }, "loop");
        return 1;
    }

    private static int volume(CommandContext<FabricClientCommandSource> ctx) {
        if (!checkAuth(ctx)) return 0;
        int pct = IntegerArgumentType.getInteger(ctx, "percent");
        run(ctx, () -> {
            SpotifyControlsMod.getSpotifyAPI().setVolume(pct);
            sendChatMessage(ctx.getSource().getClient(), Text.literal("§a🔊 Volume → " + pct + "%"));
        }, "volume");
        return 1;
    }

    private static int current(CommandContext<FabricClientCommandSource> ctx) {
        if (!checkAuth(ctx)) return 0;
        run(ctx, () -> {
            String info = SpotifyControlsMod.getSpotifyAPI().getCurrentTrackInfo();
            sendChatMessage(ctx.getSource().getClient(),
                    info != null && !info.isEmpty()
                            ? Text.literal("§a♪ Now Playing: §f" + info)
                            : Text.literal("§eNothing playing"));
        }, "current");
        return 1;
    }

    private static int status(CommandContext<FabricClientCommandSource> ctx) {
        sendChatMessage(ctx.getSource().getClient(),
                SpotifyControlsMod.getTokenStorage().hasToken()
                        ? Text.literal("§aConnected to Spotify ✓")
                        : Text.literal("§cNot connected — run /spotify login"));
        return 1;
    }

    /* ── helpers ────────────────────────────────────────────────── */
    private static boolean checkAuth(CommandContext<FabricClientCommandSource> ctx) {
        if (!SpotifyControlsMod.getTokenStorage().hasToken()) {
            sendChatMessage(ctx.getSource().getClient(), 
                    Text.literal("§cNot logged in — run /spotify login"));
            return false;
        }
        return true;
    }

    /**
     * Sends a message directly to the player's chat.
     * Uses player.sendMessage() which is the reliable method for client-side
     * mods in 1.21+.
     */
    private static void sendChatMessage(MinecraftClient client, Text message) {
        if (client.player != null) {
            client.player.sendMessage(message, false);
        }
    }

    private static void run(CommandContext<FabricClientCommandSource> ctx,
                            CheckedRunnable action, String label) {
        MinecraftClient client = ctx.getSource().getClient();
        
        new Thread(() -> {
            try {
                action.run();
            } catch (Exception e) {
                client.execute(() ->
                        sendChatMessage(client, Text.literal("§c" + label + " failed: " + e.getMessage())));
            }
        }).start();
    }

    @FunctionalInterface
    interface CheckedRunnable { void run() throws Exception; }
}
