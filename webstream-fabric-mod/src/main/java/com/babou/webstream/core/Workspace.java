package com.babou.webstream.core;

import com.babou.webstream.core.ExportData.ExportScreen;
import com.babou.webstream.core.ExportData.ImportResult;
import com.babou.webstream.core.Profile.Family;
import com.babou.webstream.core.Profile.Screen;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.Collator;
import java.text.Normalizer;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Dossier de travail du mod : une bibliothèque d'images commune, plusieurs profils (familles + écrans),
 * et le lien monde -> profil. Un seul profil est actif à la fois ; changer de profil change instantanément
 * le contenu servi par les URL /ecran.png.
 *
 * Disposition sur disque : library/, profiles/&lt;id&gt;.json, workspace.json.
 */
public final class Workspace {
    public static final String MODE_PER_WORLD = "perWorld";
    public static final String MODE_SHARED = "shared";
    public static final String DEFAULT_PROFILE_ID = "default";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Pattern IMAGE_FILE = Pattern.compile("(?i).+\\.(png|jpe?g|gif|webp|bmp|tiff?|svg)$");
    private static final int MAX_IMPORT_SCREENS = 10_000;
    private static final int MAX_NAME_LENGTH = 60;

    public record FamilyInfo(int id, String name, int screenCount) {}

    public record ScreenInfo(String ref, String content, Double width, Double height, Integer familyId, String family) {}

    public record ScreenGroup(Integer familyId, String family, List<ScreenInfo> screens) {}

    public record ProfileInfo(String id, String name, int screenCount, int familyCount, boolean active, List<String> worlds, List<String> previews) {}

    public record LibraryFile(String name, long size) {}

    private static class State {
        int version = 1;
        String active;
        Map<String, String> worlds = new LinkedHashMap<>();
    }

    private final Path root;
    private final Path libraryDir;
    private final Path profilesDir;
    private final Path stateFile;
    private volatile String newWorldMode;
    private final String defaultProfileName;

    private final Map<String, Profile> profiles = new LinkedHashMap<>();
    private final Map<String, String> worlds = new LinkedHashMap<>();
    private String activeId;
    private String currentWorld;

    public Workspace(Path root, String newWorldMode) {
        this(root, newWorldMode, "Default");
    }

