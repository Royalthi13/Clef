package com.example.clef.ui.dashboard;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
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
import com.example.clef.utils.ExpiryHelper;
import com.example.clef.utils.PasswordGenerator;

import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

public class VaultAdapter extends RecyclerView.Adapter<VaultAdapter.ViewHolder> {

    public interface OnCredentialActionListener {
        void onSave(Credential credential);
        void onDelete(Credential credential);
    }

    private final List<Credential> displayedItems = new ArrayList<>();
    private final Context context;
    private OnCredentialActionListener actionListener;
    private int expandedPosition = -1;

    public VaultAdapter(Context context) {
        this.context = context;
    }

    public void setOnCredentialActionListener(OnCredentialActionListener listener) {
        this.actionListener = listener;
    }

    public void setCredentials(List<Credential> credentials) {
        expandedPosition = -1;
        displayedItems.clear();
        displayedItems.addAll(credentials);
        notifyDataSetChanged();
    }

    public void filterList(List<Credential> filteredList) {
        expandedPosition = -1;
        displayedItems.clear();
        displayedItems.addAll(filteredList);
        notifyDataSetChanged();
    }

    /** Refresca los datos visibles sin colapsar el item expandido. */
    public void refreshWithoutCollapse() {
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

        // ── Borde de caducidad ────────────────────────────────────────────────
        android.content.SharedPreferences prefs =
                context.getSharedPreferences(ExpiryHelper.PREFS_NAME, android.content.Context.MODE_PRIVATE);
        int strokeColor;
        if (prefs.getBoolean(ExpiryHelper.PREF_NOTIFICATIONS, false)) {
            long periodMs = prefs.getLong(ExpiryHelper.PREF_PERIOD, ExpiryHelper.PERIOD_ONE_YEAR);
            strokeColor = ExpiryHelper.getStrokeColor(context, credential.getUpdatedAt(), periodMs);
        } else {
            strokeColor = ContextCompat.getColor(context, R.color.clef_border);
        }
        ((com.google.android.material.card.MaterialCardView) holder.itemView).setStrokeColor(strokeColor);

        loadServiceIcon(holder, credential, title);

        holder.btnCopy.setOnClickListener(v ->
                ClipboardHelper.copySensitive(context, title, credential.getPassword()));

        // ── Expandir / colapsar al pulsar la card ─────────────────────────────
        holder.itemView.setOnClickListener(v -> {
            int prev = expandedPosition;
            expandedPosition = (holder.getAdapterPosition() == expandedPosition)
                    ? -1
                    : holder.getAdapterPosition();
            if (prev != -1) notifyItemChanged(prev);
            notifyItemChanged(holder.getAdapterPosition());
        });

        // ── Sección expandida ─────────────────────────────────────────────────
        boolean expanded = (position == expandedPosition);
        holder.expandedSection.setVisibility(expanded ? View.VISIBLE : View.GONE);

        if (expanded) {
            bindExpandedSection(holder, credential);
        }
    }

