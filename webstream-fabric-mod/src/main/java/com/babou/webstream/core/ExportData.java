package com.babou.webstream.core;

import java.util.ArrayList;
import java.util.List;

/** Format d'échange JSON (compatible avec les exports de la version Node.js v1.0.x). */
public class ExportData {
    public String format = "webstream-export";
    public int version = 1;
    public String exportedAt;
    public String profile;
    public List<String> families = new ArrayList<>();
    public List<ExportScreen> screens = new ArrayList<>();

    public static class ExportScreen {
        public String ref;
        public String family;
        public String content;
        public Double width;
        public Double height;
    }

    public static class ImportResult {
        public String profileId;
        public String profileName;
        public int familiesAdded;
        public int screensAdded;
        public int screensUpdated;
        public int screensSkipped;
        public int screensInvalid;
        public List<String> missingFiles = new ArrayList<>();
    }
}
