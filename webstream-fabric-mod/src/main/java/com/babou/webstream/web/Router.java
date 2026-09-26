package com.babou.webstream.web;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Routeur minimal : "/screens/{ref}/family" -> handler. Les paramètres sont décodés (%xx). */
final class Router {
    @FunctionalInterface
    interface Handler {
        void handle(Ctx c) throws Exception;
    }

    static final class Route {
        final String method;
        final Pattern pattern;
        final List<String> names;
        final Handler handler;
        /** Servie aussi sur le port public (images uniquement, sans authentification). */
        final boolean publicAsset;

        Route(String method, Pattern pattern, List<String> names, Handler handler, boolean publicAsset) {
            this.method = method;
            this.pattern = pattern;
            this.names = names;
            this.handler = handler;
            this.publicAsset = publicAsset;
        }
    }

    record Match(Route route, Map<String, String> params) {}

    private static final Pattern PARAM = Pattern.compile("\\{(\\w+)}");
    private final List<Route> routes = new ArrayList<>();

    void add(String method, String pattern, Handler handler) {
        routes.add(compile(method, pattern, handler, false));
    }

    void addPublic(String method, String pattern, Handler handler) {
        routes.add(compile(method, pattern, handler, true));
    }

    private static Route compile(String method, String pattern, Handler handler, boolean publicAsset) {
        StringBuilder regex = new StringBuilder("^");
        List<String> names = new ArrayList<>();
        Matcher m = PARAM.matcher(pattern);
        int last = 0;
        while (m.find()) {
            regex.append(Pattern.quote(pattern.substring(last, m.start())));
            regex.append("([^/]+)");
            names.add(m.group(1));
            last = m.end();
        }
        regex.append(Pattern.quote(pattern.substring(last))).append("$");
        return new Route(method, Pattern.compile(regex.toString()), names, handler, publicAsset);
    }

    Match find(String method, String rawPath) {
        for (Route r : routes) {
            if (!r.method.equals(method)) continue;
            Matcher m = r.pattern.matcher(rawPath);
            if (m.matches()) {
                Map<String, String> params = new HashMap<>();
                for (int i = 0; i < r.names.size(); i++) params.put(r.names.get(i), decode(m.group(i + 1)));
                return new Match(r, params);
            }
        }
        return null;
    }

    static String decode(String segment) {
        try {
            return URLDecoder.decode(segment.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return segment;
        }
    }
}
