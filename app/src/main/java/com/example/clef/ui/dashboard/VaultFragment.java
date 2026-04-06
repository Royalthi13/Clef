package com.example.clef.ui.dashboard;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;

import com.example.clef.R;
import com.example.clef.crypto.KeyManager;
import com.example.clef.data.model.Credential;
import com.example.clef.data.remote.FirebaseManager;
import com.example.clef.utils.BiometricHelper;
import com.example.clef.utils.ExpiryHelper;
import com.example.clef.utils.SecurePrefs;
import com.example.clef.data.model.Vault;
import com.example.clef.workers.PasswordExpiryWorker;
import com.example.clef.data.repository.VaultRepository;
import com.example.clef.utils.SessionManager;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VaultFragment extends Fragment {

    private enum SortOrder { RECENT, ALPHABETICAL, EXPIRY }

    private VaultAdapter      adapter;
    private RecyclerView      recyclerView;
    private View              layoutEmpty;
    private TextInputEditText etSearch;
    private ChipGroup         chipGroupCategories;
    private com.google.android.material.button.MaterialButton btnSort;
    private SortOrder         currentSort = SortOrder.RECENT;

    private final ExecutorService executor      = Executors.newSingleThreadExecutor();
    private final Handler         mainHandler   = new Handler(Looper.getMainLooper());
    private final FirebaseManager firebaseManager = new FirebaseManager();

    private final Runnable expiryRefreshRunnable = new Runnable() {
        @Override public void run() {
            if (isAdded()) {
                adapter.notifyDataSetChanged();
                PasswordExpiryWorker.checkAndNotify(requireContext());
                mainHandler.postDelayed(this, 60_000);
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_vault, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView        = view.findViewById(R.id.recyclerViewVault);
        layoutEmpty         = view.findViewById(R.id.layoutEmpty);
        etSearch            = view.findViewById(R.id.etSearch);
        chipGroupCategories = view.findViewById(R.id.chipGroupCategories);
        btnSort             = view.findViewById(R.id.btnSort);
        for (Credential.Category cat : Credential.Category.values()) {
            Chip chip = new Chip(requireContext());
            chip.setText(getString(cat.getLabelRes()));
            chip.setCheckable(true);
            chip.setClickable(true);
            chip.setTag(cat);
            chipGroupCategories.addView(chip);
        }

        // FIX UX: Chip "Todos" seleccionado por defecto
        Chip chipAll = view.findViewById(R.id.chipAll);
        if (chipAll != null) chipAll.setChecked(true);

        chipGroupCategories.setOnCheckedStateChangeListener((group, checkedIds) -> applyFilters());

        adapter = new VaultAdapter(requireContext());
        adapter.setOnCredentialActionListener(new VaultAdapter.OnCredentialActionListener() {
            @Override
            public void onSave(Credential credential) {
                saveCredential(credential);
            }

            @Override
            public void onDelete(Credential credential) {
                String titulo = credential.getTitle() != null ? credential.getTitle() : "esta credencial";
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Eliminar credencial")
                        .setMessage("¿Eliminar \"" + titulo + "\"?")
                        .setPositiveButton("Eliminar", (dialog, which) ->
                                BiometricHelper.confirmIdentity(
                                        requireActivity(),
                                        "Confirmar eliminación",
                                        "Verifica tu identidad para eliminar \"" + titulo + "\"",
                                        new BiometricHelper.ConfirmCallback() {
                                            @Override public void onConfirmed() {
                                                deleteCredential(credential);
                                            }
                                            @Override public void onCancelled() {}
                                        }))
                        .setNegativeButton("Cancelar", null)
                        .show();
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        // Evitar el fade por defecto al notifyItemChanged (causa parpadeo al expandir)
        androidx.recyclerview.widget.RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
        if (animator instanceof androidx.recyclerview.widget.SimpleItemAnimator) {
            ((androidx.recyclerview.widget.SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }

        // FIX UX: FAB y botón del estado vacío abren el mismo diálogo
        view.findViewById(R.id.fabAdd).setOnClickListener(v -> openAddDialog());
        View btnEmptyAdd = view.findViewById(R.id.btnEmptyAddFirst);
        if (btnEmptyAdd != null) btnEmptyAdd.setOnClickListener(v -> openAddDialog());

        // Banner tip icono generador
        String tipUid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null
                ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "anon";
        android.content.SharedPreferences tipPrefs = SecurePrefs.get(requireContext(), "generator_prefs_" + tipUid);
        View cardTip = view.findViewById(R.id.cardGeneratorTip);
        if (tipPrefs.getBoolean("tip_dismissed", false)) {
            cardTip.setVisibility(View.GONE);
        } else {
            view.findViewById(R.id.btnTipDismiss).setOnClickListener(v -> {
                tipPrefs.edit().putBoolean("tip_dismissed", true).apply();
                cardTip.animate().alpha(0f).setDuration(250)
                        .withEndAction(() -> cardTip.setVisibility(View.GONE))
                        .start();
            });
        }

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { applyFilters(); }
        });

        btnSort.setOnClickListener(v -> {
            String[] options = {"Más reciente", "Alfabético", "Por caducidad"};
            int checked = currentSort == SortOrder.RECENT ? 0
                        : currentSort == SortOrder.ALPHABETICAL ? 1 : 2;
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Ordenar por")
                    .setSingleChoiceItems(options, checked, (dialog, which) -> {
                        currentSort = which == 0 ? SortOrder.RECENT
                                    : which == 1 ? SortOrder.ALPHABETICAL
                                    : SortOrder.EXPIRY;
                        btnSort.setText(options[which] + " ▾");
                        dialog.dismiss();
                        applyFilters();
                    })
                    .show();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        executor.shutdownNow();
    }

    @Override
    public void onResume() {
        super.onResume();
        applyFilters();
        mainHandler.postDelayed(expiryRefreshRunnable, 60_000);
        startVaultListener();
    }

    @Override
    public void onPause() {
        super.onPause();
        mainHandler.removeCallbacks(expiryRefreshRunnable);
        firebaseManager.removeVaultListener();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            applyFilters();
            mainHandler.postDelayed(expiryRefreshRunnable, 60_000);
            startVaultListener();
        } else {
            mainHandler.removeCallbacks(expiryRefreshRunnable);
            firebaseManager.removeVaultListener();
        }
    }

    private void startVaultListener() {
        firebaseManager.addVaultListener((encryptedCloudVault, version) ->
                onCloudVaultChanged(encryptedCloudVault, version));
    }

    /** Llamado en el hilo principal cuando Firebase detecta un cambio en el vault. */
    private void onCloudVaultChanged(String encryptedCloudVault, long version) {
        SessionManager session = SessionManager.getInstance();
        byte[] dek = session.getDek();
        if (dek == null) return;

        executor.execute(() -> {
            try {
                Vault cloudVault = new KeyManager().descifrarVault(encryptedCloudVault, dek);

                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    Vault localVault = session.getVault();
                    if (localVault == null) return;

                    // Sustituir credenciales synced locales con las de la nube
                    List<Credential> list = localVault.getCredentials();
                    list.removeIf(Credential::isSynced);
                    for (Credential c : cloudVault.getCredentials()) {
                        c.setSynced(true);
                        list.add(c);
                    }

                    // Actualizar versión conocida
                    session.setCloudVaultVersion(version);

                    Context ctx = requireContext();
                    executor.execute(() -> {
                        try {
                            String enc = new KeyManager().cifrarVault(localVault, dek);
                            new VaultRepository(ctx).saveLocalVaultOnly(enc);
                        } catch (Exception ignored) {}
                    });

                    session.updateVault(localVault);
                    applyFilters();
                });
            } catch (Exception ignored) {}
        });
    }

    // ── Guardado ───────────────────────────────────────────────────────────────

    private void saveCredential(Credential credential) {
        SessionManager session = SessionManager.getInstance();
        byte[] dek  = session.getDek();
        Vault  vault = session.getVault();
        if (dek == null || vault == null) return;

        final long expectedVersion = session.getCloudVaultVersion();

        executor.execute(() -> {
            try {
                String encrypted = new KeyManager().cifrarVault(vault, dek);
                VaultRepository repo = new VaultRepository(requireContext());

                // Siempre guardar el vault completo en local
                repo.saveLocalVaultOnly(encrypted);

                if (credential.isSynced()) {
                    // Subir a Firebase solo las credenciales con synced=true
                    repo.uploadSyncedOnly(vault, dek, expectedVersion, new VaultRepository.Callback<Void>() {
                        @Override
                        public void onSuccess(Void r) {
                            session.setCloudVaultVersion(expectedVersion + 1);
                            session.updateVault(vault);
                            mainHandler.post(() -> { if (isAdded()) adapter.refreshWithoutCollapse(); });
                        }

                        @Override
                        public void onError(Exception e) {
                            if (com.example.clef.data.remote.FirebaseManager.CONFLICT_ERROR.equals(e.getMessage())) {
                                retryAfterConflict(credential, vault, dek);
                            } else {
                                session.updateVault(vault);
                                mainHandler.post(() -> {
                                    if (!isAdded()) return;
                                    adapter.refreshWithoutCollapse();
                                    android.widget.Toast.makeText(requireContext(),
                                            "Guardado local. Error al sincronizar.",
                                            android.widget.Toast.LENGTH_SHORT).show();
                                });
                            }
                        }
                    });
                } else {
                    session.updateVault(vault);
                    mainHandler.post(() -> { if (isAdded()) adapter.refreshWithoutCollapse(); });
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    android.widget.Toast.makeText(requireContext(),
                            "Error al guardar", android.widget.Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void retryAfterConflict(Credential pendingCredential, Vault staleVault, byte[] dek) {
        VaultRepository repo = new VaultRepository(requireContext());
        repo.downloadAndCacheFromFirebase(new VaultRepository.Callback<com.example.clef.data.remote.FirebaseManager.UserData>() {
            @Override
            public void onSuccess(com.example.clef.data.remote.FirebaseManager.UserData userData) {
                if (userData == null || userData.vault == null) return;
                executor.execute(() -> {
                    try {
                        // Descifrar el vault fresco de Firebase
                        Vault freshVault = new KeyManager().descifrarVault(userData.vault, dek);

                        // Fusionar: credenciales cloud (synced=true) + locales no synced del vault en sesión
                        java.util.List<Credential> merged = new java.util.ArrayList<>();
                        for (Credential c : freshVault.getCredentials()) {
                            c.setSynced(true);
                            merged.add(c);
                        }
                        for (Credential c : staleVault.getCredentials()) {
                            if (!c.isSynced()) merged.add(c);
                        }
                        freshVault.setCredentials(merged);

                        // Aplicar el cambio pendiente encima del vault fresco
                        java.util.List<Credential> list = freshVault.getCredentials();
                        int idx = findCredentialIndex(list, pendingCredential);
                        if (idx >= 0) {
                            list.set(idx, pendingCredential);
                        } else {
                            list.add(pendingCredential);
                        }

                        // Guardar localmente y subir con la versión nueva
                        String encLocal = new KeyManager().cifrarVault(freshVault, dek);
                        repo.saveLocalVaultOnly(encLocal);

                        final long newExpected = userData.version;
                        repo.uploadSyncedOnly(freshVault, dek, newExpected, new VaultRepository.Callback<Void>() {
                            @Override
                            public void onSuccess(Void r) {
                                SessionManager session = SessionManager.getInstance();
                                session.setCloudVaultVersion(newExpected + 1);
                                session.updateVault(freshVault);
                                mainHandler.post(() -> { if (isAdded()) adapter.refreshWithoutCollapse(); });
                            }

                            @Override
                            public void onError(Exception e) {
                                SessionManager.getInstance().updateVault(freshVault);
                                mainHandler.post(() -> {
                                    if (!isAdded()) return;
                                    adapter.refreshWithoutCollapse();
                                    android.widget.Toast.makeText(requireContext(),
                                            "Guardado local. Error al sincronizar.",
                                            android.widget.Toast.LENGTH_SHORT).show();
                                });
                            }
                        });
                    } catch (Exception e) {
                        mainHandler.post(() -> {
                            if (!isAdded()) return;
                            android.widget.Toast.makeText(requireContext(),
                                    "Error al guardar", android.widget.Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    android.widget.Toast.makeText(requireContext(),
                            "Guardado local. Error al sincronizar.",
                            android.widget.Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    // ── Borrado ────────────────────────────────────────────────────────────────

    private void deleteCredential(Credential credential) {
        SessionManager session = SessionManager.getInstance();
        byte[] dek   = session.getDek();
        Vault  vault = session.getVault();
        if (dek == null || vault == null) return;

        List<Credential> list = vault.getCredentials();
        int idx = findCredentialIndex(list, credential);
        if (idx < 0) { applyFilters(); return; }

        // Borrar de memoria inmediatamente y refrescar UI
        list.remove(idx);
        session.updateVault(vault);
        applyFilters();

        View root = getView();
        if (root == null) return;

        String titulo = credential.getTitle() != null ? credential.getTitle() : "Credencial";

        com.google.android.material.snackbar.Snackbar
                .make(root, "\"" + titulo + "\" eliminada", 5000)
                .setAction("Deshacer", v -> {
                    // Restaurar en memoria
                    list.add(idx, credential);
                    session.updateVault(vault);
                    applyFilters();
                })
                .addCallback(new com.google.android.material.snackbar.Snackbar.Callback() {
                    @Override
                    public void onDismissed(com.google.android.material.snackbar.Snackbar sb, int event) {
                        // Solo persistir si no se pulsó "Deshacer"
                        if (event == DISMISS_EVENT_ACTION) return;
                        executor.execute(() -> {
                            try {
                                String encrypted = new KeyManager().cifrarVault(vault, dek);
                                VaultRepository repo = new VaultRepository(
                                        getContext() != null ? getContext() : root.getContext());
                                repo.saveLocalVaultOnly(encrypted);
                                if (credential.isSynced()) {
                                    long expectedVersion = session.getCloudVaultVersion();
                                    repo.uploadSyncedOnly(vault, dek, expectedVersion, new VaultRepository.Callback<Void>() {
                                        @Override public void onSuccess(Void r) {}
                                        @Override public void onError(Exception e) {
                                            list.add(idx, credential);
                                            session.updateVault(vault);
                                            mainHandler.post(() -> {
                                                if (!isAdded()) return;
                                                applyFilters();
                                                android.widget.Toast.makeText(requireContext(),
                                                        "Error al eliminar. Inténtalo de nuevo.",
                                                        android.widget.Toast.LENGTH_SHORT).show();
                                            });
                                        }
                                    });
                                }
                            } catch (Exception e) {
                                list.add(idx, credential);
                                session.updateVault(vault);
                                mainHandler.post(() -> { if (isAdded()) applyFilters(); });
                            }
                        });
                    }
                })
                .show();
    }

    private int findCredentialIndex(List<Credential> list, Credential target) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == target) return i;
        }
        for (int i = 0; i < list.size(); i++) {
            Credential c = list.get(i);
            if (safeEquals(c.getTitle(),    target.getTitle()) &&
                    safeEquals(c.getUsername(), target.getUsername())) {
                return i;
            }
        }
        return -1;
    }

    private boolean safeEquals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    // ── Filtrado ──────────────────────────────────────────────────────────────

    private void applyFilters() {
        Vault vault = SessionManager.getInstance().getVault();
        if (vault == null) { showList(Collections.emptyList()); return; }

        ExpiryHelper.saveMetadata(requireContext(), vault.getCredentials());
        PasswordExpiryWorker.checkAndNotify(requireContext());

        List<Credential> list = new ArrayList<>(vault.getCredentials());

        int checkedId = chipGroupCategories.getCheckedChipId();
        if (checkedId != View.NO_ID && checkedId != R.id.chipAll) {
            Chip chip = chipGroupCategories.findViewById(checkedId);
            if (chip != null && chip.getTag() instanceof Credential.Category) {
                Credential.Category cat = (Credential.Category) chip.getTag();
                list.removeIf(c -> c.getCategory() != cat);
            }
        }

        String query = etSearch.getText() != null
                ? etSearch.getText().toString().trim().toLowerCase() : "";
        if (!query.isEmpty()) {
            list.removeIf(c -> {
                String t = c.getTitle()    != null ? c.getTitle()   .toLowerCase() : "";
                String u = c.getUsername() != null ? c.getUsername().toLowerCase() : "";
                return !t.contains(query) && !u.contains(query);
            });
        }

        // Ordenación
        android.content.SharedPreferences prefs =
                SecurePrefs.get(requireContext(), ExpiryHelper.PREFS_NAME);
        long periodMs = prefs.getLong(ExpiryHelper.PREF_PERIOD, ExpiryHelper.PERIOD_ONE_YEAR);

        switch (currentSort) {
            case ALPHABETICAL:
                list.sort((a, b) -> {
                    String ta = a.getTitle() != null ? a.getTitle().toLowerCase() : "";
                    String tb = b.getTitle() != null ? b.getTitle().toLowerCase() : "";
                    return ta.compareTo(tb);
                });
                break;
            case EXPIRY:
                list.sort((a, b) -> {
                    ExpiryHelper.Status sa = ExpiryHelper.getStatus(a.getUpdatedAt(), periodMs);
                    ExpiryHelper.Status sb = ExpiryHelper.getStatus(b.getUpdatedAt(), periodMs);
                    // Expiradas primero, luego warning, luego ok, luego sin período
                    return sb.ordinal() - sa.ordinal();
                });
                break;
            case RECENT:
            default:
                list.sort((a, b) -> {
                    long ta = a.getLastUsedAt() > 0 ? a.getLastUsedAt() : a.getUpdatedAt();
                    long tb = b.getLastUsedAt() > 0 ? b.getLastUsedAt() : b.getUpdatedAt();
                    return Long.compare(tb, ta);
                });
                break;
        }

        showList(list);
    }

    private void showList(List<Credential> credentials) {
        adapter.setCredentials(credentials);
        boolean empty = credentials.isEmpty();
        layoutEmpty .setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE    : View.VISIBLE);
    }

    private void openAddDialog() {
        AddItemDialog dialog = AddItemDialog.newInstance();
        dialog.setOnCredentialSavedListener(this::applyFilters);
        dialog.show(getChildFragmentManager(), "add_item");
    }
}