    /** defaultProfileName : nom (dans la langue de l'utilisateur) du profil créé au premier lancement. */
    public Workspace(Path root, String newWorldMode, String defaultProfileName) {
        this.defaultProfileName = defaultProfileName == null || defaultProfileName.isBlank() ? "Default" : defaultProfileName.trim();
        // Chemin absolu et normalisé : Fabric fournit un chemin relatif (.\config\...), et les contrôles
        // anti-« .. » comparent des chemins normalisés avec startsWith
        this.root = root.toAbsolutePath().normalize();
        this.libraryDir = this.root.resolve("library");
        this.profilesDir = this.root.resolve("profiles");
        this.stateFile = this.root.resolve("workspace.json");
        this.newWorldMode = MODE_SHARED.equals(newWorldMode) ? MODE_SHARED : MODE_PER_WORLD;
        try {
            Files.createDirectories(profilesDir);
            boolean freshLibrary = !Files.exists(libraryDir);
            Files.createDirectories(libraryDir);
            if (freshLibrary) migrateLegacyLibrary();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        reload();
    }

    // Reprend la bibliothèque de l'ancienne version Node.js (storage/library) sans la déplacer
    private void migrateLegacyLibrary() throws IOException {
        Path legacy = root.resolve("storage").resolve("library");
        if (!Files.isDirectory(legacy)) return;
        try (Stream<Path> files = Files.list(legacy)) {
            for (Path f : (Iterable<Path>) files::iterator) {
                if (Files.isRegularFile(f) && IMAGE_FILE.matcher(f.getFileName().toString()).matches()) {
                    Files.copy(f, libraryDir.resolve(f.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    // ------------------------------------------------------------------ chargement / sauvegarde

    public synchronized void reload() {
        profiles.clear();
        worlds.clear();
        try (Stream<Path> files = Files.list(profilesDir)) {
            List<Path> sorted = files.filter(f -> f.getFileName().toString().endsWith(".json")).sorted().toList();
            for (Path f : sorted) {
                try {
                    Profile p = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Profile.class);
                    if (p != null && p.id != null && p.name != null) {
                        if (p.families == null) p.families = new ArrayList<>();
                        if (p.screens == null) p.screens = new ArrayList<>();
                        profiles.put(p.id, p);
                    }
                } catch (JsonParseException | IOException e) {
                    // profil illisible : ignoré, le fichier reste sur disque pour être réparé à la main
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        if (profiles.isEmpty()) {
            Profile p = new Profile();
            p.id = DEFAULT_PROFILE_ID;
            p.name = defaultProfileName;
            profiles.put(p.id, p);
            saveProfile(p);
        }

        State state = new State();
        if (Files.exists(stateFile)) {
            try {
                State read = GSON.fromJson(Files.readString(stateFile, StandardCharsets.UTF_8), State.class);
                if (read != null) state = read;
            } catch (JsonParseException | IOException e) {
                // état illisible : on repart d'un état vide
            }
        }
        if (state.worlds != null) {
            state.worlds.forEach((w, id) -> {
                if (profiles.containsKey(id)) worlds.put(w, id);
            });
        }
        activeId = profiles.containsKey(state.active) ? state.active : profiles.keySet().iterator().next();
        saveState();
    }

    private void saveProfile(Profile p) {
        writeAtomically(profilesDir.resolve(p.id + ".json"), GSON.toJson(p));
    }

    private void saveState() {
        State s = new State();
        s.active = activeId;
        s.worlds = new LinkedHashMap<>(worlds);
        writeAtomically(stateFile, GSON.toJson(s));
    }

    private static void writeAtomically(Path target, String content) {
        try {
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Profile active() {
        return profiles.get(activeId);
    }

    private static String now() {
        return Instant.now().toString();
    }

    // ------------------------------------------------------------------ mondes et profils

    /** perWorld ou shared : appliqué à la prochaine ouverture d'un monde encore inconnu. */
    public void setNewWorldMode(String mode) {
        this.newWorldMode = MODE_SHARED.equals(mode) ? MODE_SHARED : MODE_PER_WORLD;
    }

    public synchronized String currentWorld() {
        return currentWorld;
    }

    public synchronized String activeProfileId() {
        return activeId;
    }

    public synchronized String activeProfileName() {
        return active().name;
    }

    /** Appelé à l'ouverture d'un monde : retrouve son profil, ou en attribue un selon newWorldProfile. */
    public synchronized void attachWorld(String world) {
        currentWorld = world;
        String bound = worlds.get(world);
        if (bound != null && profiles.containsKey(bound)) {
            activeId = bound;
        } else if (MODE_SHARED.equals(newWorldMode)) {
            activeId = profiles.containsKey(DEFAULT_PROFILE_ID) ? DEFAULT_PROFILE_ID : profiles.keySet().iterator().next();
            worlds.put(world, activeId);
        } else {
            Profile p = newProfile(world);
            activeId = p.id;
            worlds.put(world, p.id);
        }
        saveState();
    }

    public synchronized void detachWorld() {
        currentWorld = null;
    }

    public synchronized List<ProfileInfo> listProfiles() {
        List<ProfileInfo> out = new ArrayList<>();
        for (Profile p : profiles.values()) {
            List<String> bound = new ArrayList<>();
            worlds.forEach((w, id) -> {
                if (id.equals(p.id)) bound.add(w);
            });
            List<String> previews = p.screens.stream().map(sc -> sc.content).filter(java.util.Objects::nonNull).distinct().limit(4).toList();
            out.add(new ProfileInfo(p.id, p.name, p.screens.size(), p.families.size(), p.id.equals(activeId), bound, previews));
        }
        return out;
    }

    public synchronized ProfileInfo profileInfo(String id) {
        return listProfiles().stream().filter(p -> p.id().equals(id)).findFirst().orElse(null);
    }

    public synchronized Profile createProfile(String name, String cloneFromId) {
        Profile created = newProfile(name);
        if (cloneFromId != null && !cloneFromId.isBlank()) {
            Profile source = profiles.get(cloneFromId);
            if (source == null) {
                deleteProfileFile(created);
                profiles.remove(created.id);
                throw new WsException("PROFILE_NOT_FOUND");
            }
            copyContent(source, created);
            saveProfile(created);
        }
        return created;
    }

    public synchronized Profile duplicateProfile(String id) {
        return duplicateProfile(id, "(copy)");
    }

    /** suffix : « (copie) », « (copy) »… ajouté au nom du profil dupliqué. */
    public synchronized Profile duplicateProfile(String id, String suffix) {
        Profile source = profiles.get(id);
        if (source == null) throw new WsException("PROFILE_NOT_FOUND");
        String base = source.name + " " + suffix;
        String name = base;
        for (int i = 2; nameTaken(name); i++) name = base + " " + i;
        return createProfile(name, id);
    }

    public synchronized void renameProfile(String id, String name) {
        Profile p = profiles.get(id);
        if (p == null) throw new WsException("PROFILE_NOT_FOUND");
        String clean = cleanProfileName(name);
        for (Profile other : profiles.values()) {
            if (!other.id.equals(id) && other.name.equalsIgnoreCase(clean)) throw new WsException("PROFILE_EXISTS");
        }
        p.name = clean;
        saveProfile(p);
    }

    public synchronized void deleteProfile(String id) {
        Profile p = profiles.get(id);
        if (p == null) throw new WsException("PROFILE_NOT_FOUND");
        if (id.equals(activeId)) throw new WsException("PROFILE_ACTIVE");
        profiles.remove(id);
        worlds.values().removeIf(id::equals);
        deleteProfileFile(p);
        saveState();
    }

    /** Active un profil sans redémarrage ; si bindCurrentWorld, le mémorise pour le monde ouvert. */
    public synchronized void activateProfile(String id, boolean bindCurrentWorld) {
        if (!profiles.containsKey(id)) throw new WsException("PROFILE_NOT_FOUND");
        activeId = id;
        if (bindCurrentWorld && currentWorld != null) worlds.put(currentWorld, id);
        saveState();
    }

    private Profile newProfile(String name) {
        String clean = cleanProfileName(name);
        if (nameTaken(clean)) {
            String base = clean;
            for (int i = 2; nameTaken(clean); i++) clean = base + " " + i;
        }
        Profile p = new Profile();
        p.name = clean;
        p.id = uniqueProfileId(clean);
        profiles.put(p.id, p);
        saveProfile(p);
        return p;
    }

    private boolean nameTaken(String name) {
        for (Profile p : profiles.values()) if (p.name.equalsIgnoreCase(name)) return true;
        return false;
    }

    private String uniqueProfileId(String name) {
        String base = slug(name);
        String id = base;
        for (int i = 2; profiles.containsKey(id); i++) id = base + "-" + i;
        return id;
    }

    private void deleteProfileFile(Profile p) {
        try {
            Files.deleteIfExists(profilesDir.resolve(p.id + ".json"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void copyContent(Profile from, Profile to) {
        Profile copy = GSON.fromJson(GSON.toJson(from), Profile.class);
        to.nextFamilyId = copy.nextFamilyId;
        to.families = copy.families;
        to.screens = copy.screens;
    }

    private static String cleanProfileName(String name) {
        String clean = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (clean.isEmpty()) throw new WsException("PROFILE_NAME_REQUIRED");
        return clean.length() > MAX_NAME_LENGTH ? clean.substring(0, MAX_NAME_LENGTH).trim() : clean;
    }

    static String slug(String name) {
        String s = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        s = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (s.length() > 40) s = s.substring(0, 40).replaceAll("-+$", "");
        return s.isEmpty() ? "profil" : s;
    }

    // ------------------------------------------------------------------ familles

    public synchronized List<FamilyInfo> listFamilies() {
        Profile p = active();
        List<FamilyInfo> out = new ArrayList<>();
        for (Family f : p.families) out.add(new FamilyInfo(f.id, f.name, p.screenCount(f.id)));
        out.sort(Comparator.comparing(FamilyInfo::name, Collator.getInstance(Locale.FRENCH)));
        return out;
    }

    public synchronized void createFamily(String name) {
        Profile p = active();
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) throw new WsException("FAMILY_NAME_REQUIRED");
        if (n.length() > MAX_NAME_LENGTH) n = n.substring(0, MAX_NAME_LENGTH).trim();
        if (p.familyByName(n) != null) throw new WsException("FAMILY_EXISTS");
        p.families.add(new Family(p.nextFamilyId++, n));
        saveProfile(p);
    }

    public synchronized void deleteFamily(int id) {
        Profile p = active();
        Family f = p.family(id);
        if (f == null) throw new WsException("FAMILY_NOT_FOUND");
        if (p.screenCount(id) > 0) throw new WsException("FAMILY_IN_USE");
        p.families.remove(f);
        saveProfile(p);
    }

    // ------------------------------------------------------------------ écrans

    private ScreenInfo info(Profile p, Screen s) {
        Family f = s.familyId == null ? null : p.family(s.familyId);
        return new ScreenInfo(s.ref, s.content, s.width, s.height, s.familyId, f == null ? null : f.name);
    }

    public synchronized List<ScreenInfo> listScreens() {
        Profile p = active();
        List<ScreenInfo> out = new ArrayList<>();
        for (Screen s : p.screens) out.add(info(p, s));
        out.sort(Comparator.comparing(ScreenInfo::ref, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /** Une entrée par famille (même vide), plus « Sans famille » s'il reste des écrans orphelins. */
    public synchronized List<ScreenGroup> listScreensByFamily() {
        Profile p = active();
        List<ScreenGroup> groups = new ArrayList<>();
        for (Family f : p.families) groups.add(new ScreenGroup(f.id, f.name, new ArrayList<>()));
        List<ScreenInfo> orphans = new ArrayList<>();
        for (ScreenInfo s : listScreens()) {
            if (s.familyId() == null || p.family(s.familyId()) == null) {
                orphans.add(s);
            } else {
                for (ScreenGroup g : groups) if (g.familyId().equals(s.familyId())) g.screens().add(s);
            }
        }
        groups.sort(Comparator.comparing(ScreenGroup::family, Collator.getInstance(Locale.FRENCH)));
        // Les écrans sans famille viennent toujours en dernier ; l'interface affiche le libellé dans sa langue
        if (!orphans.isEmpty()) groups.add(new ScreenGroup(null, "", orphans));
        return groups;
    }

    public synchronized ScreenInfo getScreen(String ref) {
        Profile p = active();
        Screen s = ref == null ? null : p.screen(ref);
        return s == null ? null : info(p, s);
    }

    public synchronized String createScreen(String ref, Integer familyId, Double width, Double height) {
        Profile p = active();
        String r = normalizeRef(ref);
        if (r.isEmpty()) throw new WsException("REF_REQUIRED");
        if (p.screen(r) != null) throw new WsException("SCREEN_EXISTS");
        if (familyId != null && p.family(familyId) == null) throw new WsException("FAMILY_NOT_FOUND");
        Screen s = new Screen();
        s.ref = r;
        s.familyId = familyId;
        s.width = checkDimension(width);
        s.height = checkDimension(height);
        s.createdAt = now();
        s.updatedAt = s.createdAt;
        p.screens.add(s);
        saveProfile(p);
        return r;
    }

    public synchronized void setScreenFamily(String ref, Integer familyId) {
        Profile p = active();
        Screen s = requireScreen(p, ref);
        if (familyId != null && p.family(familyId) == null) throw new WsException("FAMILY_NOT_FOUND");
        s.familyId = familyId;
        touch(p, s);
    }

    public synchronized void setDimensions(String ref, Double width, Double height) {
        Profile p = active();
        Screen s = requireScreen(p, ref);
        s.width = checkDimension(width);
        s.height = checkDimension(height);
        touch(p, s);
    }

    public synchronized void assignContent(String ref, String fileName) {
        Profile p = active();
        Screen s = requireScreen(p, ref);
        if (resolveLibraryFile(fileName) == null) throw new WsException("CONTENT_NOT_FOUND");
        s.content = fileName;
        touch(p, s);
    }

    public synchronized void unassignContent(String ref) {
        Profile p = active();
        Screen s = requireScreen(p, ref);
        s.content = null;
        touch(p, s);
    }

    public synchronized void deleteScreen(String ref) {
        Profile p = active();
        Screen s = requireScreen(p, ref);
        p.screens.remove(s);
        saveProfile(p);
    }

    private static Screen requireScreen(Profile p, String ref) {
        Screen s = ref == null ? null : p.screen(ref);
        if (s == null) throw new WsException("SCREEN_NOT_FOUND");
        return s;
    }

    private void touch(Profile p, Screen s) {
        s.updatedAt = now();
        saveProfile(p);
    }

    private static Double checkDimension(Double v) {
        if (v == null) return null;
        if (v.isNaN() || v.isInfinite() || v <= 0 || v > 1000) throw new WsException("BAD_DIMENSIONS");
        return v;
    }

    /** Rend une référence utilisable dans une URL : sans accents ni espaces, 64 caractères max. */
    public static String normalizeRef(String raw) {
        if (raw == null) return "";
        String s = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        s = s.replaceAll("\\s+", "_").replaceAll("[^A-Za-z0-9._-]", "").replaceAll("^\\.+", "");
        return s.length() > 64 ? s.substring(0, 64) : s;
    }

    // ------------------------------------------------------------------ bibliothèque

    public Path libraryDir() {
        return libraryDir;
    }

    public Path rootDir() {
        return root;
    }

    public List<String> listLibraryFiles() {
        Collator collator = Collator.getInstance(Locale.FRENCH);
        collator.setStrength(Collator.PRIMARY);
        try (Stream<Path> files = Files.list(libraryDir)) {
            return files.filter(Files::isRegularFile)
                .map(f -> f.getFileName().toString())
                .filter(n -> IMAGE_FILE.matcher(n).matches())
                .sorted(collator::compare)
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Fichiers de la bibliothèque avec leur taille en octets. */
    public List<LibraryFile> listLibraryFilesInfo() {
        List<LibraryFile> out = new ArrayList<>();
        for (String name : listLibraryFiles()) {
            try {
                out.add(new LibraryFile(name, Files.size(libraryDir.resolve(name))));
            } catch (IOException e) {
                // fichier supprimé entre-temps : ignoré
            }
        }
        return out;
    }

    /** Pour chaque image, les écrans du profil actif qui l'utilisent. */
    public synchronized Map<String, List<String>> usageByFile() {
        Map<String, List<String>> usage = new HashMap<>();
        for (Screen s : active().screens) {
            if (s.content != null) usage.computeIfAbsent(s.content, k -> new ArrayList<>()).add(s.ref);
        }
        usage.values().forEach(l -> l.sort(String.CASE_INSENSITIVE_ORDER));
        return usage;
    }

    public static boolean isAllowedImageName(String name) {
        return name != null && IMAGE_FILE.matcher(name).matches();
    }

    /** Chemin d'un fichier de la bibliothèque, ou null si le nom est invalide ou le fichier absent. */
    public Path resolveLibraryFile(String name) {
        if (name == null || name.isEmpty() || name.startsWith(".") || name.contains("/") || name.contains("\\") || name.contains("\0")) {
            return null;
        }
        Path p = libraryDir.resolve(name).normalize();
        if (!p.startsWith(libraryDir) || !Files.isRegularFile(p)) return null;
        return p;
    }

    /** Range un fichier reçu (déjà écrit dans tmp) dans la bibliothèque et retourne son nom final. */
    public synchronized String storeLibraryFile(String originalName, Path tmp) {
        String base = originalName == null ? "" : originalName.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        String name = base.replaceAll("[^a-zA-Z0-9._-]", "_").replaceAll("^\\.+", "");
        if (name.isEmpty()) throw new WsException("BAD_PATH");
        if (!isAllowedImageName(name)) throw new WsException("BAD_FILE_TYPE");
        try {
            Path target = libraryDir.resolve(name);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return name;
    }

    public synchronized void deleteLibraryFile(String name) {
        if (name == null || name.isEmpty() || name.contains("/") || name.contains("\\") || name.startsWith(".")) {
            throw new WsException("BAD_PATH");
        }
        for (Profile p : profiles.values()) {
            for (Screen s : p.screens) {
                if (name.equals(s.content)) throw new WsException("FILE_IN_USE");
            }
        }
        Path file = resolveLibraryFile(name);
        if (file == null) throw new WsException("FILE_NOT_FOUND");
        try {
            Files.delete(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ------------------------------------------------------------------ export / import

    public synchronized ExportData exportProfile(String profileId) {
        Profile p = profileId == null ? active() : profiles.get(profileId);
        if (p == null) throw new WsException("PROFILE_NOT_FOUND");
        ExportData out = new ExportData();
        out.exportedAt = now();
        out.profile = p.name;
        p.families.stream().map(f -> f.name).sorted(String.CASE_INSENSITIVE_ORDER).forEach(out.families::add);
        List<Screen> sorted = new ArrayList<>(p.screens);
        sorted.sort(Comparator.comparing(s -> s.ref, String.CASE_INSENSITIVE_ORDER));
        for (Screen s : sorted) {
            ExportScreen e = new ExportScreen();
            e.ref = s.ref;
            Family f = s.familyId == null ? null : p.family(s.familyId);
            e.family = f == null ? null : f.name;
            e.content = s.content;
            e.width = s.width;
            e.height = s.height;
            out.screens.add(e);
        }
        return out;
    }

    public static ExportData parseExport(String json) {
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) throw new WsException("IMPORT_INVALID");
            root = parsed.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new WsException("IMPORT_BAD_JSON");
        }
        if (!root.has("screens") || !root.get("screens").isJsonArray()) throw new WsException("IMPORT_INVALID");
        try {
            ExportData data = GSON.fromJson(root, ExportData.class);
            if (data.families == null) data.families = new ArrayList<>();
            return data;
        } catch (JsonParseException e) {
            throw new WsException("IMPORT_INVALID");
        }
    }

    public static String toJson(ExportData data) {
        return GSON.toJson(data);
    }

    /**
     * Importe dans un profil existant (targetProfileId, ou le profil actif si null) ou, si newProfileName
     * est renseigné, dans un nouveau profil.
     * mode : skip (ajouter seulement les nouveaux), overwrite (mettre à jour), replace (tout remplacer).
     */
    public synchronized ImportResult importData(ExportData data, String mode, String targetProfileId, String newProfileName) {
        if (!List.of("skip", "overwrite", "replace").contains(mode)) throw new WsException("IMPORT_BAD_MODE");
        if (data == null || data.screens == null) throw new WsException("IMPORT_INVALID");
        if (data.screens.size() > MAX_IMPORT_SCREENS) throw new WsException("IMPORT_TOO_LARGE");

        Profile target;
        if (newProfileName != null && !newProfileName.isBlank()) {
            target = newProfile(newProfileName);
            mode = "replace";
        } else {
            target = targetProfileId == null || targetProfileId.isBlank() ? active() : profiles.get(targetProfileId);
            if (target == null) throw new WsException("PROFILE_NOT_FOUND");
        }

        ImportResult result = new ImportResult();
        result.profileId = target.id;
        result.profileName = target.name;

        if (mode.equals("replace")) {
            target.families = new ArrayList<>();
            target.screens = new ArrayList<>();
            target.nextFamilyId = 1;
        }

        Set<String> familyNames = new LinkedHashSet<>();
        for (String f : data.families) {
            String n = f == null ? "" : f.trim();
            if (!n.isEmpty()) familyNames.add(n.length() > MAX_NAME_LENGTH ? n.substring(0, MAX_NAME_LENGTH) : n);
        }
        Map<String, ExportScreen> byRef = new LinkedHashMap<>();
        for (ExportScreen s : data.screens) {
            String ref = s == null ? "" : normalizeRef(s.ref);
            if (ref.isEmpty()) {
                result.screensInvalid++;
                continue;
            }
            if (s.family != null && !s.family.isBlank()) familyNames.add(s.family.trim());
            byRef.put(ref, s);
        }

        for (String name : familyNames) {
            if (target.familyByName(name) == null) {
                target.families.add(new Family(target.nextFamilyId++, name));
                result.familiesAdded++;
            }
        }

        for (Map.Entry<String, ExportScreen> entry : byRef.entrySet()) {
            String ref = entry.getKey();
            ExportScreen in = entry.getValue();
            Family fam = in.family == null || in.family.isBlank() ? null : target.familyByName(in.family.trim());
            String content = in.content == null ? "" : in.content.trim();
            if (content.isEmpty() || content.contains("/") || content.contains("\\")) content = null;
            Double w = positiveOrNull(in.width);
            Double h = positiveOrNull(in.height);

            Screen existing = target.screen(ref);
            if (existing == null) {
                Screen s = new Screen();
                s.ref = ref;
                s.familyId = fam == null ? null : fam.id;
                s.content = content;
                s.width = w;
                s.height = h;
                s.createdAt = now();
                s.updatedAt = s.createdAt;
                target.screens.add(s);
                result.screensAdded++;
            } else if (mode.equals("overwrite")) {
                existing.familyId = fam == null ? null : fam.id;
                existing.content = content;
                existing.width = w;
                existing.height = h;
                existing.updatedAt = now();
                result.screensUpdated++;
            } else {
                result.screensSkipped++;
            }
            if (content != null && resolveLibraryFile(content) == null && !result.missingFiles.contains(content)) {
                result.missingFiles.add(content);
            }
        }

        saveProfile(target);
        return result;
    }

    private static Double positiveOrNull(Double v) {
        return v != null && !v.isNaN() && !v.isInfinite() && v > 0 && v <= 1000 ? v : null;
    }
}
