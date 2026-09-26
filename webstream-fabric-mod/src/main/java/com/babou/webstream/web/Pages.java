package com.babou.webstream.web;

import com.babou.webstream.core.ExportData.ImportResult;
import com.babou.webstream.core.Workspace;
import com.babou.webstream.core.Workspace.FamilyInfo;
import com.babou.webstream.core.Workspace.LibraryFile;
import com.babou.webstream.core.Workspace.ProfileInfo;
import com.babou.webstream.core.Workspace.ScreenGroup;
import com.babou.webstream.core.Workspace.ScreenInfo;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.babou.webstream.web.Html.esc;
import static com.babou.webstream.web.Html.num;
import static com.babou.webstream.web.Html.urlEnc;

/**
 * Génération des pages HTML. Le style est dans styles.css, les comportements dans app.js,
 * les textes dans /web/lang/*.json (clés passées à t()).
 */
final class Pages {
    private final Workspace ws;
    private final WebSettings settings;
    private final String version;
    private final Branding branding;

    interface Branding {
        boolean hasBanner();

        boolean hasIcon();
    }

    Pages(Workspace ws, WebSettings settings, String version, Branding branding) {
        this.ws = ws;
        this.settings = settings;
        this.version = version;
        this.branding = branding;
    }

    /** Texte traduit dans la langue des réglages ; les paramètres HTML doivent déjà être échappés. */
    private String t(String key, Object... args) {
        return settings.tr(key, args);
    }

    // ------------------------------------------------------------------ adresses

    /** Adresse à donner à WebStreamer : publicUrl si configurée, sinon l'hôte utilisé pour ouvrir l'interface. */
    String baseUrl(Ctx c) {
        String pub = settings.publicUrl == null ? "" : settings.publicUrl.trim();
        if (!pub.isEmpty()) return pub.endsWith("/") ? pub.substring(0, pub.length() - 1) : pub;
        String host = c == null ? null : c.header("Host");
        return "http://" + (host == null || host.isBlank() ? "localhost:" + settings.port : host);
    }

    /** Adresse complète d'un écran, avec le paramètre configuré (ex. ?refresh=1). */
    String screenUrl(Ctx c, String ref) {
        String q = settings.urlQuery == null ? "" : settings.urlQuery.trim();
        return baseUrl(c) + "/" + urlEnc(ref) + ".png" + (q.isEmpty() ? "" : "?" + q);
    }

    // ------------------------------------------------------------------ petits éléments

    private static final Map<String, String> ICONS = Map.ofEntries(
        Map.entry("home", "<path d=\"M3 11l9-8 9 8\"/><path d=\"M5 10v10h14V10\"/>"),
        Map.entry("image", "<rect x=\"3\" y=\"4\" width=\"18\" height=\"16\" rx=\"2\"/><circle cx=\"9\" cy=\"10\" r=\"1.6\"/><path d=\"M21 16l-5-5-9 9\"/>"),
        Map.entry("monitor", "<rect x=\"2\" y=\"4\" width=\"20\" height=\"13\" rx=\"2\"/><path d=\"M8 21h8M12 17v4\"/>"),
        Map.entry("folder", "<path d=\"M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z\"/>"),
        Map.entry("layers", "<path d=\"M12 3l9 5-9 5-9-5z\"/><path d=\"M3 13l9 5 9-5\"/>"),
        Map.entry("database", "<ellipse cx=\"12\" cy=\"5\" rx=\"8\" ry=\"3\"/><path d=\"M4 5v6c0 1.7 3.6 3 8 3s8-1.3 8-3V5\"/><path d=\"M4 11v6c0 1.7 3.6 3 8 3s8-1.3 8-3v-6\"/>"),
        Map.entry("settings", "<path d=\"M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12M20 18h0\"/><circle cx=\"16\" cy=\"6\" r=\"2\"/><circle cx=\"10\" cy=\"12\" r=\"2\"/><circle cx=\"18\" cy=\"18\" r=\"2\"/>"),
        Map.entry("plus", "<path d=\"M12 5v14M5 12h14\"/>"),
        Map.entry("copy", "<rect x=\"9\" y=\"9\" width=\"11\" height=\"11\" rx=\"2\"/><path d=\"M5 15V6a2 2 0 0 1 2-2h9\"/>"),
        Map.entry("upload", "<path d=\"M12 16V4M7 9l5-5 5 5\"/><path d=\"M4 20h16\"/>"),
        Map.entry("download", "<path d=\"M12 4v12M7 11l5 5 5-5\"/><path d=\"M4 20h16\"/>"),
        Map.entry("search", "<circle cx=\"11\" cy=\"11\" r=\"7\"/><path d=\"M21 21l-4.3-4.3\"/>"),
        Map.entry("x", "<path d=\"M6 6l12 12M18 6L6 18\"/>"),
        Map.entry("globe", "<circle cx=\"12\" cy=\"12\" r=\"9\"/><path d=\"M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18\"/>"),
        Map.entry("check", "<path d=\"M5 12l5 5 9-10\"/>"),
        Map.entry("menu", "<path d=\"M4 7h16M4 12h16M4 17h16\"/>"),
        Map.entry("trash", "<path d=\"M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3\"/>"),
        Map.entry("dots", "<circle cx=\"5\" cy=\"12\" r=\"1.3\"/><circle cx=\"12\" cy=\"12\" r=\"1.3\"/><circle cx=\"19\" cy=\"12\" r=\"1.3\"/>"));

    static String icon(String name) {
        return "<svg class=\"i\" viewBox=\"0 0 24 24\" aria-hidden=\"true\">" + ICONS.getOrDefault(name, "") + "</svg>";
    }

    static String flash(String kind, String html) {
        return "<div class=\"alert alert-" + kind + "\">" + html + "</div>\n";
    }

