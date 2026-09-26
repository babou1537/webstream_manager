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
        src.sendError(Text.literal("WebStream n'est pas démarré (désactivé, ou erreur au lancement : voir les logs du serveur)."));
        return false;
    }

    private static int status(ServerCommandSource src) {
        if (!running(src)) return 0;
        Workspace ws = WebStreamMod.workspace();
        WebService web = WebStreamMod.service();
        String world = ws.currentWorld();
        src.sendFeedback(() -> Text.literal("WebStream — profil actif : ").formatted(Formatting.GRAY)
            .append(Text.literal(ws.activeProfileName()).formatted(Formatting.WHITE, Formatting.BOLD))
            .append(Text.literal(" (" + ws.listScreens().size() + " écran(s)" + (world == null ? "" : ", monde " + world) + ")").formatted(Formatting.GRAY)), false);
        src.sendFeedback(() -> Text.literal("Adresse des images : ").formatted(Formatting.GRAY)
            .append(Text.literal(web.baseUrl() + "/<écran>.png").formatted(Formatting.AQUA)), false);
        if (blank(WebStreamMod.CONFIG.publicUrl)) {
            src.sendFeedback(() -> Text.literal("Astuce : définissez « publicUrl » dans config/webstream.json pour que les autres joueurs voient les écrans.")
                .formatted(Formatting.YELLOW), false);
        }
        return 1;
    }

    private static int url(ServerCommandSource src, String ref) {
        if (!running(src)) return 0;
        ScreenInfo screen = WebStreamMod.workspace().getScreen(ref);
        if (screen == null) {
            src.sendError(Text.literal("Écran « " + ref + " » introuvable dans le profil « " + WebStreamMod.workspace().activeProfileName() + " »."));
            return 0;
        }
        String link = WebStreamMod.service().screenUrl(ref);
        src.sendFeedback(() -> Text.literal("Adresse de « " + ref + " » : ").formatted(Formatting.GRAY).append(clickable(link)), false);
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
            src.sendFeedback(() -> Text.literal("L'interface n'écoute que sur la machine du serveur (" + WebStreamMod.CONFIG.localAdminUrl()
                + "). Pour y accéder à distance, définissez « adminUrl », « bindAddress » et « adminPassword » dans config/webstream.json.")
                .formatted(Formatting.YELLOW), false);
            return 1;
        }
        src.sendFeedback(() -> Text.literal("Interface d'administration : ").formatted(Formatting.GRAY).append(clickable(link)), false);
        return 1;
    }

    private static int currentProfile(ServerCommandSource src) {
        if (!running(src)) return 0;
        Workspace ws = WebStreamMod.workspace();
        src.sendFeedback(() -> Text.literal("Profil actif : ").formatted(Formatting.GRAY)
            .append(Text.literal(ws.activeProfileName() + " (" + ws.activeProfileId() + ")").formatted(Formatting.WHITE)), false);
        return 1;
    }

    private static int listProfiles(ServerCommandSource src) {
        if (!running(src)) return 0;
        for (ProfileInfo p : WebStreamMod.workspace().listProfiles()) {
            MutableText line = Text.literal((p.active() ? "▶ " : "  ") + p.name()).formatted(p.active() ? Formatting.GREEN : Formatting.WHITE)
                .append(Text.literal("  [" + p.id() + "] " + p.screenCount() + " écran(s)"
                    + (p.worlds().isEmpty() ? "" : " — mondes : " + String.join(", ", p.worlds()))).formatted(Formatting.GRAY));
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
            src.sendError(Text.literal("Profil « " + id + " » introuvable. Voir /webstream profile list"));
            return 0;
        }
        src.sendFeedback(() -> Text.literal("Profil « " + ws.activeProfileName() + " » activé"
            + (ws.currentWorld() == null ? "" : " et retenu pour le monde « " + ws.currentWorld() + " »")
            + ". Les écrans se mettent à jour.").formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int reload(ServerCommandSource src) {
        if (!running(src)) return 0;
        WebStreamMod.reload();
        src.sendFeedback(() -> Text.literal("Profils rechargés depuis le disque. Profil actif : " + WebStreamMod.workspace().activeProfileName())
            .formatted(Formatting.GREEN), true);
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
        MutableText copy = Text.literal(" [copier]").styled(s -> s.withColor(Formatting.GRAY)
            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, url)));
        return link.append(copy);
    }
}
