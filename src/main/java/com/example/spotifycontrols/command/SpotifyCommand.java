package com.example.spotifycontrols.command;

import com.example.spotifycontrols.SpotifyControlsMod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;

/**
 * All /spotify sub-commands.
 *
 * Uses Fabric's CLIENT command API (v2).  The command source type is
 * {@link FabricClientCommandSource} — NOT the vanilla ClientCommandSource.
 * sendFeedback on FabricClientCommandSource takes a single Text argument
 * (no boolean broadcast flag).
 *
 * Client commands execute on the client in BOTH singleplayer and
 * multiplayer, so every /spotify command works in both modes without
 * any server-side mod.
 */
public class SpotifyCommand {

    /* ── registration ───────────────────────────────────────────── */
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
        ctx.getSource().sendFeedback(Text.literal("§eStarting Spotify authentication…"));

        new Thread(() -> {
            try {
                SpotifyControlsMod.getSpotifyAuth().startAuthFlow();
                String url = SpotifyControlsMod.getSpotifyAuth().getLastAuthUrl();

                // single clickable link — styled with click + hover events
                Text link = Text.literal("§b§n" + url)
                        .styled(s -> s
                                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Text.literal("§eClick to open Spotify login"))));

                ctx.getSource().sendFeedback(
                        Text.literal("§aClick the link to log in: ").append(link));

            } catch (Exception e) {
                ctx.getSource().sendFeedback(Text.literal("§cAuth failed: " + e.getMessage()));
            }
        }).start();

        return 1;
    }

    private static int logout(CommandContext<FabricClientCommandSource> ctx) {
        SpotifyControlsMod.getTokenStorage().clearToken();
        ctx.getSource().sendFeedback(Text.literal("§aLogged out of Spotify ✓"));
        return 1;
    }

    /* ── playback ───────────────────────────────────────────────── */
    private static int resume(CommandContext<FabricClientCommandSource> ctx) {
        if (!checkAuth(ctx)) return 0;
        run(ctx, () -> {
            SpotifyControlsMod.getSpotifyAPI().play();
            ctx.getSource().sendFeedback(Text.literal("§a▶ Resumed"));
        }, "resume");
        return 1;
    }

    private static int playSearch(CommandContext<FabricClientCommandSource> ctx) {
        if (!checkAuth(ctx)) return 0;
        String query = StringArgumentType.getString(ctx, "query");
        run(ctx, () -> {
            String info = SpotifyControlsMod.getSpotifyAPI().searchAndPlay(query);
            if (info != null)
                ctx.getSource().sendFeedback(Text.literal("§a♪ Now playing: §f" + info));
            else
                ctx.getSource().sendFeedback(Text.literal("§cNo results for: " + query));
        }, "play");
        return 1;
    }

    private static int pause(CommandContext<FabricClientCommandSource> ctx) {
        if (!checkAuth(ctx)) return 0;
        run(ctx, () -> {
            SpotifyControlsMod.getSpotifyAPI().pause();
            SpotifyControlsMod.notifyPaused();          // restore XP bar instantly
            ctx.getSource().sendFeedback(Text.literal("§e⏸ Paused"));
        }, "pause");
        return 1;
    }

    private static int skip(CommandContext<FabricClientCommandSource> ctx) {
        if (!checkAuth(ctx)) return 0;
        run(ctx, () -> {
            SpotifyControlsMod.getSpotifyAPI().skip();
            ctx.getSource().sendFeedback(Text.literal("§a⏭ Skipped"));
        }, "skip");
        return 1;
    }

    private static int previous(CommandContext<FabricClientCommandSource> ctx) {
        if (!checkAuth(ctx)) return 0;
        run(ctx, () -> {
            SpotifyControlsMod.getSpotifyAPI().previous();
            ctx.getSource().sendFeedback(Text.literal("§a⏮ Previous"));
        }, "previous");
        return 1;
    }

    /* ── settings ───────────────────────────────────────────────── */
    private static int loop(CommandContext<FabricClientCommandSource> ctx) {
        if (!checkAuth(ctx)) return 0;
        String mode = StringArgumentType.getString(ctx, "mode");
        run(ctx, () -> {
            SpotifyControlsMod.getSpotifyAPI().setRepeatMode(mode);
            ctx.getSource().sendFeedback(Text.literal("§a🔁 Loop → " + mode));
        }, "loop");
        return 1;
    }

    private static int volume(CommandContext<FabricClientCommandSource> ctx) {
        if (!checkAuth(ctx)) return 0;
        int pct = IntegerArgumentType.getInteger(ctx, "percent");
        run(ctx, () -> {
            SpotifyControlsMod.getSpotifyAPI().setVolume(pct);
            ctx.getSource().sendFeedback(Text.literal("§a🔊 Volume → " + pct + "%"));
        }, "volume");
        return 1;
    }

    private static int current(CommandContext<FabricClientCommandSource> ctx) {
        if (!checkAuth(ctx)) return 0;
        run(ctx, () -> {
            String info = SpotifyControlsMod.getSpotifyAPI().getCurrentTrackInfo();
            ctx.getSource().sendFeedback(
                    info != null && !info.isEmpty()
                            ? Text.literal("§a♪ Now Playing: §f" + info)
                            : Text.literal("§eNothing playing"));
        }, "current");
        return 1;
    }

    private static int status(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(
                SpotifyControlsMod.getTokenStorage().hasToken()
                        ? Text.literal("§aConnected to Spotify ✓")
                        : Text.literal("§cNot connected — run /spotify login"));
        return 1;
    }

    /* ── helpers ────────────────────────────────────────────────── */
    private static boolean checkAuth(CommandContext<FabricClientCommandSource> ctx) {
        if (!SpotifyControlsMod.getTokenStorage().hasToken()) {
            ctx.getSource().sendFeedback(Text.literal("§cNot logged in — run /spotify login"));
            return false;
        }
        return true;
    }

    /**
     * Runs an API call on a background thread.  Errors are reported back
     * to chat automatically.
     */
    private static void run(CommandContext<FabricClientCommandSource> ctx,
                            CheckedRunnable action, String label) {
        new Thread(() -> {
            try {
                action.run();
            } catch (Exception e) {
                ctx.getSource().sendFeedback(
                        Text.literal("§c" + label + " failed: " + e.getMessage()));
            }
        }).start();
    }

    @FunctionalInterface
    interface CheckedRunnable { void run() throws Exception; }
}