    /** 1536 -> « 2 Ko / 2 KB » ; 3 145 728 -> « 3,0 Mo / 3.0 MB » (séparateur décimal selon la langue). */
    String fmtSize(long bytes) {
        if (bytes < 1024) return bytes + " " + t("unit.b");
        if (bytes < 1024 * 1024) return String.format(I18n.locale(settings.language), "%.0f %s", bytes / 1024.0, t("unit.kb"));
        return String.format(I18n.locale(settings.language), "%.1f %s", bytes / 1048576.0, t("unit.mb"));
    }

    private static String pageHead(String title, String sub, String actionsHtml) {
        return "<div class=\"page-head\"><div><h1>" + esc(title) + "</h1>"
            + (sub == null ? "" : "<p class=\"sub\">" + sub + "</p>") + "</div>"
            + (actionsHtml == null ? "" : "<div class=\"actions\">" + actionsHtml + "</div>") + "</div>\n";
    }

    // ------------------------------------------------------------------ mise en page

    String layout(Ctx c, String title, String page, String body) {
        StringBuilder sb = new StringBuilder(body.length() + 6000);
        sb.append("<!DOCTYPE html>\n<html lang=\"").append(esc(settings.language)).append("\">\n<head>\n")
            .append("  <meta charset=\"UTF-8\">\n")
            .append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
            .append("  <meta name=\"color-scheme\" content=\"dark\">\n")
            .append("  <link rel=\"icon\" type=\"image/png\" href=\"").append(branding.hasIcon() ? "/branding/icon" : "/static/images/icon.png").append("\">\n")
            .append("  <title>").append(esc(title)).append(" - WebStream Manager</title>\n")
            .append("  <link rel=\"stylesheet\" href=\"/static/styles.css\">\n")
            .append("</head>\n<body data-page=\"").append(esc(page)).append("\" data-base-url=\"").append(esc(baseUrl(c)))
            .append("\" data-url-query=\"").append(esc(settings.urlQuery)).append("\">\n<div class=\"app\">\n");

        // barre latérale
        sb.append("  <aside class=\"sidebar\" id=\"sidebar\">\n");
        if (branding.hasBanner()) {
            sb.append("    <a href=\"/\"><img src=\"/branding/banner\" alt=\"WebStream Manager\" class=\"banner-img\" /></a>\n");
        } else {
            sb.append("    <a href=\"/\" class=\"brand\"><img src=\"/static/images/icon.png\" alt=\"\" class=\"brand-icon\" /><span>WEB<b>STREAM</b></span></a>\n");
        }
        profileSwitch(sb);
        sb.append("    <nav class=\"nav\" aria-label=\"").append(esc(t("nav.aria"))).append("\">\n");
        nav(sb, "/", "home", t("nav.home"), "home", page);
        nav(sb, "/screens", "monitor", t("nav.screens"), "screens", page);
        nav(sb, "/library", "image", t("nav.library"), "library", page);
        nav(sb, "/families", "folder", t("nav.families"), "families", page);
        nav(sb, "/profiles", "layers", t("nav.profiles"), "profiles", page);
        nav(sb, "/data", "database", t("nav.data"), "data", page);
        nav(sb, "/settings", "settings", t("nav.settings"), "settings", page);
        sb.append("    </nav>\n    <div class=\"sidebar-foot\">\n")
            .append("      <span><span class=\"dot ok\"></span>").append(t("side.interface", settings.port)).append("</span>\n");
        if (settings.publicPort > 0) {
            sb.append("      <span><span class=\"dot ok\"></span>").append(t("side.public", settings.publicPort)).append("</span>\n");
        } else {
            sb.append("      <a href=\"/settings\" title=\"").append(esc(t("side.notSharedTitle"))).append("\"><span class=\"dot warn\"></span>")
                .append(t("side.notShared")).append("</a>\n");
        }
        sb.append("      <span class=\"clock\" id=\"clock\" aria-label=\"").append(esc(t("clock.aria"))).append("\"></span>\n")
            .append("    </div>\n  </aside>\n  <div class=\"backdrop\" id=\"backdrop\"></div>\n");

        // contenu
        sb.append("  <div class=\"main\">\n    <header class=\"topbar\">\n")
            .append("      <button class=\"nav-toggle\" id=\"navToggle\" aria-label=\"").append(esc(t("menu.aria"))).append("\" aria-controls=\"sidebar\" aria-expanded=\"false\">")
            .append(icon("menu")).append("</button>\n")
            .append("      <strong>").append(esc(ws.activeProfileName())).append("</strong>\n    </header>\n")
            .append("    <main class=\"content\">\n").append(body).append("\n    </main>\n")
            .append("    <footer class=\"site-foot\">WebStream Manager ").append(esc(version)).append(" // signal ok</footer>\n  </div>\n</div>\n")
            .append("<div class=\"toasts\" id=\"toasts\" aria-live=\"polite\"></div>\n")
            .append("<script>window.WS_I18N=").append(I18n.toJson(I18n.forScript(settings.language, "js.", "err."))).append(";</script>\n")
            .append("<script src=\"/static/app.js\"></script>\n</body>\n</html>\n");
        return sb.toString();
    }

    private static void nav(StringBuilder sb, String href, String icon, String label, String key, String current) {
        sb.append("      <a href=\"").append(href).append("\"").append(key.equals(current) ? " class=\"active\" aria-current=\"page\"" : "").append(">")
            .append(icon(icon)).append("<span>").append(esc(label)).append("</span></a>\n");
    }