    private void bindExpandedSection(ViewHolder holder, Credential credential) {
        // Eliminar watchers anteriores para evitar disparos al setear texto
        holder.removeWatchers();

        holder.etPassword.setText(credential.getPassword());
        holder.etUrl.setText(credential.getUrl() != null ? credential.getUrl() : "");
        holder.etNotes.setText(credential.getNotes() != null ? credential.getNotes() : "");
        holder.btnSave.setEnabled(false);

        // Watcher único que activa el botón Guardar al detectar cambios
        TextWatcher dirtyWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                holder.btnSave.setEnabled(true);
            }
        };
        holder.passwordWatcher = dirtyWatcher;
        holder.urlWatcher      = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { holder.btnSave.setEnabled(true); }
        };
        holder.notesWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { holder.btnSave.setEnabled(true); }
        };
        holder.etPassword.addTextChangedListener(holder.passwordWatcher);
        holder.etUrl     .addTextChangedListener(holder.urlWatcher);
        holder.etNotes   .addTextChangedListener(holder.notesWatcher);

        // Icono dado (start) → generar contraseña
        holder.tilPassword.setStartIconOnClickListener(v -> {
            String generated = PasswordGenerator.generateFromPrefs(context);
            holder.etPassword.setText(generated);
            holder.etPassword.setSelection(generated.length());
        });

        // Ojo → mantener pulsado para ver, soltar para ocultar
        holder.btnShowPassword.setOnTouchListener((v, event) -> {
            int len = holder.etPassword.getText() != null
                    ? holder.etPassword.getText().length() : 0;
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    holder.etPassword.setInputType(
                            android.text.InputType.TYPE_CLASS_TEXT |
                            android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    holder.etPassword.setSelection(len);
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    holder.etPassword.setInputType(
                            android.text.InputType.TYPE_CLASS_TEXT |
                            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    holder.etPassword.setSelection(len);
                    break;
            }
            return true;
        });

        // Mostrar contraseña anterior si existe (persiste en el modelo)
        String prevPwd = credential.getPreviousPassword();
        if (prevPwd != null && !prevPwd.isEmpty()) {
            holder.etPreviousPassword.setText(prevPwd);
            holder.layoutPreviousPassword.setVisibility(View.VISIBLE);
        } else {
            holder.layoutPreviousPassword.setVisibility(View.GONE);
        }

        // ℹ️ → aviso informativo
        holder.btnPasswordInfo.setOnClickListener(v ->
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                        .setTitle("Cambiar contraseña")
                        .setMessage("La contraseña anterior quedará visible abajo para que puedas usarla al cambiarla en el sitio web.")
                        .setPositiveButton("Entendido", null)
                        .show());

        // Cambiar contraseña → diálogo con generador
        holder.btnChangePassword.setOnClickListener(v -> {
            android.view.View dialogView = android.view.LayoutInflater.from(context)
                    .inflate(R.layout.dialog_change_password, null);
            com.google.android.material.textfield.TextInputLayout tilNew =
                    dialogView.findViewById(R.id.tilNewPassword);
            com.google.android.material.textfield.TextInputEditText etNew =
                    dialogView.findViewById(R.id.etNewPassword);
            android.widget.ImageButton btnShowNew =
                    dialogView.findViewById(R.id.btnShowNewPassword);

            // Dado → generar contraseña
            tilNew.setStartIconOnClickListener(gen -> {
                String generated = PasswordGenerator.generateFromPrefs(context);
                etNew.setText(generated);
                etNew.setSelection(generated.length());
            });

            // Ojo → mantener pulsado para ver
            btnShowNew.setOnTouchListener((v2, event) -> {
                int len = etNew.getText() != null ? etNew.getText().length() : 0;
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        etNew.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                                android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                        etNew.setSelection(len);
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        etNew.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                        etNew.setSelection(len);
                        break;
                }
                return true;
            });

            new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                    .setTitle("Cambiar contraseña")
                    .setView(dialogView)
                    .setPositiveButton("Aceptar", (d, w) -> {
                        String newPwd = etNew.getText() != null
                                ? etNew.getText().toString() : "";
                        if (newPwd.isEmpty()) return;

                        String oldPwd = holder.etPassword.getText() != null
                                ? holder.etPassword.getText().toString() : "";

                        // Guardar en el modelo para que persista al cifrar el vault
                        credential.setPreviousPassword(oldPwd);
                        holder.etPreviousPassword.setText(oldPwd);
                        holder.layoutPreviousPassword.setVisibility(View.VISIBLE);

                        holder.etPassword.setText(newPwd);
                        holder.etPassword.setSelection(newPwd.length());
                        holder.btnSave.setEnabled(true);
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        });

        // Ojo contraseña anterior → mantener pulsado para ver
        holder.btnShowPreviousPassword.setOnTouchListener((v, event) -> {
            int len = holder.etPreviousPassword.getText() != null
                    ? holder.etPreviousPassword.getText().length() : 0;
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    holder.etPreviousPassword.setInputType(
                            android.text.InputType.TYPE_CLASS_TEXT |
                            android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    holder.etPreviousPassword.setSelection(len);
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    holder.etPreviousPassword.setInputType(
                            android.text.InputType.TYPE_CLASS_TEXT |
                            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    holder.etPreviousPassword.setSelection(len);
                    break;
            }
            return true;
        });

        // Guardar cambios
        holder.btnSave.setOnClickListener(v -> {
            String newPassword = text(holder.etPassword);
            boolean passwordChanged = !newPassword.equals(
                    credential.getPassword() != null ? credential.getPassword() : "");

            credential.setPassword(newPassword);
            credential.setUrl(text(holder.etUrl));
            credential.setNotes(text(holder.etNotes));
            if (passwordChanged) {
                credential.setUpdatedAt(System.currentTimeMillis());
            }
            holder.btnSave.setEnabled(false);
            if (actionListener != null) actionListener.onSave(credential);
        });

        // Eliminar
        holder.btnDelete.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onDelete(credential);
        });
    }

    private String text(TextInputEditText et) {
        return (et.getText() != null) ? et.getText().toString().trim() : "";
    }

    // ── Favicon ────────────────────────────────────────────────────────────────

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

    @Override
    public int getItemCount() { return displayedItems.size(); }

    // ── ViewHolder ─────────────────────────────────────────────────────────────

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView        ivSyncStatus;
        final ImageView        ivServiceLogo;
        final TextView         tvInitial;
        final TextView         tvTitle;
        final TextView         tvUsername;
        final ImageButton      btnCopy;

        // Sección expandida
        final View              expandedSection;
        final TextInputLayout   tilPassword;
        final TextInputEditText etPassword;
        final ImageButton       btnShowPassword;
        final MaterialButton    btnChangePassword;
        final ImageButton       btnPasswordInfo;
        final View              layoutPreviousPassword;
        final TextInputEditText etPreviousPassword;
        final ImageButton       btnShowPreviousPassword;
        final TextInputEditText etUrl;
        final TextInputEditText etNotes;
        final MaterialButton    btnSave;
        final MaterialButton    btnDelete;

        TextWatcher passwordWatcher;
        TextWatcher urlWatcher;
        TextWatcher notesWatcher;
        String previousPassword = null;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvInitial      = itemView.findViewById(R.id.tvInitial);
            ivServiceLogo  = itemView.findViewById(R.id.ivServiceLogo);
            tvTitle        = itemView.findViewById(R.id.tvTitle);
            tvUsername     = itemView.findViewById(R.id.tvUsername);
            ivSyncStatus   = itemView.findViewById(R.id.ivSyncStatus);
            btnCopy        = itemView.findViewById(R.id.btnCopyPassword);

            expandedSection        = itemView.findViewById(R.id.expandedSection);
            tilPassword            = itemView.findViewById(R.id.tilPassword);
            etPassword             = itemView.findViewById(R.id.etPassword);
            btnShowPassword        = itemView.findViewById(R.id.btnShowPassword);
            btnChangePassword      = itemView.findViewById(R.id.btnChangePassword);
            btnPasswordInfo        = itemView.findViewById(R.id.btnPasswordInfo);
            layoutPreviousPassword = itemView.findViewById(R.id.layoutPreviousPassword);
            etPreviousPassword     = itemView.findViewById(R.id.etPreviousPassword);
            btnShowPreviousPassword= itemView.findViewById(R.id.btnShowPreviousPassword);
            etUrl                  = itemView.findViewById(R.id.etUrl);
            etNotes                = itemView.findViewById(R.id.etNotes);
            btnSave                = itemView.findViewById(R.id.btnSave);
            btnDelete              = itemView.findViewById(R.id.btnDelete);
        }

        void removeWatchers() {
            if (passwordWatcher != null) etPassword.removeTextChangedListener(passwordWatcher);
            if (urlWatcher      != null) etUrl     .removeTextChangedListener(urlWatcher);
            if (notesWatcher    != null) etNotes   .removeTextChangedListener(notesWatcher);
            passwordWatcher = urlWatcher = notesWatcher = null;
        }
    }
}
