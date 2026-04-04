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
import com.example.clef.utils.ExpiryHelper;
import com.example.clef.data.model.Vault;
import com.example.clef.workers.PasswordExpiryWorker;
import com.example.clef.data.repository.VaultRepository;
import com.example.clef.utils.SessionManager;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VaultFragment extends Fragment {

    private VaultAdapter      adapter;
    private RecyclerView      recyclerView;
    private View              layoutEmpty;
    private TextInputEditText etSearch;
    private ChipGroup         chipGroupCategories;

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
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Eliminar credencial")
                        .setMessage("¿Eliminar \"" + credential.getTitle() + "\"?\nEsta acción no se puede deshacer.")
                        .setPositiveButton("Eliminar", (dialog, which) -> deleteCredential(credential))
                        .setNegativeButton("Cancelar", null)
                        .show();
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        // FIX UX: FAB y botón del estado vacío abren el mismo diálogo
        view.findViewById(R.id.fabAdd).setOnClickListener(v -> openAddDialog());
        View btnEmptyAdd = view.findViewById(R.id.btnEmptyAddFirst);
        if (btnEmptyAdd != null) btnEmptyAdd.setOnClickListener(v -> openAddDialog());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { applyFilters(); }
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
        byte[] dek = session.getDek();
        if (dek == null) return;

        executor.execute(() -> {
            try {
                VaultRepository repo = new VaultRepository(requireContext());
                String freshVaultB64 = repo.loadLocalVault();

                Vault vault;
                if (freshVaultB64 != null) {
                    vault = new KeyManager().descifrarVault(freshVaultB64, dek);
                    session.updateVault(vault);
                } else {
                    vault = session.getVault();
                }

                if (vault == null) return;

                List<Credential> list = vault.getCredentials();
                int idx = findCredentialIndex(list, credential);

                if (idx < 0) {
                    mainHandler.post(() -> {
                        if (!isAdded()) return;
                        applyFilters();
                        android.widget.Toast.makeText(requireContext(),
                                "Esta credencial ya no existe en tu bóveda",
                                android.widget.Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                final Credential removed = list.get(idx);
                vault.removeCredential(idx);

                String encrypted = new KeyManager().cifrarVault(vault, dek);
                repo.saveVault(encrypted, new VaultRepository.Callback<Void>() {
                    @Override
                    public void onSuccess(Void r) {
                        session.updateVault(vault);
                        mainHandler.post(() -> { if (isAdded()) applyFilters(); });
                    }

                    @Override
                    public void onError(Exception e) {
                        vault.getCredentials().add(idx, removed);
                        mainHandler.post(() -> {
                            if (!isAdded()) return;
                            applyFilters();
                            android.widget.Toast.makeText(requireContext(),
                                    "Error al eliminar. Inténtalo de nuevo.",
                                    android.widget.Toast.LENGTH_SHORT).show();
                        });
                    }
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    android.widget.Toast.makeText(requireContext(),
                            "Error al acceder a la bóveda",
                            android.widget.Toast.LENGTH_SHORT).show();
                });
            }
        });
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