    private void profileSwitch(StringBuilder sb) {
        String world = ws.currentWorld();
        int screens = ws.listScreens().size();
        sb.append("    <div class=\"profile-switch\" id=\"profileSwitch\">\n")
            .append("      <button type=\"button\" class=\"ps-current\" aria-haspopup=\"true\" aria-expanded=\"false\">\n")
            .append("        <span class=\"ps-label\">").append(t("ps.live")).append("</span>\n")
            .append("        <span class=\"ps-name\">").append(esc(ws.activeProfileName())).append("</span>\n")
            .append("        <span class=\"ps-sub\">").append(world == null ? "" : t("ps.world", esc(world)) + " · ").append(t("ps.screens", screens)).append("</span>\n")
            .append("        <span class=\"ps-caret\">▾</span>\n      </button>\n")
            .append("      <div class=\"ps-menu\" hidden>\n");
        int channel = 0;
        for (ProfileInfo p : ws.listProfiles()) {
            sb.append("        <button type=\"button\" class=\"ps-item").append(p.active() ? " active" : "").append("\" data-profile-id=\"").append(esc(p.id()))
                .append("\"><span>").append(esc(p.name())).append("</span><small>").append(channelLabel(++channel)).append("</small></button>\n");
        }
        sb.append("        <a class=\"ps-manage\" href=\"/profiles\">").append(t("ps.manage")).append("</a>\n      </div>\n    </div>\n");
    }

    /** « CH 01 » : chaque profil est une chaîne que l'on peut diffuser sur les écrans. */
    static String channelLabel(int n) {
        return String.format("CH %02d", n);
    }

    String notFound(Ctx c) {
        return layout(c, t("nf.title"), "", "<div class=\"empty\"><strong>" + t("nf.text") + "</strong>"
            + "<a class=\"btn btn-primary\" href=\"/\">" + t("nf.back") + "</a></div>");
    }

    // ------------------------------------------------------------------ tableau de bord

    String home(Ctx c) {
        List<LibraryFile> lib = ws.listLibraryFilesInfo();
        long bytes = lib.stream().mapToLong(LibraryFile::size).sum();
        int screens = ws.listScreens().size();
        long assigned = ws.listScreens().stream().filter(s -> s.content() != null).count();
        String example = screens > 0 ? screenUrl(c, ws.listScreens().get(0).ref()) : screenUrl(c, t("home.exampleRef"));

        StringBuilder b = new StringBuilder();
        b.append(pageHead(t("nav.home"), t("home.sub", esc(ws.activeProfileName()))
            + (ws.currentWorld() == null ? "" : t("home.subWorld", esc(ws.currentWorld()))), null));

        b.append("<div class=\"stats\">\n");
        stat(b, "/screens", "monitor", String.valueOf(screens), t("home.stat.screens", assigned));
        stat(b, "/library", "image", String.valueOf(lib.size()), t("home.stat.images", fmtSize(bytes)));
        stat(b, "/families", "folder", String.valueOf(ws.listFamilies().size()), t("home.stat.families"));
        stat(b, "/profiles", "layers", String.valueOf(ws.listProfiles().size()), t("home.stat.profiles"));
        b.append("</div>\n<div class=\"card-grid\">\n");

        b.append("<section class=\"card\"><h2>").append(t("home.addr.title")).append("</h2>\n")
            .append("<p class=\"sub\">").append(t("home.addr.sub")).append("</p>\n")
            .append("<div class=\"copybox\"><code>").append(esc(example)).append("</code>")
            .append("<button type=\"button\" class=\"btn btn-sm\" data-copy=\"").append(esc(example)).append("\">").append(icon("copy")).append(t("btn.copy")).append("</button></div>\n")
            .append("<p class=\"hint\" style=\"margin-top:.7rem\">").append(t("home.addr.hint")).append("</p></section>\n");

        b.append("<section class=\"card\"><h2>").append(t("home.mp.title")).append("</h2>\n");
        if (settings.publicPort > 0) {
            boolean hasUrl = settings.publicUrl != null && !settings.publicUrl.isBlank();
            b.append("<p><span class=\"chip chip-ok\">").append(icon("globe")).append(t("home.mp.open", settings.publicPort)).append("</span></p>\n")
                .append(hasUrl ? "<p class=\"muted\">" + t("home.mp.url", esc(settings.publicUrl)) + "</p>\n"
                    : "<p class=\"muted\">" + t("home.mp.noUrl") + "</p>\n");
        } else {
            b.append("<p><span class=\"chip chip-warn\">").append(t("home.mp.closed")).append("</span></p>\n")
                .append("<p class=\"muted\">").append(t("home.mp.closedText")).append("</p>\n");
        }
        b.append("<div class=\"actions\"><a class=\"btn btn-primary\" href=\"/settings\">").append(icon("settings")).append(t("home.mp.settings")).append("</a>")
            .append("<button type=\"button\" class=\"btn\" id=\"quickTest\">").append(t("btn.test")).append("</button><span id=\"quickTestResult\" class=\"muted\"></span></div></section>\n</div>\n");

        b.append("<section class=\"card\"><h2>").append(t("home.steps.title")).append("</h2><ol class=\"steps\">\n")
            .append("<li><span>").append(t("home.steps.1")).append("</span></li>\n")
            .append("<li><span>").append(t("home.steps.2")).append("</span></li>\n")
            .append("<li><span>").append(t("home.steps.3")).append("</span></li>\n")
            .append("<li><span>").append(t("home.steps.4")).append("</span></li>\n")
            .append("</ol></section>\n");
        return b.toString();
    }

    private static void stat(StringBuilder b, String href, String icon, String number, String label) {
        b.append("<a class=\"stat\" href=\"").append(href).append("\"><span class=\"ico\">").append(icon(icon)).append("</span><span><span class=\"num\">")
            .append(esc(number)).append("</span><br><span class=\"lbl\">").append(esc(label)).append("</span></span></a>\n");
    }

    // ------------------------------------------------------------------ bibliothèque

