package com.babou.webstream;

import com.babou.webstream.core.Workspace;
import com.babou.webstream.web.WebService;
import com.babou.webstream.web.WebSettings;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lance l'interface hors Minecraft pour la développer ou la tester à la main :
 *   gradlew runWeb --args="<dossier de travail> <port> [export.json] [dossier d'images] [langue] [nom du monde] [version affichée]"
 */
public final class DevServer {
    private DevServer() {}

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args.length > 0 ? args[0] : "build/dev-workspace");
        WebSettings settings = new WebSettings();
        settings.port = args.length > 1 ? Integer.parseInt(args[1]) : 8282;
        if (args.length > 4 && !args[4].isBlank()) settings.language = args[4];
        String world = args.length > 5 && !args[5].isBlank() ? args[5] : "Demo World";

        Workspace ws = new Workspace(dir, Workspace.MODE_PER_WORLD, settings.tr("profile.default"));
        if (args.length > 3) {
            try (var images = Files.list(Path.of(args[3]))) {
                for (Path img : (Iterable<Path>) images::iterator) {
                    if (Workspace.isAllowedImageName(img.getFileName().toString())) {
                        Files.copy(img, ws.libraryDir().resolve(img.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
        ws.attachWorld(world);
        if (args.length > 2 && !args[2].isBlank() && ws.listScreens().isEmpty()) {
            ws.importData(Workspace.parseExport(Files.readString(Path.of(args[2]))), "skip", null, null);
        }

        WebService web = new WebService(ws, settings, args.length > 6 ? args[6] : "dev");
        web.start();
        System.out.println("WebStream (dev) : http://localhost:" + web.adminPort() + "  — Ctrl+C pour arrêter");
        Thread.currentThread().join();
    }
}
