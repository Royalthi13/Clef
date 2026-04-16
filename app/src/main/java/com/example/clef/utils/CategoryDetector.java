package com.example.clef.utils;

import com.example.clef.data.model.Credential.Category;

import java.util.HashMap;
import java.util.Map;

/**
 * Detecta automáticamente la categoría más probable para una credencial
 * a partir del título y la URL, usando un diccionario de keywords.
 *
 * No es infalible — es una heurística. El usuario siempre puede corregir
 * manualmente en el selector del diálogo.
 */
public final class CategoryDetector {

    private static final Map<String, Category> DICT = new HashMap<>();

    static {
        // Bancos
        for (String k : new String[]{
                "bank", "banco", "santander", "bbva", "caixa", "caixabank",
                "sabadell", "ing", "openbank", "bankinter", "unicaja", "kutxa",
                "paypal", "revolut", "wise", "n26", "bizum", "visa", "mastercard"
        }) DICT.put(k, Category.BANK);

        // Redes sociales
        for (String k : new String[]{
                "instagram", "facebook", "twitter", "tiktok", "snapchat", "linkedin",
                "reddit", "pinterest", "tumblr", "threads", "mastodon", "bluesky",
                "whatsapp", "telegram", "discord", "messenger", "signal", "x.com", "ig", "fb"
        }) DICT.put(k, Category.SOCIAL);

        for (String k : new String[]{
                "gmail", "google", "googlemail", "hotmail", "outlook", "yahoo",
                "icloud", "protonmail", "proton", "tuta", "tutanota", "zoho",
                "office", "office365", "microsoft", "teams", "onedrive", "sharepoint",
                "slack", "zoom", "meet", "webex",
                "notion", "trello", "asana", "jira", "confluence", "atlassian",
                "github", "gitlab", "bitbucket", "sourceforge",
                "dropbox", "drive", "googledrive", "workspace", "box",
                "monday", "clickup", "linear", "figma", "miro", "canva",
                "stackoverflow", "npm", "docker", "aws", "azure", "gcp",
                "digitalocean", "heroku", "vercel", "netlify", "cloudflare"
        }) DICT.put(k, Category.WORK);

        // Juegos
        for (String k : new String[]{
                "steam", "epic", "epicgames", "playstation", "psn", "xbox",
                "nintendo", "battle.net", "blizzard", "riot", "ea", "origin",
                "ubisoft", "gog", "twitch", "minecraft", "roblox"
        }) DICT.put(k, Category.GAMES);

        // Compras
        for (String k : new String[]{
                "amazon", "ebay", "aliexpress", "shein", "zalando", "temu",
                "wallapop", "vinted", "carrefour", "mercadona", "corteingles",
                "elcorteingles", "ikea", "pccomponentes", "mediamarkt"
        }) DICT.put(k, Category.SHOPPING);

        // Transporte
        for (String k : new String[]{
                "uber", "cabify", "bolt", "renfe", "iberia", "vueling", "ryanair",
                "airbnb", "booking", "blablacar", "metro", "alsa", "dgt"
        }) DICT.put(k, Category.TRANSPORT);

        // Ocio / streaming / entretenimiento
        for (String k : new String[]{
                "netflix", "spotify", "youtube", "disney", "disneyplus", "hbo",
                "max", "primevideo", "amazonprime", "appletv", "movistar",
                "crunchyroll", "filmin", "rakuten", "plex", "atresplayer", "rtve", "mitele",
                "audible", "deezer", "tidal", "soundcloud", "applemusic",
                "pandora", "iheartradio", "podcast", "ivoox",
                "kindle", "goodreads", "letterboxd", "imdb"
        }) DICT.put(k, Category.LEISURE);

        // Deportes
        for (String k : new String[]{
                "strava", "fitbit", "garmin", "nike", "adidas", "decathlon",
                "myfitnesspal", "basketball", "laliga", "fifa", "dazn", "uefa"
        }) DICT.put(k, Category.SPORTS);
    }

    private CategoryDetector() {}

    /**
     * Devuelve la categoría detectada, o null si no hay coincidencia clara.
     * El llamador decide el default (normalmente OTHER).
     */
    public static Category detect(String title, String url) {
        String combined = (safe(title) + " " + safe(url))
                .toLowerCase()
                .replaceAll("[^a-z0-9. ]", " ");

        if (combined.trim().isEmpty()) return null;

        for (Map.Entry<String, Category> e : DICT.entrySet()) {
            String kw = e.getKey();
            // Match por palabra completa O por substring si la keyword tiene ≥5 chars
            if (combined.contains(" " + kw + " ")
                    || combined.startsWith(kw + " ")
                    || combined.endsWith(" " + kw)
                    || combined.equals(kw)
                    || (kw.length() >= 5 && combined.contains(kw))) {
                return e.getValue();
            }
        }
        return null;
    }

    private static String safe(String s) { return s == null ? "" : s; }
}