    String library(Ctx c, List<LibraryFile> files, Map<String, List<String>> usage, String errorHtml) {
        long bytes = files.stream().mapToLong(LibraryFile::size).sum();
        StringBuilder b = new StringBuilder();
        b.append(pageHead(t("nav.library"), esc(t("lib.sub", files.size(), fmtSize(bytes))),
            "<button type=\"button\" class=\"btn btn-primary\" id=\"uploadBtn\">" + icon("upload") + t("lib.upload") + "</button>"));
        if (errorHtml != null) b.append(flash("error", errorHtml));

        b.append("<form id=\"uploadForm\" action=\"/library/upload\" method=\"post\" enctype=\"multipart/form-data\" hidden>")
            .append("<input type=\"file\" id=\"fileInput\" name=\"files\" accept=\"image/*\" multiple /></form>\n")
            .append("<div class=\"dropzone\" id=\"dropzone\" tabindex=\"0\" role=\"button\"><strong>").append(t("lib.drop.title")).append("</strong>")
            .append("<span>").append(t("lib.drop.sub")).append("</span>")
            .append("<div class=\"progress\" id=\"uploadProgress\" hidden style=\"width:min(420px,90%)\"><i></i></div></div>\n");

        b.append("<div class=\"toolbar\"><div class=\"searchbox grow\">").append(icon("search"))
            .append("<input type=\"search\" id=\"searchInput\" placeholder=\"").append(esc(t("lib.search"))).append("\" autocomplete=\"off\" aria-label=\"").append(esc(t("search.aria"))).append("\" /></div>\n")
            .append("<div class=\"seg\" id=\"sizeSeg\" role=\"group\" aria-label=\"").append(esc(t("lib.size.aria"))).append("\">")
            .append("<button type=\"button\" class=\"active\" data-size=\"small\">").append(t("lib.size.small")).append("</button>")
            .append("<button type=\"button\" data-size=\"medium\">").append(t("lib.size.medium")).append("</button>")
            .append("<button type=\"button\" data-size=\"large\">").append(t("lib.size.large")).append("</button></div></div>\n");

        b.append("<div id=\"library-grid\" class=\"grid size-small\">\n");
        if (files.isEmpty()) {
            b.append("<div class=\"empty no-files\"><strong>").append(t("lib.empty.title")).append("</strong>").append(t("lib.empty.text")).append("</div>\n");
        }
        for (LibraryFile f : files) {
            List<String> used = usage.getOrDefault(f.name(), List.of());
            JsonArray refs = new JsonArray();
            used.forEach(refs::add);
            b.append("<div class=\"library-item\" tabindex=\"0\" data-file=\"").append(esc(f.name())).append("\" data-filename=\"").append(esc(f.name().toLowerCase(Locale.ROOT)))
                .append("\" data-size=\"").append(esc(fmtSize(f.size()))).append("\" data-usage=\"").append(esc(refs.toString())).append("\">\n")
                .append("  <img src=\"/thumb/").append(urlEnc(f.name())).append("\" alt=\"").append(esc(f.name())).append("\" loading=\"lazy\" decoding=\"async\" />\n")
                .append("  <div class=\"meta\"><span class=\"name\" title=\"").append(esc(f.name())).append("\">").append(esc(f.name())).append("</span>")
                .append("<span><span class=\"chip chip-muted\">").append(esc(fmtSize(f.size()))).append("</span> ")
                .append(used.isEmpty() ? "" : "<span class=\"chip\">" + t("lib.usedBy", used.size()) + "</span>").append("</span></div>\n</div>\n");
        }
        b.append("</div>\n");
        appendModalShell(b, "libraryModal");
        return b.toString();
    }

    private void appendModalShell(StringBuilder b, String id) {
        b.append("<div class=\"modal\" id=\"").append(id).append("\" role=\"dialog\" aria-modal=\"true\">\n<div class=\"modal-backdrop\" data-close></div>\n")
            .append("<div class=\"modal-dialog\"><div class=\"modal-head\"><div class=\"modal-title\"></div>")
            .append("<button type=\"button\" class=\"btn btn-ghost btn-icon\" data-close aria-label=\"").append(esc(t("modal.close"))).append("\">").append(icon("x")).append("</button></div>\n")
            .append("<div class=\"modal-body\"></div></div></div>\n");
    }

    // ------------------------------------------------------------------ écrans

    private String tile(Ctx c, ScreenInfo s, boolean showFamily) {
        double wd = s.width() == null ? 16 : s.width();
        double hd = s.height() == null ? 9 : s.height();
        String src = s.content() != null
            ? "/thumb/" + urlEnc(s.content())
            : "/screens/placeholder/" + Math.round(wd * 100) + "/" + Math.round(hd * 100) + ".svg";
        String url = screenUrl(c, s.ref());
        StringBuilder b = new StringBuilder();
        b.append("<div class=\"screen-card\" data-search=\"").append(esc((s.ref() + " " + (s.family() == null ? "" : s.family())).toLowerCase(Locale.ROOT))).append("\">\n")
            .append("  <button type=\"button\" class=\"screen-tile\" data-ref=\"").append(esc(s.ref())).append("\" data-content=\"").append(esc(s.content()))
            .append("\" data-width=\"").append(num(s.width())).append("\" data-height=\"").append(num(s.height())).append("\" data-family-id=\"")
            .append(s.familyId() == null ? "" : s.familyId()).append("\">\n")
            .append("    <div class=\"sc-frame\" style=\"--w: ").append(num(wd)).append("; --h: ").append(num(hd)).append(";\">")
            .append("<img src=\"").append(src).append("\" alt=\"").append(esc(s.ref())).append("\" loading=\"lazy\" decoding=\"async\" /></div>\n")
            .append("    <div class=\"sc-meta\"><div class=\"sc-title\"><code>").append(esc(s.ref())).append("</code></div>\n")
            .append("      <div class=\"sc-sub\">").append(s.width() == null ? "—" : num(s.width())).append(" × ").append(s.height() == null ? "—" : num(s.height())).append("</div>\n");
        if (showFamily) b.append("      <div class=\"sc-family\">").append(s.family() == null ? t("group.none") : esc(s.family())).append("</div>\n");
        b.append("    </div>\n  </button>\n")
            .append("  <span class=\"tile-status chip ").append(s.content() == null ? "chip-warn\">" + t("tile.noImage") : "chip-ok\">" + t("tile.hasImage")).append("</span>\n")
            .append("  <button type=\"button\" class=\"btn btn-sm btn-icon tile-copy\" data-copy=\"").append(esc(url)).append("\" title=\"").append(esc(t("tile.copyTitle")))
            .append("\" aria-label=\"").append(esc(t("tile.copyTitle"))).append("\">").append(icon("copy")).append("</button>\n</div>\n");
        return b.toString();
    }

