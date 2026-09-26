package com.babou.webstream.command;

import com.babou.webstream.WebStreamMod;
import com.babou.webstream.core.Workspace;
import com.babou.webstream.core.Workspace.ProfileInfo;
import com.babou.webstream.core.Workspace.ScreenInfo;
import com.babou.webstream.core.WsException;
import com.babou.webstream.web.WebService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * /webstream            état (profil actif, monde, adresse des images)
 * /webstream url <écran>          lien cliquable vers l'image d'un écran
 * /webstream admin                lien vers l'interface d'administration       (opérateurs)
 * /webstream profile [list|use <id>]  voir / changer de profil                (list, use : opérateurs)
 * /webstream reload               relit les profils depuis le disque            (opérateurs)
 *
 * Les messages suivent la langue choisie dans la page Réglages (fr, en, es).
 */
public final class WebStreamCommand {
    private static final int OP_LEVEL = 3;

    private static final SuggestionProvider<ServerCommandSource> SCREENS = (ctx, builder) -> {
        Workspace ws = WebStreamMod.workspace();
        List<String> refs = ws == null ? List.of() : ws.listScreens().stream().map(ScreenInfo::ref).toList();
        return CommandSource.suggestMatching(refs, builder);
    };

    private static final SuggestionProvider<ServerCommandSource> PROFILES = (ctx, builder) -> {
        Workspace ws = WebStreamMod.workspace();
        List<String> ids = ws == null ? List.of() : ws.listProfiles().stream().map(ProfileInfo::id).toList();
        return CommandSource.suggestMatching(ids, builder);
    };

    private WebStreamCommand() {}

