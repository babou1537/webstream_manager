package com.babou.webstream;

import com.babou.webstream.core.ExportData;
import com.babou.webstream.core.ExportData.ImportResult;
import com.babou.webstream.core.WsException;
import com.babou.webstream.core.Workspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceTest {
    @TempDir
    Path root;

    Workspace ws;

    @BeforeEach
    void setUp() {
        ws = new Workspace(root, Workspace.MODE_PER_WORLD);
    }

    private static String code(Runnable r) {
        try {
            r.run();
        } catch (WsException e) {
            return e.code();
        }
        return "NO_ERROR";
    }

    private void png(String name) throws IOException {
        Path tmp = Files.createTempFile(root, "up", ".tmp");
        Files.write(tmp, new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        ws.storeLibraryFile(name, tmp);
    }

    @Test
    void createsDefaultProfileOnFirstRun() {
        assertEquals(1, ws.listProfiles().size());
        assertEquals("default", ws.activeProfileId());
    }

    @Test
    void familyRules() {
        ws.createFamily("MTR");
        assertEquals("FAMILY_EXISTS", code(() -> ws.createFamily("mtr")));
        assertEquals("FAMILY_NAME_REQUIRED", code(() -> ws.createFamily("  ")));
        int id = ws.listFamilies().get(0).id();
        ws.createScreen("a", id, null, null);
        assertEquals("FAMILY_IN_USE", code(() -> ws.deleteFamily(id)));
        ws.deleteScreen("a");
        ws.deleteFamily(id);
        assertTrue(ws.listFamilies().isEmpty());
        assertEquals("FAMILY_NOT_FOUND", code(() -> ws.deleteFamily(id)));
    }

    @Test
    void screenLifecycleAndRefNormalization() throws IOException {
        png("plan.png");
        String ref = ws.createScreen("Écran Métro 1", null, 2.8, 1.3);
        assertEquals("Ecran_Metro_1", ref);
        assertEquals("SCREEN_EXISTS", code(() -> ws.createScreen("Ecran_Metro_1", null, null, null)));
        assertEquals("REF_REQUIRED", code(() -> ws.createScreen("###", null, null, null)));

        assertEquals("CONTENT_NOT_FOUND", code(() -> ws.assignContent(ref, "absent.png")));
        assertEquals("CONTENT_NOT_FOUND", code(() -> ws.assignContent(ref, "../secret.png")));
        ws.assignContent(ref, "plan.png");
        assertEquals("plan.png", ws.getScreen(ref).content());

        assertEquals("FILE_IN_USE", code(() -> ws.deleteLibraryFile("plan.png")));
        ws.unassignContent(ref);
        ws.deleteLibraryFile("plan.png");
        assertEquals("FILE_NOT_FOUND", code(() -> ws.deleteLibraryFile("plan.png")));
        assertEquals("BAD_PATH", code(() -> ws.deleteLibraryFile("../x.png")));

        assertEquals("BAD_DIMENSIONS", code(() -> ws.setDimensions(ref, -1.0, 2.0)));
        ws.setDimensions(ref, 4.0, 2.0);
        assertEquals(4.0, ws.getScreen(ref).width());
        assertEquals("SCREEN_NOT_FOUND", code(() -> ws.setDimensions("nope", 1.0, 1.0)));
    }

    @Test
    void groupsByFamilyIncludingEmptyAndOrphans() {
        ws.createFamily("Zoo");
        ws.createFamily("Alpha");
        int alpha = ws.listFamilies().get(0).id();
        ws.createScreen("s1", alpha, null, null);
        ws.createScreen("s2", null, null, null);
        var groups = ws.listScreensByFamily();
        assertEquals(3, groups.size());
        assertEquals("Alpha", groups.get(0).family());
        assertEquals(1, groups.get(0).screens().size());
        assertEquals("Zoo", groups.get(1).family());
        assertTrue(groups.get(1).screens().isEmpty());
        assertNull(groups.get(2).familyId(), "les écrans sans famille viennent toujours en dernier, dans n'importe quelle langue");
        assertEquals(1, groups.get(2).screens().size());
    }

    @Test
    void libraryRejectsBadNamesAndTypes() throws IOException {
        Path tmp = Files.createTempFile(root, "up", ".tmp");
        assertEquals("BAD_FILE_TYPE", code(() -> ws.storeLibraryFile("virus.exe", tmp)));
        assertEquals("BAD_PATH", code(() -> ws.storeLibraryFile("", tmp)));
        String stored = ws.storeLibraryFile("..\\..\\dossier/Mon Image (1).png", tmp);
        assertEquals("Mon_Image__1_.png", stored);
        assertNull(ws.resolveLibraryFile("../workspace.json"));
        assertNull(ws.resolveLibraryFile(".hidden.png"));
        assertNotNull(ws.resolveLibraryFile(stored));
        assertEquals(1, ws.listLibraryFiles().size());
    }

    @Test
    void profilesAreIndependentAndSwitchable() {
        ws.createFamily("F1");
        ws.createScreen("commun", null, null, null);
        ws.createProfile("Monde Creatif", null);
        var profiles = ws.listProfiles();
        assertEquals(2, profiles.size());
        String other = profiles.stream().filter(p -> !p.active()).findFirst().orElseThrow().id();
        assertEquals("monde-creatif", other);

        ws.activateProfile(other, false);
        assertTrue(ws.listScreens().isEmpty());
        ws.createScreen("seulement-ici", null, null, null);

        ws.activateProfile("default", false);
        assertEquals(1, ws.listScreens().size());
        assertEquals("commun", ws.listScreens().get(0).ref());
    }

    @Test
    void cloneRenameDeleteRules() {
        ws.createScreen("a", null, null, null);
        var copy = ws.duplicateProfile("default");
        assertEquals("Default (copy)", copy.name);
        ws.activateProfile(copy.id, false);
        assertEquals(1, ws.listScreens().size());
        assertEquals("PROFILE_ACTIVE", code(() -> ws.deleteProfile(copy.id)));
        assertEquals("PROFILE_EXISTS", code(() -> ws.renameProfile(copy.id, "DEFAULT")));
        ws.renameProfile(copy.id, "Autre");
        ws.deleteProfile("default");
        assertEquals("PROFILE_ACTIVE", code(() -> ws.deleteProfile(copy.id)));
        assertEquals("PROFILE_NOT_FOUND", code(() -> ws.activateProfile("zzz", false)));
        assertEquals("PROFILE_NAME_REQUIRED", code(() -> ws.createProfile("  ", null)));
    }

    @Test
    void worldBindingPerWorldMode() {
        ws.attachWorld("Monde A");
        String a = ws.activeProfileId();
        assertEquals("monde-a", a);
        ws.createScreen("ecran-a", null, null, null);

        ws.attachWorld("Monde B");
        String b = ws.activeProfileId();
        assertNotEquals(a, b);
        assertTrue(ws.listScreens().isEmpty());

        ws.attachWorld("Monde A");
        assertEquals(a, ws.activeProfileId());
        assertEquals(1, ws.listScreens().size());

        ws.activateProfile(b, true);
        ws.attachWorld("Monde B");
        ws.attachWorld("Monde A");
        assertEquals(b, ws.activeProfileId());
    }

    @Test
    void worldBindingSharedMode() {
        Workspace shared = new Workspace(root.resolve("shared"), Workspace.MODE_SHARED);
        shared.attachWorld("X");
        shared.createScreen("ecran", null, null, null);
        shared.attachWorld("Y");
        assertEquals("default", shared.activeProfileId());
        assertEquals(1, shared.listScreens().size());
    }

    @Test
    void stateSurvivesRestart() {
        ws.attachWorld("Monde A");
        ws.createFamily("Fam");
        ws.createScreen("e1", null, 2.0, 1.0);

        Workspace again = new Workspace(root, Workspace.MODE_PER_WORLD);
        again.attachWorld("Monde A");
        assertEquals("monde-a", again.activeProfileId());
        assertEquals("e1", again.listScreens().get(0).ref());
        assertEquals(1, again.listFamilies().size());
    }

    @Test
    void corruptProfileFileIsIgnored() throws IOException {
        ws.createScreen("ok", null, null, null);
        Files.writeString(root.resolve("profiles").resolve("casse.json"), "{ pas du json");
        Workspace again = new Workspace(root, Workspace.MODE_PER_WORLD);
        assertEquals(1, again.listProfiles().size());
        assertEquals("ok", again.listScreens().get(0).ref());
    }

    @Test
    void importAcceptsLegacyNodeExportAndReportsMissingImages() {
        String json = """
            {"format":"webstream-export","version":1,"exportedAt":"2026-01-01T00:00:00Z",
             "families":["MTR","Parc"],
             "screens":[
              {"ref":"plan_mtr_t3_little","family":"MTR","content":"MetroMap.png","width":2.8,"height":1.3},
              {"ref":"screen_parc_1","family":"Parc","content":null,"width":null,"height":null},
              {"ref":"","family":null},
              {"ref":"mauvais","family":null,"content":"../evil.png","width":-4,"height":0}
             ]}""";
        ExportData data = Workspace.parseExport(json);
        ImportResult r = ws.importData(data, "skip", null, null);
        assertEquals(3, r.screensAdded);
        assertEquals(1, r.screensInvalid);
        assertEquals(2, r.familiesAdded);
        assertEquals(List.of("MetroMap.png"), r.missingFiles);
        assertNull(ws.getScreen("mauvais").content());
        assertNull(ws.getScreen("mauvais").width());
        assertEquals("MTR", ws.getScreen("plan_mtr_t3_little").family());

        ImportResult again = ws.importData(data, "skip", null, null);
        assertEquals(0, again.screensAdded);
        assertEquals(3, again.screensSkipped);
        assertEquals(3, ws.importData(data, "overwrite", null, null).screensUpdated);
    }

    @Test
    void importIntoNewProfileAndReplace() {
        ws.createScreen("ancien", null, null, null);
        ExportData data = Workspace.parseExport("{\"families\":[],\"screens\":[{\"ref\":\"nouveau\"}]}");
        ImportResult r = ws.importData(data, "skip", null, "Importé");
        assertEquals("importe", r.profileId);
        assertEquals("ancien", ws.listScreens().get(0).ref());

        ImportResult rep = ws.importData(data, "replace", null, null);
        assertEquals(1, rep.screensAdded);
        assertEquals(1, ws.listScreens().size());
        assertEquals("nouveau", ws.listScreens().get(0).ref());

        assertEquals("IMPORT_BAD_MODE", code(() -> ws.importData(data, "??", null, null)));
        assertEquals("IMPORT_INVALID", code(() -> Workspace.parseExport("{}")));
        assertEquals("IMPORT_INVALID", code(() -> Workspace.parseExport("[1,2]")));
        assertEquals("IMPORT_INVALID", code(() -> Workspace.parseExport("{\"screens\":\"pasunetableau\"}")));
        assertEquals("IMPORT_BAD_JSON", code(() -> Workspace.parseExport("{oops")));
    }

    @Test
    void exportRoundTrips() {
        ws.createFamily("F");
        ws.createScreen("e", ws.listFamilies().get(0).id(), 3.0, 2.0);
        String json = Workspace.toJson(ws.exportProfile(null));
        Workspace other = new Workspace(root.resolve("other"), Workspace.MODE_PER_WORLD);
        other.importData(Workspace.parseExport(json), "skip", null, null);
        assertEquals("F", other.getScreen("e").family());
        assertEquals(3.0, other.getScreen("e").width());
    }

    @Test
    void relativeRootPathStillResolvesLibraryFiles() throws IOException {
        // Fabric fournit un chemin relatif du type .\config\webstream : c'était le cas réel du serveur
        Path relative = Path.of(".", "build", "rel-ws-" + System.nanoTime());
        try {
            Workspace rel = new Workspace(relative, Workspace.MODE_PER_WORLD);
            Files.write(rel.libraryDir().resolve("a.png"), new byte[]{(byte) 0x89, 'P', 'N', 'G'});
            assertNotNull(rel.resolveLibraryFile("a.png"));
            rel.createScreen("s", null, null, null);
            rel.assignContent("s", "a.png");
            assertEquals("a.png", rel.getScreen("s").content());
            assertNull(rel.resolveLibraryFile("../workspace.json"));
        } finally {
            try (var walk = Files.walk(relative)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    @Test
    void legacyNodeLibraryIsMigratedOnFirstRun() throws IOException {
        Path legacyRoot = root.resolve("legacy");
        Path legacyLib = legacyRoot.resolve("storage").resolve("library");
        Files.createDirectories(legacyLib);
        Files.write(legacyLib.resolve("a.png"), new byte[]{1});
        Files.write(legacyLib.resolve("notes.txt"), new byte[]{1});
        Workspace migrated = new Workspace(legacyRoot, Workspace.MODE_PER_WORLD);
        assertEquals(List.of("a.png"), migrated.listLibraryFiles());
        assertTrue(Files.exists(legacyLib.resolve("a.png")));
    }
}
