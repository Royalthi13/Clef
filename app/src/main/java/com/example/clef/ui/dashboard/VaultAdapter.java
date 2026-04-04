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
        if (prefs.getBoolean(ExpiryHelper.PREF_COLORS, false)) {
            long periodMs = prefs.getLong(ExpiryHelper.PREF_PERIOD, ExpiryHelper.PERIOD_ONE_YEAR);
            strokeColor = ExpiryHelper.getStrokeColor(context, credential.getUpdatedAt(), periodMs);
        } else {
            strokeColor = ContextCompat.getColor(context, R.color.clef_border);
        }
        ((com.google.android.material.card.MaterialCardView) holder.itemView).setStrokeColor(strokeColor);

        loadServiceIcon(holder, credential, title);

        holder.btnCopy.setOnClickListener(v -> {
            ClipboardHelper.copySensitive(context, title, credential.getPassword());
            credential.setLastUsedAt(System.currentTimeMillis());
            if (actionListener != null) actionListener.onSave(credential);
        });

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
        boolean expanded    = (position == expandedPosition);
        boolean wasVisible  = holder.expandedSection.getVisibility() == View.VISIBLE;

        if (expanded && !wasVisible) {
            bindExpandedSection(holder, credential);
            animateExpand(holder.expandedSection);
        } else if (!expanded && wasVisible) {
            animateCollapse(holder.expandedSection);
        } else if (expanded) {
            // Ya visible (p.ej. refresh tras guardar): rebind sin animar
            bindExpandedSection(holder, credential);
        }
    }

    private void bindExpandedSection(ViewHolder holder, Credential credential) {
        // Eliminar watchers anteriores para evitar disparos al setear texto
        holder.removeWatchers();

        holder.etExpandedTitle   .setText(credential.getTitle()    != null ? credential.getTitle()    : "");
        holder.etExpandedUsername.setText(credential.getUsername() != null ? credential.getUsername() : "");
        holder.etPassword.setText(credential.getPassword());
        holder.etUrl.setText(credential.getUrl()    != null ? credential.getUrl()    : "");
        holder.etNotes.setText(credential.getNotes() != null ? credential.getNotes() : "");
        holder.btnSave.setEnabled(false);

        // Contraseña solo lectura: solo se cambia desde el diálogo "Cambiar contraseña"
        holder.etPassword.setFocusable(false);
        holder.etPassword.setFocusableInTouchMode(false);
        holder.tilPassword.setStartIconOnClickListener(null);
        holder.tilPassword.setStartIconDrawable(null);

        // Título, usuario, URL y notas activan el botón Guardar
        holder.titleWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { holder.btnSave.setEnabled(true); }
        };
        holder.usernameWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { holder.btnSave.setEnabled(true); }
        };
        holder.urlWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { holder.btnSave.setEnabled(true); }
        };
        holder.notesWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { holder.btnSave.setEnabled(true); }
        };
        holder.etExpandedTitle   .addTextChangedListener(holder.titleWatcher);
        holder.etExpandedUsername.addTextChangedListener(holder.usernameWatcher);
        holder.etUrl             .addTextChangedListener(holder.urlWatcher);
        holder.etNotes           .addTextChangedListener(holder.notesWatcher);

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

        // Contraseña anterior (history[0]) y sección de historial
        String prevPwd = credential.getPreviousPassword();
        if (prevPwd != null && !prevPwd.isEmpty()) {
            holder.etPreviousPassword.setText(prevPwd);
            holder.layoutPreviousPassword.setVisibility(View.VISIBLE);
        } else {
            holder.layoutPreviousPassword.setVisibility(View.GONE);
        }
        refreshHistorySection(holder, credential);

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
            android.widget.LinearLayout sBar1 =
                    dialogView.findViewById(R.id.strengthBar1);
            android.widget.LinearLayout sBar2 =
                    dialogView.findViewById(R.id.strengthBar2);
            android.widget.LinearLayout sBar3 =
                    dialogView.findViewById(R.id.strengthBar3);
            android.widget.TextView sLabel =
                    dialogView.findViewById(R.id.tvStrengthLabel);

            etNew.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int i, int b, int c) {}
                @Override public void afterTextChanged(android.text.Editable s) {
                    com.example.clef.utils.PasswordStrengthHelper.update(
                            context, s.toString(), sBar1, sBar2, sBar3, sLabel);
                }
            });

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

                        // Añadir al historial del modelo para que persista al cifrar el vault
                        credential.addToHistory(oldPwd);
                        holder.etPreviousPassword.setText(oldPwd);
                        holder.layoutPreviousPassword.setVisibility(View.VISIBLE);
                        refreshHistorySection(holder, credential);

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
            String newTitle    = text(holder.etExpandedTitle);
            String newUsername = text(holder.etExpandedUsername);
            String newPassword = text(holder.etPassword);
            boolean passwordChanged = !newPassword.equals(
                    credential.getPassword() != null ? credential.getPassword() : "");

            if (!newTitle.isEmpty()) credential.setTitle(newTitle);
            credential.setUsername(newUsername);
            credential.setPassword(newPassword);
            credential.setUrl(text(holder.etUrl));
            credential.setNotes(text(holder.etNotes));
            if (passwordChanged) {
                credential.setUpdatedAt(System.currentTimeMillis());
            }

            // Actualizar header sin colapsar
            holder.tvTitle   .setText(newTitle.isEmpty() ? "Sin Título" : newTitle);
            holder.tvUsername.setText(newUsername);

            holder.btnSave.setEnabled(false);
            if (actionListener != null) actionListener.onSave(credential);
        });

        // Eliminar
        holder.btnDelete.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onDelete(credential);
        });
    }

    /** Rellena el contenedor de historial con las entradas 1-4 (la 0 ya se muestra arriba). */
    private void refreshHistorySection(ViewHolder holder, com.example.clef.data.model.Credential credential) {
        java.util.List<String> history = credential.getPasswordHistory();
        java.util.List<String> older = history.size() > 1
                ? history.subList(1, history.size()) : new java.util.ArrayList<>();

        if (older.isEmpty()) {
            holder.btnToggleHistory.setVisibility(View.GONE);
            holder.layoutPasswordHistory.setVisibility(View.GONE);
            return;
        }

        holder.btnToggleHistory.setVisibility(View.VISIBLE);
        holder.btnToggleHistory.setText("Ver historial (" + older.size() + ")");

        // Reconstruir filas
        holder.layoutPasswordHistory.removeAllViews();
        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(context);
        for (String pwd : older) {
            android.view.View row = inflater.inflate(R.layout.item_history_row, holder.layoutPasswordHistory, false);
            com.google.android.material.textfield.TextInputEditText etHist =
                    row.findViewById(R.id.etHistoryPassword);
            android.widget.ImageButton btnEye = row.findViewById(R.id.btnShowHistoryPassword);

            etHist.setText(pwd);

            btnEye.setOnTouchListener((v, event) -> {
                int len = etHist.getText() != null ? etHist.getText().length() : 0;
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        etHist.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                                android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                        etHist.setSelection(len);
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        etHist.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                        etHist.setSelection(len);
                        break;
                }
                return true;
            });

            holder.layoutPasswordHistory.addView(row);
        }

        holder.btnToggleHistory.setOnClickListener(v -> {
            boolean visible = holder.layoutPasswordHistory.getVisibility() == View.VISIBLE;
            holder.layoutPasswordHistory.setVisibility(visible ? View.GONE : View.VISIBLE);
            holder.btnToggleHistory.setText(visible
                    ? "Ver historial (" + older.size() + ")"
                    : "Ocultar historial");
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

    private void animateExpand(View view) {
        view.setVisibility(View.VISIBLE);
        view.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(
                        ((android.view.View) view.getParent()).getWidth(),
                        android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(0,
                        android.view.View.MeasureSpec.UNSPECIFIED));
        int targetH = view.getMeasuredHeight();
        view.getLayoutParams().height = 0;
        view.requestLayout();

        android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofInt(0, targetH);
        anim.setDuration(250);
        anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
        anim.addUpdateListener(va -> {
            view.getLayoutParams().height = (int) va.getAnimatedValue();
            view.requestLayout();
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                view.getLayoutParams().height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
                view.requestLayout();
            }
        });
        anim.start();
    }

    private void animateCollapse(View view) {
        int initH = view.getMeasuredHeight();
        if (initH == 0) { view.setVisibility(View.GONE); return; }

        android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofInt(initH, 0);
        anim.setDuration(250);
        anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
        anim.addUpdateListener(va -> {
            view.getLayoutParams().height = (int) va.getAnimatedValue();
            view.requestLayout();
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                view.setVisibility(View.GONE);
                view.getLayoutParams().height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
                view.requestLayout();
            }
        });
        anim.start();
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
        final TextInputLayout   tilExpandedTitle;
        final TextInputEditText etExpandedTitle;
        final TextInputLayout   tilExpandedUsername;
        final TextInputEditText etExpandedUsername;
        final TextInputLayout   tilPassword;
        final TextInputEditText etPassword;
        final ImageButton       btnShowPassword;
        final MaterialButton    btnChangePassword;
        final ImageButton       btnPasswordInfo;
        final View              layoutPreviousPassword;
        final TextInputEditText etPreviousPassword;
        final ImageButton       btnShowPreviousPassword;
        final MaterialButton    btnToggleHistory;
        final android.widget.LinearLayout layoutPasswordHistory;
        final TextInputEditText etUrl;
        final TextInputEditText etNotes;
        final MaterialButton    btnSave;
        final MaterialButton    btnDelete;

        TextWatcher titleWatcher;
        TextWatcher usernameWatcher;
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
            tilExpandedTitle       = itemView.findViewById(R.id.tilExpandedTitle);
            etExpandedTitle        = itemView.findViewById(R.id.etExpandedTitle);
            tilExpandedUsername    = itemView.findViewById(R.id.tilExpandedUsername);
            etExpandedUsername     = itemView.findViewById(R.id.etExpandedUsername);
            tilPassword            = itemView.findViewById(R.id.tilPassword);
            etPassword             = itemView.findViewById(R.id.etPassword);
            btnShowPassword        = itemView.findViewById(R.id.btnShowPassword);
            btnChangePassword      = itemView.findViewById(R.id.btnChangePassword);
            btnPasswordInfo        = itemView.findViewById(R.id.btnPasswordInfo);
            layoutPreviousPassword = itemView.findViewById(R.id.layoutPreviousPassword);
            etPreviousPassword     = itemView.findViewById(R.id.etPreviousPassword);
            btnShowPreviousPassword= itemView.findViewById(R.id.btnShowPreviousPassword);
            btnToggleHistory       = itemView.findViewById(R.id.btnToggleHistory);
            layoutPasswordHistory  = itemView.findViewById(R.id.layoutPasswordHistory);
            etUrl                  = itemView.findViewById(R.id.etUrl);
            etNotes                = itemView.findViewById(R.id.etNotes);
            btnSave                = itemView.findViewById(R.id.btnSave);
            btnDelete              = itemView.findViewById(R.id.btnDelete);
        }

        void removeWatchers() {
            if (titleWatcher    != null) etExpandedTitle   .removeTextChangedListener(titleWatcher);
            if (usernameWatcher != null) etExpandedUsername.removeTextChangedListener(usernameWatcher);
            if (urlWatcher      != null) etUrl             .removeTextChangedListener(urlWatcher);
            if (notesWatcher    != null) etNotes           .removeTextChangedListener(notesWatcher);
            titleWatcher = usernameWatcher = urlWatcher = notesWatcher = null;
        }
    }
}
