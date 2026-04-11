package com.example.clef.utils;

import com.example.clef.data.model.Credential;

/**
 * B-5 FIX: lógica de favicon extraída de VaultAdapter y UploadSelectAdapter.
 * Antes duplicada en dos clases — ahora existe en un único lugar.
 */
public final class FaviconHelper {

    private FaviconHelper() {}

    public static String buildFaviconUrl(Credential credential, String title) {
        String domain = null;
        if (credential.getUrl() != null && !credential.getUrl().trim().isEmpty()) {
            domain = extractDomain(credential.getUrl());
        }
        if (domain == null) domain = guessKnownDomain(title.toLowerCase().trim());
        if (domain == null && title.length() > 2) {
            domain = title.toLowerCase().replaceAll("\\s+", "") + ".com";
        }
        if (domain == null) return null;
        return "https://www.google.com/s2/favicons?sz=64&domain=" + domain;
    }

    public static String extractDomain(String url) {
        try {
            String clean = url.trim();
            if (!clean.startsWith("http")) clean = "https://" + clean;
            java.net.URL parsed = new java.net.URL(clean);
            String host = parsed.getHost();
            if (host == null || host.isEmpty()) return null;
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return null;
        }
    }

    public static String guessKnownDomain(String name) {
        switch (name) {
            case "instagram": case "ig":        return "instagram.com";
            case "facebook":  case "fb":        return "facebook.com";
            case "twitter":   case "x":         return "x.com";
            case "google":                      return "google.com";
            case "gmail":                       return "gmail.com";
            case "youtube":                     return "youtube.com";
            case "netflix":                     return "netflix.com";
            case "spotify":                     return "spotify.com";
            case "amazon":                      return "amazon.com";
            case "apple":                       return "apple.com";
            case "microsoft":                   return "microsoft.com";
            case "github":                      return "github.com";
            case "linkedin":                    return "linkedin.com";
            case "whatsapp":                    return "whatsapp.com";
            case "telegram":                    return "telegram.org";
            case "discord":                     return "discord.com";
            case "twitch":                      return "twitch.tv";
            case "reddit":                      return "reddit.com";
            case "tiktok":                      return "tiktok.com";
            case "paypal":                      return "paypal.com";
            case "dropbox":                     return "dropbox.com";
            case "notion":                      return "notion.so";
            case "slack":                       return "slack.com";
            case "zoom":                        return "zoom.us";
            case "steam":                       return "steampowered.com";
            case "epic": case "epic games":     return "epicgames.com";
            case "playstation": case "psn":     return "playstation.com";
            case "xbox":                        return "xbox.com";
            case "nintendo":                    return "nintendo.com";
            case "ebay":                        return "ebay.com";
            case "aliexpress":                  return "aliexpress.com";
            case "mercadona":                   return "mercadona.es";
            case "correos":                     return "correos.es";
            case "santander":                   return "bancosantander.es";
            case "bbva":                        return "bbva.es";
            case "caixabank": case "caixa":     return "caixabank.es";
            default:                            return null;
        }
    }
}