    private static String tr(String key, Object... args) {
        return WebStreamMod.CONFIG.tr(key, args);
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("webstream")
            .executes(ctx -> status(ctx.getSource()))
            .then(CommandManager.literal("url")
                .then(CommandManager.argument("screen", StringArgumentType.word()).suggests(SCREENS)
                    .executes(ctx -> url(ctx.getSource(), StringArgumentType.getString(ctx, "screen")))))
            .then(CommandManager.literal("admin")
                .requires(s -> s.hasPermissionLevel(OP_LEVEL))
                .executes(ctx -> admin(ctx.getSource())))
            .then(CommandManager.literal("profile")
                .executes(ctx -> currentProfile(ctx.getSource()))
                .then(CommandManager.literal("list")
                    .requires(s -> s.hasPermissionLevel(OP_LEVEL))
                    .executes(ctx -> listProfiles(ctx.getSource())))
                .then(CommandManager.literal("use")
                    .requires(s -> s.hasPermissionLevel(OP_LEVEL))
                    .then(CommandManager.argument("id", StringArgumentType.word()).suggests(PROFILES)
                        .executes(ctx -> useProfile(ctx.getSource(), StringArgumentType.getString(ctx, "id"))))))
            .then(CommandManager.literal("reload")
                .requires(s -> s.hasPermissionLevel(OP_LEVEL))
                .executes(ctx -> reload(ctx.getSource()))));
    }

    // ------------------------------------------------------------------ sous-commandes

    private static boolean running(ServerCommandSource src) {
        if (WebStreamMod.workspace() != null && WebStreamMod.service() != null) return true;
        src.sendError(Text.literal(tr("cmd.notStarted")));
        return false;
    }

    private static int status(ServerCommandSource src) {
        if (!running(src)) return 0;
        Workspace ws = WebStreamMod.workspace();
        WebService web = WebStreamMod.service();
        String world = ws.currentWorld();
        int screens = ws.listScreens().size();
        src.sendFeedback(() -> Text.literal(tr("cmd.status.prefix")).formatted(Formatting.GRAY)
            .append(Text.literal(ws.activeProfileName()).formatted(Formatting.WHITE, Formatting.BOLD))
            .append(Text.literal(world == null ? tr("cmd.status.suffix", screens) : tr("cmd.status.suffixWorld", screens, world)).formatted(Formatting.GRAY)), false);
        src.sendFeedback(() -> Text.literal(tr("cmd.status.addr")).formatted(Formatting.GRAY)
            .append(Text.literal(tr("cmd.status.example", web.baseUrl())).formatted(Formatting.AQUA)), false);
        if (blank(WebStreamMod.CONFIG.publicUrl)) {
            src.sendFeedback(() -> Text.literal(tr("cmd.status.hint")).formatted(Formatting.YELLOW), false);
        }
        return 1;
    }

    private static int url(ServerCommandSource src, String ref) {
        if (!running(src)) return 0;
        ScreenInfo screen = WebStreamMod.workspace().getScreen(ref);
        if (screen == null) {
            src.sendError(Text.literal(tr("cmd.url.notFound", ref, WebStreamMod.workspace().activeProfileName())));
            return 0;
        }
        String link = WebStreamMod.service().screenUrl(ref);
        src.sendFeedback(() -> Text.literal(tr("cmd.url.label", ref)).formatted(Formatting.GRAY).append(clickable(link)), false);
        return 1;
    }

    private static int admin(ServerCommandSource src) {
        if (!running(src)) return 0;
        String adminUrl = WebStreamMod.CONFIG.adminUrl;
        String link;
        if (!blank(adminUrl)) {
            link = adminUrl.trim();
        } else if (!src.getServer().isDedicated()) {
            link = WebStreamMod.CONFIG.localAdminUrl();
        } else {
            src.sendFeedback(() -> Text.literal(tr("cmd.admin.localOnly", WebStreamMod.CONFIG.localAdminUrl())).formatted(Formatting.YELLOW), false);
            return 1;
        }
        src.sendFeedback(() -> Text.literal(tr("cmd.admin.label")).formatted(Formatting.GRAY).append(clickable(link)), false);
        return 1;
    }

    private static int currentProfile(ServerCommandSource src) {
        if (!running(src)) return 0;
        Workspace ws = WebStreamMod.workspace();
        src.sendFeedback(() -> Text.literal(tr("cmd.profile.current")).formatted(Formatting.GRAY)
            .append(Text.literal(ws.activeProfileName() + " (" + ws.activeProfileId() + ")").formatted(Formatting.WHITE)), false);
        return 1;
    }

    private static int listProfiles(ServerCommandSource src) {
        if (!running(src)) return 0;
        for (ProfileInfo p : WebStreamMod.workspace().listProfiles()) {
            String detail = "  [" + p.id() + "] " + tr("cmd.profile.screens", p.screenCount())
                + (p.worlds().isEmpty() ? "" : tr("cmd.profile.worlds", String.join(", ", p.worlds())));
            MutableText line = Text.literal((p.active() ? "▶ " : "  ") + p.name()).formatted(p.active() ? Formatting.GREEN : Formatting.WHITE)
                .append(Text.literal(detail).formatted(Formatting.GRAY));
            src.sendFeedback(() -> line, false);
        }
        return 1;
    }

    private static int useProfile(ServerCommandSource src, String id) {
        if (!running(src)) return 0;
        Workspace ws = WebStreamMod.workspace();
        try {
            ws.activateProfile(id, true);
        } catch (WsException e) {
            src.sendError(Text.literal(tr("cmd.profile.notFound", id)));
            return 0;
        }
        src.sendFeedback(() -> Text.literal(tr("cmd.profile.used", ws.activeProfileName())
            + (ws.currentWorld() == null ? "" : tr("cmd.profile.usedWorld", ws.currentWorld()))
            + tr("cmd.profile.usedEnd")).formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int reload(ServerCommandSource src) {
        if (!running(src)) return 0;
        WebStreamMod.reload();
        src.sendFeedback(() -> Text.literal(tr("cmd.reload.done", WebStreamMod.workspace().activeProfileName())).formatted(Formatting.GREEN), true);
        return 1;
    }

    // ------------------------------------------------------------------ utilitaires

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    /** Lien cliquable (ouvre le navigateur) suivi d'un bouton pour copier l'adresse. */
    private static MutableText clickable(String url) {
        MutableText link = Text.literal(url).styled(s -> s.withColor(Formatting.AQUA).withUnderline(true)
            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url)));
        MutableText copy = Text.literal(" [" + tr("cmd.copy") + "]").styled(s -> s.withColor(Formatting.GRAY)
            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, url)));
        return link.append(copy);
    }
}
