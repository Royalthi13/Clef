package com.example.clef.ui.dashboard;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestOptions;
import com.example.clef.R;
import com.example.clef.data.model.Credential;
import com.example.clef.utils.ClipboardHelper;

import java.util.ArrayList;
import java.util.List;

public class VaultAdapter extends RecyclerView.Adapter<VaultAdapter.ViewHolder> {

    public interface OnDeleteListener {
        void onDelete(int position, Credential credential);
    }

    private final List<Credential> allItems = new ArrayList<>();
    private final List<Credential> displayedItems = new ArrayList<>();
    private final Context context;
    private OnDeleteListener deleteListener;

    public VaultAdapter(Context context) {
        this.context = context;
    }

    public void setOnDeleteListener(OnDeleteListener listener) {
        this.deleteListener = listener;
    }

    public void setCredentials(List<Credential> credentials) {
        allItems.clear();
        allItems.addAll(credentials);
        displayedItems.clear();
        displayedItems.addAll(credentials);
        notifyDataSetChanged();
    }

    public void filterList(List<Credential> filteredList) {
        displayedItems.clear();
        displayedItems.addAll(filteredList);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_credential, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Credential credential = displayedItems.get(position);

        String rawTitle = credential.getTitle();
        final String title = (rawTitle == null || rawTitle.trim().isEmpty()) ? "Sin Título" : rawTitle;

        holder.tvTitle.setText(title);
        holder.tvUsername.setText(credential.getUsername());
        holder.ivSyncStatus.setVisibility(credential.isSynced() ? View.VISIBLE : View.GONE);

        // Intentar cargar favicon del servicio basándonos en el título o URL
        loadServiceIcon(holder, credential, title);

        holder.btnCopy.setOnClickListener(v ->
                ClipboardHelper.copySensitive(context, title, credential.getPassword())
        );

        holder.btnDelete.setOnClickListener(v -> {
            if (deleteListener != null) {
                deleteListener.onDelete(holder.getAdapterPosition(), credential);
            }
        });
    }

    /**
     * Intenta cargar el favicon del servicio.
     * Primero intenta con la URL guardada, si no, construye el dominio desde el título.
     * Si falla todo, muestra la inicial como fallback.
     */
    private void loadServiceIcon(ViewHolder holder, Credential credential, String title) {
        String faviconUrl = buildFaviconUrl(credential, title);

        if (faviconUrl != null) {
            // Ocultar texto inicial, mostrar imagen
            holder.tvInitial.setVisibility(View.GONE);
            holder.ivServiceLogo.setVisibility(View.VISIBLE);

            Glide.with(context)
                    .load(faviconUrl)
                    .apply(new RequestOptions()
                            .transform(new CircleCrop())
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .placeholder(R.drawable.ic_shield_lock)
                            .error(R.drawable.ic_shield_lock))
                    .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                        @Override
                        public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException e,
                                                    Object model,
                                                    com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                                    boolean isFirstResource) {
                            // Si falla el favicon, mostrar inicial
                            holder.ivServiceLogo.setVisibility(View.GONE);
                            holder.tvInitial.setVisibility(View.VISIBLE);
                            holder.tvInitial.setText(title.substring(0, 1).toUpperCase());
                            return true;
                        }

                        @Override
                        public boolean onResourceReady(android.graphics.drawable.Drawable resource,
                                                       Object model,
                                                       com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                                       com.bumptech.glide.load.DataSource dataSource,
                                                       boolean isFirstResource) {
                            return false;
                        }
                    })
                    .into(holder.ivServiceLogo);
        } else {
            // Sin favicon: mostrar inicial
            holder.ivServiceLogo.setVisibility(View.GONE);
            holder.tvInitial.setVisibility(View.VISIBLE);
            holder.tvInitial.setText(title.substring(0, 1).toUpperCase());
        }
    }

    /**
     * Construye la URL del favicon de Google para el servicio.
     * Prioridad: URL guardada → nombre del servicio conocido → título como dominio.
     */
    private String buildFaviconUrl(Credential credential, String title) {
        String domain = null;

        // 1. Intentar extraer dominio de la URL guardada
        if (credential.getUrl() != null && !credential.getUrl().trim().isEmpty()) {
            domain = extractDomain(credential.getUrl());
        }

        // 2. Si no hay URL, mapear nombre del servicio a dominio conocido
        if (domain == null) {
            domain = guessKnownDomain(title.toLowerCase().trim());
        }

        // 3. Como último recurso, usar el título como si fuera un dominio
        if (domain == null && title.length() > 2) {
            // Solo si parece un nombre de servicio razonable (sin espacios, etc.)
            String cleaned = title.toLowerCase().replaceAll("\\s+", "");
            domain = cleaned + ".com";
        }

        if (domain == null) return null;
        return "https://www.google.com/s2/favicons?sz=64&domain=" + domain;
    }

    /**
     * Extrae el dominio de una URL completa.
     * Ej: "https://www.instagram.com/login" → "instagram.com"
     */
    private String extractDomain(String url) {
        try {
            String clean = url.trim();
            if (!clean.startsWith("http")) clean = "https://" + clean;
            java.net.URL parsed = new java.net.URL(clean);
            String host = parsed.getHost();
            if (host == null || host.isEmpty()) return null;
            // Quitar "www."
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Mapa de nombres populares a dominios reales.
     * Así "instagram" → "instagram.com" funciona sin que el usuario escriba la URL.
     */
    private String guessKnownDomain(String name) {
        switch (name) {
            case "instagram": case "ig":        return "instagram.com";
            case "facebook": case "fb":         return "facebook.com";
            case "twitter": case "x":           return "x.com";
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

    @Override
    public int getItemCount() {
        return displayedItems.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivSyncStatus;
        final ImageView ivServiceLogo;
        final TextView  tvInitial;
        final TextView  tvTitle;
        final TextView  tvUsername;
        final ImageButton btnCopy;
        final ImageButton btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvInitial    = itemView.findViewById(R.id.tvInitial);
            ivServiceLogo= itemView.findViewById(R.id.ivServiceLogo);
            tvTitle      = itemView.findViewById(R.id.tvTitle);
            tvUsername   = itemView.findViewById(R.id.tvUsername);
            ivSyncStatus = itemView.findViewById(R.id.ivSyncStatus);
            btnCopy      = itemView.findViewById(R.id.btnCopyPassword);
            btnDelete    = itemView.findViewById(R.id.btnDelete);
        }
    }
}