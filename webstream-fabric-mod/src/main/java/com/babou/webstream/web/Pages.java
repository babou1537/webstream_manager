package com.babou.webstream.web;

import com.babou.webstream.core.ExportData.ImportResult;
import com.babou.webstream.core.Workspace;
import com.babou.webstream.core.Workspace.FamilyInfo;
import com.babou.webstream.core.Workspace.ProfileInfo;
import com.babou.webstream.core.Workspace.ScreenGroup;
import com.babou.webstream.core.Workspace.ScreenInfo;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

import static com.babou.webstream.web.Html.esc;
import static com.babou.webstream.web.Html.num;
import static com.babou.webstream.web.Html.urlEnc;

/** Génération des pages HTML (mêmes classes CSS et attributs data-* que l'ancienne interface EJS). */
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

    /** Adresse à donner à WebStreamer : publicUrl si configurée, sinon l'hôte utilisé pour ouvrir l'interface. */
    String baseUrl(Ctx c) {
        String pub = settings.publicUrl == null ? "" : settings.publicUrl.trim();
        if (!pub.isEmpty()) return pub.endsWith("/") ? pub.substring(0, pub.length() - 1) : pub;
        String host = c == null ? null : c.header("Host");
        return "http://" + (host == null || host.isBlank() ? "localhost:" + settings.port : host);
    }

    // ------------------------------------------------------------------ mise en page

    String layout(Ctx c, String title, String page, String body) {
        StringBuilder sb = new StringBuilder(body.length() + 4096);
        sb.append("<!DOCTYPE html>\n<html lang=\"fr\">\n<head>\n")
            .append("  <meta charset=\"UTF-8\">\n")
            .append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
            .append("  <link rel=\"icon\" type=\"image/png\" href=\"").append(branding.hasIcon() ? "/branding/icon" : "/static/images/icon.png").append("\">\n")
            .append("  <title>").append(esc(title)).append(" - WebStream Manager</title>\n")
            .append("  <link rel=\"stylesheet\" href=\"/static/styles.css\">\n")
            .append("</head>\n<body>\n");

        sb.append("  <header class=\"site-header\">\n    <div class=\"header-left\">\n")
            .append("      <button class=\"nav-toggle\" aria-label=\"Menu\" aria-controls=\"topbar-links\" aria-expanded=\"false\">\n")
            .append("        <span></span><span></span><span></span>\n      </button>\n");
        if (branding.hasBanner()) {
            sb.append("      <img src=\"/branding/banner\" alt=\"Bannière\" class=\"banner-img\" />\n");
        } else {
            sb.append("      <a href=\"/\" class=\"brand\"><img src=\"/static/images/icon.png\" alt=\"\" class=\"brand-icon\" /><span>WebStream Manager</span></a>\n");
        }
        sb.append("    </div>\n    <div class=\"header-right\">\n");
        sb.append("      <a class=\"chip\" href=\"/profiles\" title=\"Changer de profil\">🗂 ").append(esc(ws.activeProfileName())).append("</a>\n");
        String world = ws.currentWorld();
        if (world != null && !world.equalsIgnoreCase(ws.activeProfileName())) {
            sb.append("      <span class=\"chip chip-muted\" title=\"Monde ouvert\">🌍 ").append(esc(world)).append("</span>\n");
        }
        sb.append("      <div id=\"clock\" class=\"clock\" aria-label=\"Horloge\"></div>\n");
        if (branding.hasIcon()) sb.append("      <img src=\"/branding/icon\" alt=\"\" class=\"server-icon\" />\n");
        sb.append("    </div>\n  </header>\n\n");

        sb.append("  <div class=\"sidenav-backdrop\" data-action=\"close-sidenav\"></div>\n")
            .append("  <nav class=\"sidenav\" aria-label=\"Navigation latérale\">\n    <div class=\"sidenav-links\">\n");
        navLink(sb, "/", "Accueil", "home", page);
        navLink(sb, "/library", "Bibliothèque", "library", page);
        navLink(sb, "/screens", "Écrans", "screens", page);
        navLink(sb, "/families", "Familles", "families", page);
        navLink(sb, "/profiles", "Profils", "profiles", page);
        navLink(sb, "/data", "Données", "data", page);
        sb.append("    </div>\n  </nav>\n\n  <main>\n").append(body).append("\n  </main>\n\n");
        sb.append("  <footer>\n    <p>WebStream Manager ").append(esc(version)).append("</p>\n  </footer>\n\n")
            .append("  <script src=\"/static/nav.js\"></script>\n")
            .append("  <script src=\"/static/uploader.js\"></script>\n")
            .append("  <script src=\"/static/preview.js\"></script>\n");
        if (page.equals("library")) sb.append("  <script src=\"/static/library-search.js\"></script>\n");
        sb.append("</body>\n</html>\n");
        return sb.toString();
    }

    private static void navLink(StringBuilder sb, String href, String label, String key, String current) {
        sb.append("      <a href=\"").append(href).append("\" class=\"").append(key.equals(current) ? "active" : "")
            .append("\">").append(label).append("</a>\n");
    }

    static String flash(String kind, String html) {
        String style = switch (kind) {
            case "error" -> "background: #fee2e2; border-color: #f87171;";
            case "warn" -> "background: #fef3c7; border-color: #fbbf24;";
            default -> "background: #dcfce7; border-color: #4ade80;";
        };
        return "<div class=\"card_screens\" style=\"" + style + "\">" + html + "</div>\n";
    }

    String notFound(Ctx c) {
        return layout(c, "Page introuvable", "", "<section class=\"page\"><h1>404</h1><p>La page demandée est introuvable.</p>"
            + "<p><a href=\"/\">Retour à l'accueil</a></p></section>");
    }

    // ------------------------------------------------------------------ accueil

    String home(Ctx c) {
        int screens = ws.listScreens().size();
        String base = baseUrl(c);
        StringBuilder b = new StringBuilder();
        b.append("<section class=\"page\">\n  <h1>WebStream Manager</h1>\n")
            .append("  <p>Gérez les images affichées sur vos écrans WebStreamer via des adresses fixes : ")
            .append("changez l'image ici, l'écran se met à jour en jeu.</p>\n");
        b.append("  <div class=\"card_screens\">\n    <h2>État</h2>\n    <ul>\n");
        b.append("      <li>Profil actif : <a href=\"/profiles\"><strong>").append(esc(ws.activeProfileName())).append("</strong></a> — ")
            .append(screens).append(" écran(s)</li>\n");
        b.append("      <li>Monde ouvert : ").append(ws.currentWorld() == null ? "—" : esc(ws.currentWorld())).append("</li>\n");
        b.append("      <li>Adresse d'un écran dans WebStreamer : <code>").append(esc(base)).append("/&lt;nom-de-l-écran&gt;.png</code></li>\n");
        b.append("    </ul>\n  </div>\n");

        boolean localOnly = (settings.publicUrl == null || settings.publicUrl.isBlank()) && settings.publicPort == 0;
        if (localOnly) {
            b.append(flash("warn", "<p style=\"margin:0\"><strong>Jouer à plusieurs ?</strong> Cette adresse ne fonctionne que sur cette machine. "
                + "Activez <code>publicPort</code> et renseignez <code>publicUrl</code> dans <code>config/webstream.json</code> "
                + "pour que les autres joueurs voient vos écrans.</p>"));
        }

        b.append("  <div class=\"card_screens\">\n    <h2>Pour commencer</h2>\n    <ol>\n")
            .append("      <li><a href=\"/library\">Bibliothèque</a> : déposez vos images.</li>\n")
            .append("      <li><a href=\"/screens\">Écrans</a> : créez un écran, assignez-lui une image, copiez son adresse.</li>\n")
            .append("      <li>Dans le jeu, collez cette adresse dans le bloc d'affichage WebStreamer.</li>\n")
            .append("      <li><a href=\"/profiles\">Profils</a> : gardez une configuration différente par monde, ou changez de configuration à tout moment.</li>\n")
            .append("    </ol>\n  </div>\n</section>\n");
        return b.toString();
    }

    // ------------------------------------------------------------------ bibliothèque

    String library(Ctx c, List<String> files, String errorHtml) {
        StringBuilder b = new StringBuilder();
        b.append("<section class=\"page\">\n  <h1>Bibliothèque de contenu</h1>\n");
        if (errorHtml != null) b.append(flash("error", errorHtml));
        b.append("""
              <form id="uploadForm" action="/library/upload" method="post" enctype="multipart/form-data">
                <div class="dropzone" id="dropzone">Glissez-déposez vos fichiers ici ou cliquez</div>
                <input type="file" id="fileInput" name="files" accept="image/*" multiple hidden />
                <button type="submit">Uploader</button>
              </form>

              <div class="card_screens">
                <div class="library-header">
                  <h2>Fichiers</h2>
                  <div class="library-controls">
                    <div class="library-search">
                      <div class="search-results" id="searchResults"></div>
                      <input type="text" id="searchInput" placeholder="Rechercher dans la bibliothèque..." autocomplete="off" />
                      <div class="search-icons">
                        <button type="button" class="clear-search" id="clearSearch" title="Effacer la recherche">×</button>
                        <span class="search-icon">🔍</span>
                      </div>
                    </div>
                    <div class="size-controls">
                      <button type="button" class="size-btn active" data-size="small" title="Petites vignettes">
                        <svg width="16" height="16" viewBox="0 0 16 16">
                          <rect x="1" y="1" width="3" height="3" fill="currentColor"/><rect x="6" y="1" width="3" height="3" fill="currentColor"/><rect x="11" y="1" width="3" height="3" fill="currentColor"/>
                          <rect x="1" y="6" width="3" height="3" fill="currentColor"/><rect x="6" y="6" width="3" height="3" fill="currentColor"/><rect x="11" y="6" width="3" height="3" fill="currentColor"/>
                          <rect x="1" y="11" width="3" height="3" fill="currentColor"/><rect x="6" y="11" width="3" height="3" fill="currentColor"/><rect x="11" y="11" width="3" height="3" fill="currentColor"/>
                        </svg>
                      </button>
                      <button type="button" class="size-btn" data-size="medium" title="Vignettes moyennes">
                        <svg width="16" height="16" viewBox="0 0 16 16">
                          <rect x="1" y="1" width="6" height="6" fill="currentColor"/><rect x="9" y="1" width="6" height="6" fill="currentColor"/>
                          <rect x="1" y="9" width="6" height="6" fill="currentColor"/><rect x="9" y="9" width="6" height="6" fill="currentColor"/>
                        </svg>
                      </button>
                      <button type="button" class="size-btn" data-size="large" title="Grandes vignettes">
                        <svg width="16" height="16" viewBox="0 0 16 16">
                          <rect x="2" y="2" width="12" height="5" fill="currentColor"/><rect x="2" y="9" width="12" height="5" fill="currentColor"/>
                        </svg>
                      </button>
                    </div>
                  </div>
                </div>

                <div id="library-grid" class="grid size-small">
            """);
        if (files.isEmpty()) {
            b.append("      <p class=\"no-files\">Aucun fichier pour le moment.</p>\n");
        }
        for (String f : files) {
            b.append("      <div class=\"card library-item\" data-filename=\"").append(esc(f.toLowerCase())).append("\">\n")
                .append("        <img src=\"/content/").append(urlEnc(f)).append("\" data-filename=\"").append(esc(f))
                .append("\" alt=\"").append(esc(f)).append("\" class=\"previewable\" loading=\"lazy\" />\n")
                .append("        <div class=\"meta\">").append(esc(f)).append("</div>\n      </div>\n");
        }
        b.append("    </div>\n  </div>\n</section>\n");
        return b.toString();
    }

    // ------------------------------------------------------------------ écrans

    private static String tile(ScreenInfo s, boolean showFamily) {
        String w = s.width() == null ? "16" : num(s.width());
        String h = s.height() == null ? "9" : num(s.height());
        double wd = s.width() == null ? 16 : s.width();
        double hd = s.height() == null ? 9 : s.height();
        String src = s.content() != null
            ? "/content/" + urlEnc(s.content())
            : "/screens/placeholder/" + Math.round(wd * 100) + "/" + Math.round(hd * 100) + ".svg";
        StringBuilder b = new StringBuilder();
        b.append("            <div class=\"screen-card\">\n")
            .append("              <button type=\"button\" class=\"screen-tile\" data-ref=\"").append(esc(s.ref()))
            .append("\" data-content=\"").append(esc(s.content())).append("\" data-width=\"").append(num(s.width()))
            .append("\" data-height=\"").append(num(s.height())).append("\" data-family-id=\"")
            .append(s.familyId() == null ? "" : s.familyId()).append("\">\n")
            .append("                <div class=\"sc-frame\" style=\"--w: ").append(w).append("; --h: ").append(h).append(";\">\n")
            .append("                  <img src=\"").append(src).append("\" alt=\"").append(esc(s.ref())).append("\" loading=\"lazy\" />\n")
            .append("                </div>\n                <div class=\"sc-meta\">\n")
            .append("                  <div class=\"sc-title\"><code>").append(esc(s.ref())).append("</code></div>\n")
            .append("                  <div class=\"sc-sub\">").append(s.width() == null ? "—" : num(s.width())).append(" × ")
            .append(s.height() == null ? "—" : num(s.height())).append("</div>\n");
        if (showFamily) {
            b.append("                  <div class=\"sc-family\">").append(s.family() == null ? "Sans famille" : esc(s.family())).append("</div>\n");
        }
        b.append("                </div>\n              </button>\n            </div>\n");
        return b.toString();
    }

    String screens(Ctx c, String mode) {
        boolean all = mode.equals("all");
        StringBuilder b = new StringBuilder();
        b.append("""
            <section class="page">
              <div class="page-header">
                <h1>Écrans</h1>
                <button type="button" class="btn-add-screen" id="addScreenBtn">
                  <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor">
                    <path d="M8 2a.5.5 0 0 1 .5.5v5h5a.5.5 0 0 1 0 1h-5v5a.5.5 0 0 1-1 0v-5h-5a.5.5 0 0 1 0-1h5v-5A.5.5 0 0 1 8 2Z"/>
                  </svg>
                  Ajouter un écran
                </button>
              </div>

              <div class="card_screens">
                <div class="toolbar">
                  <div class="toolbar-left">
            """);
        b.append("        <a class=\"view-btn ").append(all ? "" : "active").append("\" href=\"/screens?mode=family\">Par famille</a>\n")
            .append("        <a class=\"view-btn ").append(all ? "active" : "").append("\" href=\"/screens?mode=all\">Tous</a>\n")
            .append("      </div>\n      <div class=\"toolbar-right\">\n        <a class=\"manage-btn\" href=\"/families\">⚙️ Gérer les familles</a>\n")
            .append("      </div>\n    </div>\n\n    <div class=\"content-area\">\n");

        if (all) {
            List<ScreenInfo> list = ws.listScreens();
            b.append("      <h2>Tous les écrans</h2>\n      <div class=\"screen-grid\">\n");
            if (list.isEmpty()) b.append("        <p class=\"muted\">Aucun écran</p>\n");
            for (ScreenInfo s : list) b.append(tile(s, true));
            b.append("      </div>\n");
        } else {
            List<ScreenGroup> groups = ws.listScreensByFamily();
            if (groups.isEmpty()) b.append("      <p class=\"muted\">Aucune famille</p>\n");
            for (ScreenGroup g : groups) {
                b.append("      <details class=\"family-group\" open>\n        <summary><strong>").append(esc(g.family()))
                    .append("</strong> — ").append(g.screens().size()).append(" écran(s)</summary>\n        <div class=\"screen-grid\">\n");
                for (ScreenInfo s : g.screens()) b.append(tile(s, false));
                b.append("        </div>\n      </details>\n");
            }
        }
        b.append("    </div>\n  </div>\n</section>\n\n");

        JsonArray fams = new JsonArray();
        for (FamilyInfo f : ws.listFamilies()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", f.id());
            o.addProperty("name", f.name());
            o.addProperty("screen_count", f.screenCount());
            fams.add(o);
        }
        JsonArray lib = new JsonArray();
        ws.listLibraryFiles().forEach(lib::add);
        b.append("<div id=\"screens-data\" data-base-url=\"").append(esc(baseUrl(c))).append("\" data-families=\"")
            .append(esc(fams.toString())).append("\" data-library=\"").append(esc(lib.toString())).append("\" hidden></div>\n");
        return b.toString();
    }

    // ------------------------------------------------------------------ familles

    String families(Ctx c, String errorHtml) {
        List<FamilyInfo> list = ws.listFamilies();
        StringBuilder b = new StringBuilder();
        b.append("<section class=\"page\">\n  <h1>Familles d'écrans</h1>\n")
            .append("  <p class=\"muted\">Profil : <strong>").append(esc(ws.activeProfileName())).append("</strong></p>\n")
            .append("  <div class=\"toolbar\"><div class=\"toolbar-left\"><a class=\"manage-btn\" href=\"/screens\">← Retour aux écrans</a></div></div>\n");
        if (errorHtml != null) b.append(flash("error", errorHtml));
        b.append("""
              <form class="card_screens" method="post" action="/families/create">
                <h2>Créer une famille</h2>
                <label>Nom de la famille
                  <input name="name" placeholder="MTR, Parc, Musée…" required maxlength="60" />
                </label>
                <button type="submit">Créer</button>
              </form>

              <div class="card_screens">
                <h2>Liste des familles</h2>
                <table class="table">
                  <thead><tr><th>Nom</th><th>Écrans</th><th>Actions</th></tr></thead>
                  <tbody>
            """);
        if (list.isEmpty()) b.append("        <tr><td colspan=\"3\" class=\"muted\">Aucune famille</td></tr>\n");
        for (FamilyInfo f : list) {
            b.append("        <tr><td>").append(esc(f.name())).append("</td><td>").append(f.screenCount()).append("</td><td>\n")
                .append("          <form method=\"post\" action=\"/families/").append(f.id())
                .append("/delete\" onsubmit=\"return confirm('Supprimer cette famille ?');\">\n")
                .append("            <button type=\"submit\" class=\"btn-danger\"").append(f.screenCount() > 0 ? " disabled" : "")
                .append(">Supprimer</button>\n          </form>\n        </td></tr>\n");
        }
        b.append("      </tbody>\n    </table>\n  </div>\n</section>\n");
        return b.toString();
    }

    // ------------------------------------------------------------------ profils

    String profiles(Ctx c, String flashHtml, String flashKind) {
        List<ProfileInfo> list = ws.listProfiles();
        String world = ws.currentWorld();
        StringBuilder b = new StringBuilder();
        b.append("<section class=\"page\">\n  <h1>Profils</h1>\n")
            .append("  <p>Un profil est une configuration complète : ses familles et ses écrans. ")
            .append("Les images de la bibliothèque sont communes à tous les profils. ")
            .append("Changer de profil change instantanément ce que montrent vos écrans en jeu.</p>\n");
        if (flashHtml != null) b.append(flash(flashKind, flashHtml));
        if (world != null) {
            b.append("  <p class=\"muted\">Monde ouvert : <strong>").append(esc(world))
                .append("</strong> — activer un profil ici le retient pour ce monde.</p>\n");
        }

        b.append("  <form class=\"card_screens\" method=\"post\" action=\"/profiles/create\">\n    <h2>Nouveau profil</h2>\n")
            .append("    <label>Nom <input name=\"name\" placeholder=\"Monde créatif, Événement…\" required maxlength=\"60\" /></label>\n")
            .append("    <label>Copier depuis <select name=\"cloneFrom\"><option value=\"\">— profil vide —</option>\n");
        for (ProfileInfo p : list) b.append("      <option value=\"").append(esc(p.id())).append("\">").append(esc(p.name())).append("</option>\n");
        b.append("    </select></label>\n    <button type=\"submit\">Créer</button>\n  </form>\n");

        b.append("  <div class=\"card_screens\">\n    <h2>Vos profils</h2>\n    <table class=\"table\">\n")
            .append("      <thead><tr><th>Nom</th><th>Écrans</th><th>Familles</th><th>Mondes liés</th><th>Actions</th></tr></thead>\n      <tbody>\n");
        for (ProfileInfo p : list) {
            String id = esc(p.id());
            b.append("        <tr").append(p.active() ? " class=\"profile-active\"" : "").append(">\n")
                .append("          <td><strong>").append(esc(p.name())).append("</strong>").append(p.active() ? " <span class=\"chip\">actif</span>" : "").append("</td>\n")
                .append("          <td>").append(p.screenCount()).append("</td><td>").append(p.familyCount()).append("</td>\n")
                .append("          <td>").append(p.worlds().isEmpty() ? "<span class=\"muted\">—</span>" : esc(String.join(", ", p.worlds()))).append("</td>\n")
                .append("          <td class=\"actions-cell\">\n");
            if (!p.active()) {
                b.append("            <form method=\"post\" action=\"/profiles/").append(id).append("/activate\" class=\"inline-form\">")
                    .append(world != null ? "<input type=\"hidden\" name=\"bind\" value=\"1\" />" : "")
                    .append("<button type=\"submit\">Activer</button></form>\n");
            }
            b.append("            <form method=\"post\" action=\"/profiles/").append(id).append("/duplicate\" class=\"inline-form\"><button type=\"submit\">Dupliquer</button></form>\n")
                .append("            <form method=\"post\" action=\"/profiles/").append(id).append("/rename\" class=\"inline-form\">")
                .append("<input name=\"name\" value=\"").append(esc(p.name())).append("\" maxlength=\"60\" aria-label=\"Nouveau nom\" /><button type=\"submit\">Renommer</button></form>\n")
                .append("            <form method=\"post\" action=\"/profiles/").append(id)
                .append("/delete\" class=\"inline-form\" onsubmit=\"return confirm('Supprimer ce profil et tous ses écrans ?');\">")
                .append("<button type=\"submit\" class=\"btn-danger\"").append(p.active() ? " disabled title=\"Le profil actif ne peut pas être supprimé\"" : "")
                .append(">Supprimer</button></form>\n          </td>\n        </tr>\n");
        }
        b.append("      </tbody>\n    </table>\n  </div>\n</section>\n");
        return b.toString();
    }

    // ------------------------------------------------------------------ données (export / import)

    String data(Ctx c, ImportResult result, String errorHtml) {
        List<ProfileInfo> profiles = ws.listProfiles();
        StringBuilder b = new StringBuilder();
        b.append("<section class=\"page\">\n  <h1>Données</h1>\n");
        if (errorHtml != null) b.append(flash("error", "<p style=\"margin:0\">❌ " + errorHtml + "</p>"));
        if (result != null) {
            StringBuilder r = new StringBuilder("<p style=\"margin-top:0\"><strong>✅ Import terminé</strong> dans le profil « ")
                .append(esc(result.profileName)).append(" »</p><ul style=\"margin:0\">")
                .append("<li>").append(result.screensAdded).append(" écran(s) ajouté(s)</li>")
                .append("<li>").append(result.screensUpdated).append(" écran(s) mis à jour</li>")
                .append("<li>").append(result.screensSkipped).append(" écran(s) ignoré(s) (déjà présents)</li>")
                .append("<li>").append(result.familiesAdded).append(" famille(s) ajoutée(s)</li>");
            if (result.screensInvalid > 0) r.append("<li>").append(result.screensInvalid).append(" entrée(s) invalide(s) ignorée(s)</li>");
            r.append("</ul>");
            if (!result.missingFiles.isEmpty()) {
                r.append("<p style=\"margin-bottom:0\">⚠️ ").append(result.missingFiles.size())
                    .append(" image(s) référencée(s) absente(s) de la bibliothèque — envoyez-les depuis la page Bibliothèque :</p><ul style=\"margin:0\">");
                for (String f : result.missingFiles) r.append("<li>").append(esc(f)).append("</li>");
                r.append("</ul>");
            }
            b.append(flash("ok", r.toString()));
        }

        b.append("  <div class=\"card_screens\">\n    <h2>Exporter</h2>\n")
            .append("    <p>Télécharge les familles et les écrans du profil « <strong>").append(esc(ws.activeProfileName()))
            .append("</strong> » dans un fichier JSON, importable dans un autre profil ou un autre monde. Les images ne sont pas incluses.</p>\n")
            .append("    <a class=\"manage-btn\" href=\"/data/export\">Télécharger l'export (.json)</a>\n  </div>\n");

        b.append("  <form class=\"card_screens\" method=\"post\" action=\"/data/import\" enctype=\"multipart/form-data\">\n    <h2>Importer</h2>\n")
            .append("    <p>Choisissez un export <code>.json</code> (y compris ceux des versions précédentes du mod).</p>\n")
            .append("    <label>Fichier <input type=\"file\" name=\"files\" accept=\".json\" required /></label>\n")
            .append("    <fieldset style=\"border:0;padding:0;margin:1rem 0\"><legend>Importer dans</legend>\n")
            .append("      <label><input type=\"radio\" name=\"target\" value=\"active\" checked /> Le profil actif (« ").append(esc(ws.activeProfileName())).append(" »)</label><br />\n")
            .append("      <label><input type=\"radio\" name=\"target\" value=\"new\" /> Un nouveau profil : <input name=\"newName\" placeholder=\"Nom du profil\" maxlength=\"60\" /></label>\n");
        if (profiles.size() > 1) {
            b.append("      <br /><label><input type=\"radio\" name=\"target\" value=\"other\" /> Un autre profil : <select name=\"otherId\">");
            for (ProfileInfo p : profiles) if (!p.active()) b.append("<option value=\"").append(esc(p.id())).append("\">").append(esc(p.name())).append("</option>");
            b.append("</select></label>\n");
        }
        b.append("    </fieldset>\n")
            .append("    <fieldset style=\"border:0;padding:0;margin:1rem 0\"><legend>Si un écran existe déjà</legend>\n")
            .append("      <label><input type=\"radio\" name=\"mode\" value=\"skip\" checked /> Le garder tel quel (ajouter seulement les nouveaux)</label><br />\n")
            .append("      <label><input type=\"radio\" name=\"mode\" value=\"overwrite\" /> Le mettre à jour avec le fichier importé</label><br />\n")
            .append("      <label><input type=\"radio\" name=\"mode\" value=\"replace\" /> Tout remplacer (supprime les écrans et familles du profil cible)</label>\n")
            .append("    </fieldset>\n")
            .append("    <button type=\"submit\" onclick=\"var m = this.form.mode.value; return m !== 'replace' || confirm('Supprimer tous les écrans et familles du profil cible avant l\\'import ?');\">Importer</button>\n")
            .append("  </form>\n</section>\n");
        return b.toString();
    }
}
