package com.example.clef.ui.settings;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UploadSelectAdapter extends RecyclerView.Adapter<UploadSelectAdapter.ViewHolder> {

    private final List<Credential> items;
    private final Set<Integer> selected = new HashSet<>();
    private final Context context;

    public UploadSelectAdapter(Context context, List<Credential> items) {
        this.context = context;
        this.items = items;
    }

    public List<Credential> getSelected() {
        List<Credential> result = new ArrayList<>();
        for (int idx : selected) result.add(items.get(idx));
        return result;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_credential_selectable, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Credential credential = items.get(position);
        String rawTitle = credential.getTitle();
        String title = (rawTitle == null || rawTitle.trim().isEmpty()) ? "Sin Título" : rawTitle;

        holder.tvTitle.setText(title);
        holder.tvUsername.setText(credential.getUsername());

        boolean isSelected = selected.contains(position);
        holder.ivCheck.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        holder.card.setStrokeColor(isSelected
                ? context.getColor(com.google.android.material.R.color.m3_ref_palette_primary100)
                : context.getColor(R.color.clef_border));
        holder.card.setStrokeWidth(isSelected ? 2 : 1);

        loadServiceIcon(holder, credential, title);

        holder.card.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (selected.contains(pos)) selected.remove(pos);
            else selected.add(pos);
            notifyItemChanged(pos);
        });
    }

    private void loadServiceIcon(ViewHolder holder, Credential credential, String title) {
        String faviconUrl = buildFaviconUrl(credential, title);
        if (faviconUrl != null) {
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
            holder.ivServiceLogo.setVisibility(View.GONE);
            holder.tvInitial.setVisibility(View.VISIBLE);
            holder.tvInitial.setText(title.substring(0, 1).toUpperCase());
        }
    }

    private String buildFaviconUrl(Credential credential, String title) {
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

    private String extractDomain(String url) {
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
            case "steam":                       return "steampowered.com";
            default:                            return null;
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final ImageView ivServiceLogo;
        final TextView tvInitial;
        final TextView tvTitle;
        final TextView tvUsername;
        final ImageView ivCheck;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card         = itemView.findViewById(R.id.cardSelectable);
            tvInitial    = itemView.findViewById(R.id.tvInitial);
            ivServiceLogo= itemView.findViewById(R.id.ivServiceLogo);
            tvTitle      = itemView.findViewById(R.id.tvTitle);
            tvUsername   = itemView.findViewById(R.id.tvUsername);
            ivCheck      = itemView.findViewById(R.id.ivCheck);
        }
    }
}
