package com.example.clef.data.model;

import com.example.clef.R;

/**
 * Esta clase representa UNA contraseña guardada por el usuario.
 *
 * Piénsala como una ficha con 5 campos:
 *   - title:    el nombre del sitio, por ejemplo "Gmail" o "Instagram"
 *   - username: el nombre de usuario o email con el que entras
 *   - password: la contraseña de ese sitio
 *   - url:      la dirección web (opcional)
 *   - notes:    cualquier nota extra que quieras guardar (opcional)
 *
 * IMPORTANTE: Esta clase no hace nada con los datos, solo los guarda.
 * GSON la convierte a texto JSON para que CryptoUtils pueda cifrarla.
 *
 * Ejemplo de cómo se ve por dentro antes de cifrarse:
 *   { "title": "Gmail", "username": "pepe@gmail.com", "password": "abc123" }
 */
public class Credential {
    public enum Category {
        BANK,
        SOCIAL,
        WORK,
        GAMES,
        SHOPPING,
        TRANSPORT,
        LEISURE,
        SPORTS,
        OTHER;
        public int getLabelRes() {
            switch (this) {
                case BANK:      return R.string.category_bank;
                case SOCIAL:    return R.string.category_social;
                case WORK:      return R.string.category_work;
                case GAMES:     return R.string.category_games;
                case SHOPPING:  return R.string.category_shopping;
                case TRANSPORT: return R.string.category_transport;
                case LEISURE:   return R.string.category_leisure;
                case SPORTS:    return R.string.category_sports;
                default:        return R.string.category_other;
            }
        }
        public int getIconRes() {
            switch (this) {
                case BANK:      return R.drawable.ic_category_bank;
                case SOCIAL:    return R.drawable.ic_category_social;
                case WORK:      return R.drawable.ic_category_work;
                case GAMES:     return R.drawable.ic_category_games;
                case SHOPPING:  return R.drawable.ic_category_shopping;
                case TRANSPORT: return R.drawable.ic_category_transport;
                case LEISURE:   return R.drawable.ic_category_leisure;
                case SPORTS:    return R.drawable.ic_category_sports;
                default:        return R.drawable.ic_category_other;
            }
        }
    }
    private String title;
    private String username;
    private String password;
    private java.util.List<String> passwordHistory;
    private String url;
    private String notes;
    private Category category;
    private boolean synced;
    private long updatedAt;
    private long lastUsedAt;
    private java.util.List<String> packageHints;
    private boolean favorite;
    /**
     * Constructor vacío necesario para que GSON pueda reconstruir
     * el objeto cuando descifra el JSON.
     */
    public Credential() {}

    /**
     * Crea una credencial con todos sus campos de golpe.
     *
     * @param title    Nombre del sitio, por ejemplo "Gmail".
     * @param username Usuario o email con el que entras al sitio.
     * @param password Contraseña del sitio.
     * @param url      Dirección web del sitio. Puede ser cadena vacía.
     * @param notes    Notas adicionales. Puede ser cadena vacía.
     */
    public Credential(String title, String username, String password, String url, String notes, Category category) {
        this.title    = title;
        this.username = username;
        this.password = password;
        this.url      = url;
        this.notes    = notes;
        this.category = category;
    }

    /** Devuelve el nombre del sitio, por ejemplo "Gmail". */
    public String getTitle()    { return title; }

    /** Devuelve el nombre de usuario o email. */
    public String getUsername() { return username; }

    /** Devuelve la contraseña del sitio. */
    public String getPassword() { return password; }

    /** Devuelve la URL del sitio web. Puede ser null o vacío. */
    public String getUrl()      { return url; }

    /** Devuelve las notas adicionales. Puede ser null o vacío. */
    public String getNotes()    { return notes; }

    /** Cambia el nombre del sitio. */
    public void setTitle(String title)       { this.title = title; }

    /** Cambia el nombre de usuario. */
    public void setUsername(String username) { this.username = username; }

    /** Cambia la contraseña. */
    public void setPassword(String password) { this.password = password; }

    /** Devuelve el historial de contraseñas (la más reciente en el índice 0). */
    public java.util.List<String> getPasswordHistory() {
        return passwordHistory != null ? passwordHistory : new java.util.ArrayList<>();
    }

    /** Devuelve la contraseña inmediatamente anterior a la actual, o null si no hay historial. */
    public String getPreviousPassword() {
        java.util.List<String> h = getPasswordHistory();
        return h.isEmpty() ? null : h.get(0);
    }

    /**
     * Añade una contraseña al historial. La más reciente queda en el índice 0.
     * Mantiene un máximo de 5 entradas.
     */
    public void addToHistory(String pwd) {
        if (pwd == null || pwd.isEmpty()) return;
        if (passwordHistory == null) passwordHistory = new java.util.ArrayList<>();
        if (!passwordHistory.isEmpty() && passwordHistory.get(0).equals(pwd)) return;
        passwordHistory.add(0, pwd);
        if (passwordHistory.size() > 5) passwordHistory.remove(passwordHistory.size() - 1);
    }

    /** Cambia la URL. */
    public void setUrl(String url)           { this.url = url; }

    /** Cambia las notas. */
    public void setNotes(String notes)       { this.notes = notes; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public boolean isSynced() { return synced; }
    public void setSynced(boolean synced) { this.synced = synced; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public long getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(long lastUsedAt) { this.lastUsedAt = lastUsedAt; }

    /** PackageNames de apps a las que esta credencial aplica (para autofill). */
    public java.util.List<String> getPackageHints() {
        return packageHints != null ? packageHints : new java.util.ArrayList<>();
    }

    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }

    public void addPackageHint(String pkg) {
        if (pkg == null || pkg.isEmpty()) return;
        if (packageHints == null) packageHints = new java.util.ArrayList<>();
        if (!packageHints.contains(pkg)) packageHints.add(pkg);
    }
}
