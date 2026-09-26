package com.babou.webstream.core;

import java.util.ArrayList;
import java.util.List;

/** Une configuration complète : familles et écrans. Sérialisée telle quelle en JSON (Gson). */
public class Profile {
    public String id;
    public String name;
    public int nextFamilyId = 1;
    public List<Family> families = new ArrayList<>();
    public List<Screen> screens = new ArrayList<>();

    public static class Family {
        public int id;
        public String name;

        public Family() {}

        public Family(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static class Screen {
        public String ref;
        public Integer familyId;
        public String content;
        public Double width;
        public Double height;
        public String createdAt;
        public String updatedAt;
    }

    public Family family(int id) {
        for (Family f : families) if (f.id == id) return f;
        return null;
    }

    public Family familyByName(String name) {
        for (Family f : families) if (f.name.equalsIgnoreCase(name)) return f;
        return null;
    }

    public Screen screen(String ref) {
        for (Screen s : screens) if (s.ref.equals(ref)) return s;
        return null;
    }

    public int screenCount(int familyId) {
        int n = 0;
        for (Screen s : screens) if (s.familyId != null && s.familyId == familyId) n++;
        return n;
    }
}