    String screens(Ctx c, String mode) {
        boolean all = mode.equals("all");
        StringBuilder b = new StringBuilder();
        b.append(pageHead(t("nav.screens"), t("scr.sub", esc(ws.activeProfileName())),
            "<button type=\"button\" class=\"btn btn-primary\" id=\"addScreenBtn\">" + icon("plus") + t("scr.new") + "</button>"));
        b.append("<div class=\"toolbar\"><div class=\"seg\">")
            .append("<a class=\"").append(all ? "" : "active").append("\" href=\"/screens?mode=family\">").append(t("scr.byFamily")).append("</a>")
            .append("<a class=\"").append(all ? "active" : "").append("\" href=\"/screens?mode=all\">").append(t("scr.all")).append("</a></div>\n")
            .append("<div class=\"searchbox grow\">").append(icon("search")).append("<input type=\"search\" id=\"screenFilter\" placeholder=\"").append(esc(t("scr.filter")))
            .append("\" autocomplete=\"off\" aria-label=\"").append(esc(t("scr.filter.aria"))).append("\" /></div>\n")
            .append("<a class=\"btn\" href=\"/families\">").append(icon("folder")).append(t("scr.manageFamilies")).append("</a></div>\n");

        if (all) {
            List<ScreenInfo> list = ws.listScreens();
            if (list.isEmpty()) b.append(emptyScreens());
            b.append("<div class=\"screen-grid\">\n");
            for (ScreenInfo s : list) b.append(tile(c, s, true));
            b.append("</div>\n");
        } else {
            List<ScreenGroup> groups = ws.listScreensByFamily();
            if (groups.isEmpty() || groups.stream().allMatch(g -> g.screens().isEmpty())) b.append(emptyScreens());
            for (ScreenGroup g : groups) {
                b.append("<details class=\"family-group\" open>\n<summary><strong>").append(g.familyId() == null ? t("group.none") : esc(g.family()))
                    .append("</strong> <span class=\"chip chip-muted\">").append(g.screens().size()).append("</span></summary>\n<div class=\"screen-grid\">\n");
                for (ScreenInfo s : g.screens()) b.append(tile(c, s, false));
                b.append("</div>\n</details>\n");
            }
        }
        appendModalShell(b, "screenModal");

        JsonArray fams = new JsonArray();
        for (FamilyInfo f : ws.listFamilies()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", f.id());
            o.addProperty("name", f.name());
            fams.add(o);
        }
        JsonArray lib = new JsonArray();
        ws.listLibraryFiles().forEach(lib::add);
        b.append("<div id=\"screens-data\" data-families=\"").append(esc(fams.toString())).append("\" data-library=\"").append(esc(lib.toString())).append("\" hidden></div>\n");
        return b.toString();
    }

    private String emptyScreens() {
        return "<div class=\"empty\" style=\"margin-bottom:1rem\"><strong>" + t("scr.empty.title") + "</strong>" + t("scr.empty.text") + "</div>\n";
    }

    // ------------------------------------------------------------------ familles

    String families(Ctx c, String errorHtml) {
        List<FamilyInfo> list = ws.listFamilies();
        StringBuilder b = new StringBuilder();
        b.append(pageHead(t("nav.families"), t("fam.sub", esc(ws.activeProfileName())),
            "<a class=\"btn\" href=\"/screens\">" + t("fam.back") + "</a>"));
        if (errorHtml != null) b.append(flash("error", errorHtml));
        b.append("<form class=\"card\" method=\"post\" action=\"/families/create\"><h2>").append(t("fam.new")).append("</h2>")
            .append("<div class=\"form-row\"><input name=\"name\" placeholder=\"").append(esc(t("fam.placeholder"))).append("\" required maxlength=\"60\" aria-label=\"").append(esc(t("fam.nameAria"))).append("\" />")
            .append("<button class=\"btn btn-primary\" type=\"submit\">").append(icon("plus")).append(t("btn.create")).append("</button></div></form>\n");
        b.append("<div class=\"card\"><h2>").append(t("fam.list")).append("</h2>\n");
        if (list.isEmpty()) {
            b.append("<div class=\"empty\"><strong>").append(t("fam.empty.title")).append("</strong>").append(t("fam.empty.text")).append("</div>\n");
        } else {
            b.append("<table class=\"table\"><thead><tr><th>").append(t("th.name")).append("</th><th>").append(t("th.screens")).append("</th><th></th></tr></thead><tbody>\n");
            for (FamilyInfo f : list) {
                b.append("<tr><td><strong>").append(esc(f.name())).append("</strong></td><td>").append(f.screenCount()).append("</td><td style=\"text-align:right\">")
                    .append("<form method=\"post\" action=\"/families/").append(f.id()).append("/delete\" data-confirm=\"").append(esc(t("fam.confirmDelete", f.name()))).append("\">")
                    .append("<button type=\"submit\" class=\"btn btn-sm btn-danger\"").append(f.screenCount() > 0 ? " disabled title=\"" + esc(t("fam.inUse")) + "\"" : "")
                    .append(">").append(icon("trash")).append(t("btn.delete")).append("</button></form></td></tr>\n");
            }
            b.append("</tbody></table>\n");
        }
        b.append("</div>\n");
        return b.toString();
    }

    // ------------------------------------------------------------------ profils

    String profiles(Ctx c, String flashHtml, String flashKind) {
        List<ProfileInfo> list = ws.listProfiles();
        String world = ws.currentWorld();
        StringBuilder b = new StringBuilder();
        b.append(pageHead(t("nav.profiles"), t("prof.sub"), null));
        if (flashHtml != null) b.append(flash(flashKind, flashHtml));
        if (world != null) {
            b.append(flash("ok", "<p>" + t("prof.world", esc(world)) + "</p>"));
        }

        b.append("<div class=\"profile-grid\">\n");
        int channel = 0;
        for (ProfileInfo p : list) {
            String id = esc(p.id());
            b.append("<article class=\"profile-card").append(p.active() ? " active" : "").append("\">\n<div class=\"top\"><div><div class=\"channel\">")
                .append(channelLabel(++channel)).append("</div><h3>").append(esc(p.name())).append("</h3>")
                .append("<div class=\"profile-meta\" style=\"margin-top:.4rem\"><span class=\"chip chip-muted\">").append(t("prof.chip.screens", p.screenCount())).append("</span>")
                .append("<span class=\"chip chip-muted\">").append(t("prof.chip.families", p.familyCount())).append("</span>");
            for (String w : p.worlds()) b.append("<span class=\"chip\">").append(t("prof.chip.world", esc(w))).append("</span>");
            b.append("</div></div>").append(p.active() ? "<span class=\"chip chip-live\">" + t("prof.live") + "</span>" : "").append("</div>\n");

            if (p.previews().isEmpty()) {
                b.append("<div class=\"mosaic empty-m\">").append(t("prof.noImage")).append("</div>\n");
            } else {
                b.append("<div class=\"mosaic\">");
                for (String f : p.previews()) b.append("<img src=\"/thumb/").append(urlEnc(f)).append("\" alt=\"\" loading=\"lazy\" decoding=\"async\" />");
                for (int i = p.previews().size(); i < 4; i++) b.append("<span></span>");
                b.append("</div>\n");
            }

            b.append("<div class=\"profile-actions\">\n");
            if (!p.active()) {
                b.append("<form method=\"post\" action=\"/profiles/").append(id).append("/activate\">")
                    .append(world != null ? "<input type=\"hidden\" name=\"bind\" value=\"1\" />" : "")
                    .append("<button type=\"submit\" class=\"btn btn-primary\">").append(t("btn.activate")).append("</button></form>\n");
            }
            b.append("<details class=\"more\"><summary class=\"btn btn-icon\" aria-label=\"").append(esc(t("prof.more"))).append("\">").append(icon("dots")).append("</summary><div class=\"more-menu\">\n")
                .append("<form method=\"post\" action=\"/profiles/").append(id).append("/duplicate\"><button type=\"submit\" class=\"btn btn-sm\">").append(icon("copy")).append(t("btn.duplicate")).append("</button></form>\n")
                .append("<form method=\"post\" action=\"/profiles/").append(id).append("/rename\"><input name=\"name\" value=\"").append(esc(p.name()))
                .append("\" maxlength=\"60\" aria-label=\"").append(esc(t("prof.newName"))).append("\" /><button type=\"submit\" class=\"btn btn-sm\">").append(t("btn.ok")).append("</button></form>\n")
                .append("<a class=\"btn btn-sm\" href=\"/data/export?profile=").append(id).append("\">").append(icon("download")).append(t("prof.export")).append("</a>\n")
                .append("<form method=\"post\" action=\"/profiles/").append(id).append("/delete\" data-confirm=\"").append(esc(t("prof.confirmDelete", p.name()))).append("\">")
                .append("<button type=\"submit\" class=\"btn btn-sm btn-danger\"").append(p.active() ? " disabled title=\"" + esc(t("prof.activeNoDelete")) + "\"" : "").append(">")
                .append(icon("trash")).append(t("btn.delete")).append("</button></form>\n</div></details>\n</div>\n</article>\n");
        }

        b.append("<form class=\"profile-card new\" method=\"post\" action=\"/profiles/create\">\n<h3>").append(icon("plus")).append(" ").append(t("prof.new.title")).append("</h3>\n")
            .append("<label class=\"field\">").append(t("prof.new.name")).append("<input name=\"name\" placeholder=\"").append(esc(t("prof.new.placeholder"))).append("\" required maxlength=\"60\" /></label>\n")
            .append("<label class=\"field\">").append(t("prof.new.clone")).append("<select name=\"cloneFrom\"><option value=\"\">").append(t("prof.new.empty")).append("</option>");
        for (ProfileInfo p : list) b.append("<option value=\"").append(esc(p.id())).append("\">").append(esc(p.name())).append("</option>");
        b.append("</select></label>\n<button type=\"submit\" class=\"btn btn-primary\">").append(t("prof.new.submit")).append("</button>\n</form>\n</div>\n");
        return b.toString();
    }

    // ------------------------------------------------------------------ données (export / import)

    String data(Ctx c, ImportResult result, String errorHtml) {
        List<ProfileInfo> profiles = ws.listProfiles();
        StringBuilder b = new StringBuilder();
        b.append(pageHead(t("nav.data"), t("data.sub"), null));
        if (errorHtml != null) b.append(flash("error", "<p>" + errorHtml + "</p>"));
        if (result != null) {
            StringBuilder r = new StringBuilder("<p>").append(t("data.res.done", esc(result.profileName))).append("</p><ul>")
                .append("<li>").append(t("data.res.added", result.screensAdded)).append("</li>")
                .append("<li>").append(t("data.res.updated", result.screensUpdated)).append("</li>")
                .append("<li>").append(t("data.res.skipped", result.screensSkipped)).append("</li>")
                .append("<li>").append(t("data.res.families", result.familiesAdded)).append("</li>");
            if (result.screensInvalid > 0) r.append("<li>").append(t("data.res.invalid", result.screensInvalid)).append("</li>");
            r.append("</ul>");
            if (!result.missingFiles.isEmpty()) {
                r.append("<p style=\"margin-top:.6rem\">").append(t("data.res.missing", result.missingFiles.size())).append("</p><ul>");
                for (String f : result.missingFiles) r.append("<li><code>").append(esc(f)).append("</code></li>");
                r.append("</ul>");
            }
            b.append(flash(result.missingFiles.isEmpty() ? "ok" : "warn", r.toString()));
        }

        b.append("<div class=\"card-grid\">\n<section class=\"card\"><h2>").append(t("data.export.title")).append("</h2>\n")
            .append("<p class=\"muted\">").append(t("data.export.text", esc(ws.activeProfileName()))).append("</p>\n")
            .append("<a class=\"btn btn-primary\" href=\"/data/export\">").append(icon("download")).append(t("data.export.btn")).append("</a></section>\n");

        b.append("<form class=\"card\" method=\"post\" action=\"/data/import\" enctype=\"multipart/form-data\"><h2>").append(t("data.import.title")).append("</h2>\n")
            .append("<p class=\"muted\">").append(t("data.import.text")).append("</p>\n")
            .append("<label class=\"field\">").append(t("data.import.file")).append("<input type=\"file\" name=\"files\" accept=\".json\" required /></label>\n")
            .append("<div class=\"opt\"><strong>").append(t("data.import.into")).append("</strong>\n")
            .append("<label><input type=\"radio\" name=\"target\" value=\"active\" checked /> ").append(t("data.import.active", esc(ws.activeProfileName()))).append("</label>\n")
            .append("<label><input type=\"radio\" name=\"target\" value=\"new\" /> ").append(t("data.import.new"))
            .append(" <input name=\"newName\" placeholder=\"").append(esc(t("data.import.newName"))).append("\" maxlength=\"60\" style=\"width:auto;display:inline-block\" /></label>\n");
        if (profiles.size() > 1) {
            b.append("<label><input type=\"radio\" name=\"target\" value=\"other\" /> ").append(t("data.import.other")).append(" <select name=\"otherId\" style=\"width:auto;display:inline-block\">");
            for (ProfileInfo p : profiles) if (!p.active()) b.append("<option value=\"").append(esc(p.id())).append("\">").append(esc(p.name())).append("</option>");
            b.append("</select></label>\n");
        }
        b.append("</div>\n<div class=\"opt\"><strong>").append(t("data.import.exists")).append("</strong>\n")
            .append("<label><input type=\"radio\" name=\"mode\" value=\"skip\" checked /> ").append(t("data.import.skip")).append("</label>\n")
            .append("<label><input type=\"radio\" name=\"mode\" value=\"overwrite\" /> ").append(t("data.import.overwrite")).append("</label>\n")
            .append("<label><input type=\"radio\" name=\"mode\" value=\"replace\" /> ").append(t("data.import.replace")).append("</label></div>\n")
            .append("<button type=\"submit\" class=\"btn btn-primary\" data-confirm-if=\"mode=replace\" data-confirm-text=\"").append(esc(t("data.import.confirmReplace"))).append("\">")
            .append(icon("upload")).append(t("btn.import")).append("</button></form>\n</div>\n");
        return b.toString();
    }

    // ------------------------------------------------------------------ réglages

    private static String sw(String name, boolean checked, String label, String hint) {
        return "<label class=\"switch\"><input type=\"checkbox\" name=\"" + name + "\"" + (checked ? " checked" : "") + " /><span class=\"track\"></span>"
            + "<span class=\"label\">" + label + (hint == null ? "" : "<small>" + hint + "</small>") + "</span></label>";
    }

    String settings(Ctx c, WebSettings s, List<String> errors, String flashKind, String notice, List<String> lan, boolean passwordSet) {
        StringBuilder b = new StringBuilder();
        b.append(pageHead(t("nav.settings"), t("set.sub"), null));

        if (!errors.isEmpty()) {
            StringBuilder ul = new StringBuilder("<p><strong>").append(t("set.errors")).append("</strong></p><ul>");
            for (String e : errors) ul.append("<li>").append(esc(e)).append("</li>");
            b.append(flash("error", ul.append("</ul>").toString()));
        }
        if (notice != null) b.append(flash("warn", "<p>" + esc(notice) + "</p>"));
        if ("saved".equals(flashKind)) b.append(flash("ok", "<p>" + t("set.saved") + "</p>"));
        if ("restart".equals(flashKind)) {
            b.append("<div class=\"alert alert-ok\" id=\"restartNotice\" data-port=\"").append(s.port).append("\"><p>").append(t("set.restart")).append("</p></div>\n");
        }

        boolean publicOn = s.publicPort > 0;
        int suggestPort = publicOn ? s.publicPort : 8283;
        b.append("<form method=\"post\" action=\"/settings\" class=\"settings-layout\" id=\"settingsForm\">\n");

        // langue
        b.append("<section class=\"card\"><h2>").append(t("set.lang.title")).append("</h2>\n<div class=\"opt\">")
            .append("<label class=\"field\">").append(t("set.lang.label")).append("<select name=\"language\">");
        for (String lang : I18n.LANGUAGES) b.append(opt(lang, I18n.nativeName(lang), s.language));
        b.append("</select><span class=\"hint\">").append(t("set.lang.hint")).append("</span></label></div></section>\n");

        // multijoueur
        b.append("<section class=\"card\"><h2>").append(t("set.mp.title")).append("</h2>\n")
            .append("<p class=\"sub\">").append(t("set.mp.sub")).append("</p>\n")
            .append("<div class=\"opt\">").append(sw("publicEnabled", publicOn, t("set.mp.switch"), t("set.mp.switchHint"))).append("\n")
            .append("<div class=\"only-when\" data-when=\"publicEnabled\"").append(publicOn ? "" : " hidden").append(">")
            .append("<label class=\"field\">").append(t("set.mp.portLabel")).append("<input type=\"number\" name=\"publicPort\" min=\"1\" max=\"65535\" value=\"").append(suggestPort).append("\" id=\"publicPort\" />")
            .append("<span class=\"hint\">").append(t("set.mp.portHint")).append("</span></label></div></div>\n")
            .append("<div class=\"opt\"><label class=\"field\">").append(t("set.mp.urlLabel")).append("<input name=\"publicUrl\" id=\"publicUrl\" value=\"").append(esc(s.publicUrl))
            .append("\" placeholder=\"http://my-server.com:8283\" /><span class=\"hint\">").append(t("set.mp.urlHint")).append("</span></label>\n");
        if (!lan.isEmpty()) {
            b.append("<div><span class=\"hint\">").append(t("set.mp.lan")).append("</span><div class=\"suggest\" style=\"margin-top:.4rem\">");
            for (String ip : lan) b.append("<button type=\"button\" class=\"btn btn-sm\" data-suggest-ip=\"").append(esc(ip)).append("\"><span>http://").append(esc(ip)).append(":<span data-port-mirror>").append(suggestPort).append("</span></span></button>");
            b.append("</div></div>\n");
        }
        b.append("<div><button type=\"button\" class=\"btn\" id=\"testBtn\">").append(t("set.mp.test")).append("</button>\n")
            .append("<div class=\"test-result\" id=\"testResult\" aria-live=\"polite\"></div>\n")
            .append("<p class=\"hint\">").append(t("set.mp.testNote")).append("</p></div></div></section>\n");

        // accès
        b.append("<section class=\"card\"><h2>").append(t("set.acc.title")).append("</h2>\n<div class=\"opt\">")
            .append("<label class=\"field\">").append(t("set.acc.who")).append("<select name=\"bindAddress\">")
            .append(opt("127.0.0.1", t("set.acc.local"), s.bindAddress))
            .append(opt("0.0.0.0", t("set.acc.all"), s.bindAddress));
        if (!s.bindAddress.equals("127.0.0.1") && !s.bindAddress.equals("0.0.0.0")) b.append(opt(s.bindAddress, s.bindAddress, s.bindAddress));
        b.append("</select></label>\n");
        if (!s.isLoopbackBind() && !passwordSet) {
            b.append(flash("warn", "<p style=\"margin:0\">" + t("set.acc.noPassword") + "</p>"));
        }
        b.append("</div>\n<div class=\"opt\"><label class=\"field\">").append(t("set.acc.password")).append("<input type=\"password\" name=\"adminPassword\" autocomplete=\"new-password\" placeholder=\"")
            .append(esc(passwordSet ? t("set.acc.passwordSet") : t("set.acc.passwordNone"))).append("\" /><span class=\"hint\">").append(t("set.acc.passwordHint")).append("</span></label>\n");
        if (passwordSet) b.append("<label><input type=\"checkbox\" name=\"clearPassword\" /> ").append(t("set.acc.clear")).append("</label>\n");
        b.append("</div>\n<div class=\"opt\">").append(sw("trustLocalhost", s.trustLocalhost, t("set.acc.trust"), t("set.acc.trustHint"))).append("</div>\n")
            .append("<div class=\"opt\"><label class=\"field\">").append(t("set.acc.adminUrl")).append("<input name=\"adminUrl\" value=\"").append(esc(s.adminUrl))
            .append("\" placeholder=\"http://my-server.com:8282\" /><span class=\"hint\">").append(t("set.acc.adminUrlHint")).append("</span></label></div></section>\n");

        // profils
        b.append("<section class=\"card\"><h2>").append(t("set.prof.title")).append("</h2>\n<div class=\"opt\"><label class=\"field\">").append(t("set.prof.label")).append("<select name=\"newWorldProfile\">")
            .append(opt(Workspace.MODE_PER_WORLD, t("set.prof.perWorld"), s.newWorldProfile))
            .append(opt(Workspace.MODE_SHARED, t("set.prof.shared"), s.newWorldProfile))
            .append("</select><span class=\"hint\">").append(t("set.prof.hint")).append("</span></label></div></section>\n");

        // adresses et images
        b.append("<section class=\"card\"><h2>").append(t("set.adr.title")).append("</h2>\n<div class=\"opt\"><label class=\"field\">").append(t("set.adr.query")).append("<input name=\"urlQuery\" value=\"")
            .append(esc(s.urlQuery)).append("\" placeholder=\"refresh=1\" /><span class=\"hint\">").append(t("set.adr.queryHint")).append("</span></label></div>\n")
            .append("<div class=\"opt\">").append(sw("placeholderImage", s.placeholderImage, t("set.adr.placeholder"), t("set.adr.placeholderHint"))).append("</div>\n")
            .append("<div class=\"opt\"><label class=\"field\">").append(t("set.adr.maxUpload")).append("<input type=\"number\" name=\"maxUploadMb\" min=\"1\" max=\"2048\" value=\"")
            .append(s.maxUploadMb).append("\" /></label></div></section>\n");

        // avancé
        b.append("<section class=\"card\"><h2>").append(t("set.adv.title")).append("</h2>\n<div class=\"form-grid\">")
            .append("<label class=\"field\">").append(t("set.adv.port")).append("<input type=\"number\" name=\"port\" min=\"1\" max=\"65535\" value=\"").append(s.port).append("\" /></label>")
            .append("<label class=\"field\">").append(t("set.adv.publicBind")).append("<input name=\"publicBindAddress\" value=\"").append(esc(s.publicBindAddress)).append("\" /></label></div>\n")
            .append("<p class=\"hint\" style=\"margin-top:.7rem\">").append(t("set.adv.hint")).append("</p></section>\n");

        b.append("<div class=\"sticky-save\"><button type=\"submit\" class=\"btn btn-primary\">").append(t("set.saveBtn")).append("</button>")
            .append("<span class=\"muted\">").append(t("set.file")).append("</span></div>\n</form>\n");
        return b.toString();
    }

    private static String opt(String value, String label, String current) {
        return "<option value=\"" + esc(value) + "\"" + (value.equals(current) ? " selected" : "") + ">" + esc(label) + "</option>";
    }
}
