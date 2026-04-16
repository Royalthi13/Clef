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
import android.util.Log;

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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VaultFragment extends Fragment {

    private static final String TAG = "VaultFragment";

    private enum SortOrder { RECENT, ALPHABETICAL, EXPIRY }

    private VaultAdapter      adapter;
    private RecyclerView      recyclerView;
    private View              layoutEmpty;
    private TextInputEditText etSearch;
    private ChipGroup         chipGroupCategories;
    private com.google.android.material.button.MaterialButton btnSort;
    private SortOrder         currentSort = SortOrder.RECENT;

    private final ExecutorService executor        = Executors.newSingleThreadExecutor();
    private final Handler         mainHandler     = new Handler(Looper.getMainLooper());
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
        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(requireContext());
        for (Credential.Category cat : Credential.Category.values()) {
            Chip chip = (Chip) inflater.inflate(
                    R.layout.item_filter_chip, chipGroupCategories, false);
            chip.setText(getString(cat.getLabelRes()));
            chip.setTag(cat);
            chipGroupCategories.addView(chip);
        }

        Chip chipAll = view.findViewById(R.id.chipAll);
        if (chipAll != null) chipAll.setChecked(true);

        chipGroupCategories.setOnCheckedStateChangeListener((group, checkedIds) -> applyFilters());

        adapter = new VaultAdapter(requireContext());
        adapter.setOnCredentialActionListener(new VaultAdapter.OnCredentialActionListener() {
            @Override
            public void onSave(Credential credential) { saveCredential(credential); }

            @Override
            public void onDelete(Credential credential) {
                String titulo = credential.getTitle() != null
                        ? credential.getTitle() : "esta credencial";
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Eliminar credencial")
                        .setMessage("¿Eliminar \"" + titulo + "\"?")
                        .setPositiveButton("Eliminar", (dialog, which) -> {
                            if (BiometricHelper.isAvailable(requireContext())) {
                                BiometricHelper.confirmIdentity(
                                        requireActivity(),
                                        "Confirmar eliminación",
                                        "Verifica tu identidad para eliminar \"" + titulo + "\"",
                                        new BiometricHelper.ConfirmCallback() {
                                            @Override public void onConfirmed() {
                                                deleteCredential(credential);
                                            }
                                            @Override public void onCancelled() {}
                                        });
                            } else {
                                confirmWithMasterPassword(credential);
                            }
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        androidx.recyclerview.widget.RecyclerView.ItemAnimator animator =
                recyclerView.getItemAnimator();
        if (animator instanceof androidx.recyclerview.widget.SimpleItemAnimator) {
            ((androidx.recyclerview.widget.SimpleItemAnimator) animator)
                    .setSupportsChangeAnimations(false);
        }

        view.findViewById(R.id.fabAdd).setOnClickListener(v -> openAddDialog());
        View btnEmptyAdd = view.findViewById(R.id.btnEmptyAddFirst);
        if (btnEmptyAdd != null) btnEmptyAdd.setOnClickListener(v -> openAddDialog());

        String tipUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "anon";
        android.content.SharedPreferences tipPrefs =
                SecurePrefs.get(requireContext(), "generator_prefs_" + tipUid);
        View cardTip = view.findViewById(R.id.cardGeneratorTip);
        if (tipPrefs.getBoolean("tip_dismissed", false)) {
            cardTip.setVisibility(View.GONE);
        } else {
            view.findViewById(R.id.btnTipDismiss).setOnClickListener(v -> {
                tipPrefs.edit().putBoolean("tip_dismissed", true).apply();
                cardTip.animate().alpha(0f).setDuration(250)
                        .withEndAction(() -> cardTip.setVisibility(View.GONE)).start();
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
                                : which == 1 ? SortOrder.ALPHABETICAL : SortOrder.EXPIRY;
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
        firebaseManager.addVaultListener(this::onCloudVaultChanged);
    }

    // ── M-3 + C-4 FIX ─────────────────────────────────────────────────────

    /**
     * M-3 FIX: antes el catch swalloeaba cualquier excepción silenciosamente
     * y la DEK clonada quedaba en heap sin zerizar.
     *
     * C-4 FIX: la DEK clonada por getDek() se zeriza SIEMPRE en el finally,
     * tanto si la operación tuvo éxito como si lanzó excepción.
     */
    private void onCloudVaultChanged(String encryptedCloudVault, long version) {
        SessionManager session = SessionManager.getInstance();
        byte[] dek = session.getDek(); // clon — DEBE zerisar en finally
        if (dek == null) return;       // sesión bloqueada antes del listener

        executor.execute(() -> {
            try {
                // M-3 FIX: verificar que la sesión sigue activa antes de descifrar.
                // Si lock() disparó entre getDek() y este punto, no intentar descifrar.
                if (!session.isUnlocked()) return;

                Vault cloudVault = new KeyManager().descifrarVault(encryptedCloudVault, dek);

                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    Vault localVault = session.getVault();
                    if (localVault == null) return;

                    List<Credential> list = localVault.getCredentials();
                    list.removeIf(Credential::isSynced);
                    for (Credential c : cloudVault.getCredentials()) {
                        c.setSynced(true);
                        list.add(c);
                    }
                    session.setCloudVaultVersion(version);

                    Context ctx = requireContext();
                    // C-4 FIX: nuevo getDek() para el subhilo — este también se zeriza en finally
                    byte[] dekForSave = session.getDek();
                    if (dekForSave != null) {
                        executor.execute(() -> {
                            try {
                                String enc = new KeyManager().cifrarVault(localVault, dekForSave);
                                new VaultRepository(ctx).saveLocalVaultOnly(enc);
                            } catch (Exception e) {
                                Log.e(TAG, "onCloudVaultChanged: error guardando local", e);
                            } finally {
                                SessionManager.zeroizeDekCopy(dekForSave);
                            }
                        });
                    }

                    session.updateVault(localVault);
                    applyFilters();
                });
            } catch (Exception e) {
                // M-3 FIX: logear en lugar de swallowear silenciosamente
                Log.w(TAG, "onCloudVaultChanged: error al descifrar vault remoto", e);
            } finally {
                // C-4 FIX: siempre zerizar el clon de la DEK
                SessionManager.zeroizeDekCopy(dek);
            }
        });
    }

    // ── Guardado ───────────────────────────────────────────────────────────

    /**
     * C-4 FIX: el clon de DEK se zeriza en finally del executor.
     */
    private void saveCredential(Credential credential) {
        SessionManager session = SessionManager.getInstance();
        byte[] dek   = session.getDek(); // clon
        Vault  vault = session.getVault();
        if (dek == null || vault == null) return;

        final long expectedVersion = session.getCloudVaultVersion();

        executor.execute(() -> {
            try {
                String encrypted = new KeyManager().cifrarVault(vault, dek);
                VaultRepository repo = new VaultRepository(requireContext());
                repo.saveLocalVaultOnly(encrypted);

                if (credential.isSynced()) {
                    repo.uploadSyncedOnly(vault, dek, expectedVersion,
                            new VaultRepository.Callback<Void>() {
                                @Override
                                public void onSuccess(Void r) {
                                    session.setCloudVaultVersion(expectedVersion + 1);
                                    session.updateVault(vault);
                                    mainHandler.post(() -> {
                                        if (isAdded()) adapter.refreshWithoutCollapse();
                                    });
                                }

                                @Override
                                public void onError(Exception e) {
                                    if (FirebaseManager.CONFLICT_ERROR.equals(e.getMessage())) {
                                        // Para retryAfterConflict necesitamos otro clon;
                                        // el dek actual se zeriza en finally de este execute.
                                        byte[] dekRetry = session.getDek();
                                        if (dekRetry != null) {
                                            retryAfterConflict(credential, vault, dekRetry);
                                        }
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
                Log.e(TAG, "saveCredential: error", e);
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    android.widget.Toast.makeText(requireContext(),
                            "Error al guardar", android.widget.Toast.LENGTH_SHORT).show();
                });
            } finally {
                // C-4 FIX: zerizar siempre el clon
                SessionManager.zeroizeDekCopy(dek);
            }
        });
    }

    private void retryAfterConflict(Credential pendingCredential, Vault staleVault, byte[] dek) {
        VaultRepository repo = new VaultRepository(requireContext());
        repo.downloadAndCacheFromFirebase(
                new VaultRepository.Callback<FirebaseManager.UserData>() {
                    @Override
                    public void onSuccess(FirebaseManager.UserData userData) {
                        if (userData == null || userData.vault == null) {
                            SessionManager.zeroizeDekCopy(dek);
                            return;
                        }
                        executor.execute(() -> {
                            try {
                                Vault freshVault = new KeyManager()
                                        .descifrarVault(userData.vault, dek);

                                List<Credential> merged = new ArrayList<>();
                                for (Credential c : freshVault.getCredentials()) {
                                    c.setSynced(true);
                                    merged.add(c);
                                }
                                for (Credential c : staleVault.getCredentials()) {
                                    if (!c.isSynced()) merged.add(c);
                                }
                                freshVault.setCredentials(merged);

                                List<Credential> list = freshVault.getCredentials();
                                int idx = findCredentialIndex(list, pendingCredential);
                                if (idx >= 0) list.set(idx, pendingCredential);
                                else list.add(pendingCredential);

                                String encLocal = new KeyManager().cifrarVault(freshVault, dek);
                                repo.saveLocalVaultOnly(encLocal);

                                final long newExpected = userData.version;
                                SessionManager session = SessionManager.getInstance();
                                repo.uploadSyncedOnly(freshVault, dek, newExpected,
                                        new VaultRepository.Callback<Void>() {
                                            @Override
                                            public void onSuccess(Void r) {
                                                session.setCloudVaultVersion(newExpected + 1);
                                                session.updateVault(freshVault);
                                                mainHandler.post(() -> {
                                                    if (isAdded()) adapter.refreshWithoutCollapse();
                                                });
                                            }
                                            @Override
                                            public void onError(Exception e) {
                                                session.updateVault(freshVault);
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
                                Log.e(TAG, "retryAfterConflict: error", e);
                                mainHandler.post(() -> {
                                    if (!isAdded()) return;
                                    android.widget.Toast.makeText(requireContext(),
                                            "Error al guardar",
                                            android.widget.Toast.LENGTH_SHORT).show();
                                });
                            } finally {
                                // C-4 FIX: zerizar el clon pasado a este método
                                SessionManager.zeroizeDekCopy(dek);
                            }
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        SessionManager.zeroizeDekCopy(dek);
                        mainHandler.post(() -> {
                            if (!isAdded()) return;
                            android.widget.Toast.makeText(requireContext(),
                                    "Guardado local. Error al sincronizar.",
                                    android.widget.Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }

    // ── Borrado ────────────────────────────────────────────────────────────

    private void confirmWithMasterPassword(Credential credential) {
        android.view.View dialogView = android.view.LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_verify_password, null);
        com.google.android.material.textfield.TextInputEditText etPassword =
                dialogView.findViewById(R.id.etVerifyPassword);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Confirmar eliminación")
                .setMessage("Introduce tu contraseña maestra para confirmar")
                .setView(dialogView)
                .setPositiveButton("Confirmar", (d, w) -> {
                    String pwd = etPassword.getText() != null
                            ? etPassword.getText().toString() : "";
                    if (pwd.isEmpty()) return;
                    verifyMasterAndDelete(credential, pwd.toCharArray());
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void verifyMasterAndDelete(Credential credential, char[] passwordChars) {
        VaultRepository repo = new VaultRepository(requireContext());
        FirebaseManager.UserData data = repo.loadOfflineUserData();
        if (data == null || data.salt == null || data.cajaA == null) {
            Arrays.fill(passwordChars, '\0');
            android.widget.Toast.makeText(requireContext(),
                    "No se pueden verificar las credenciales.",
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        executor.execute(() -> {
            try {
                new KeyManager().login(passwordChars, data.salt, data.cajaA, data.vault);
                mainHandler.post(() -> deleteCredential(credential));
            } catch (Exception e) {
                mainHandler.post(() -> android.widget.Toast.makeText(requireContext(),
                        "Contraseña incorrecta.",
                        android.widget.Toast.LENGTH_SHORT).show());
            } finally {
                Arrays.fill(passwordChars, '\0');
            }
        });
    }
    /**
     * C-4 FIX: el clon de DEK se zeriza en finally del Snackbar callback.
     */
    private void deleteCredential(Credential credential) {
        SessionManager session = SessionManager.getInstance();
        byte[] dek   = session.getDek(); // clon
        Vault  vault = session.getVault();
        if (dek == null || vault == null) return;

        List<Credential> list = vault.getCredentials();
        int idx = findCredentialIndex(list, credential);
        if (idx < 0) {
            SessionManager.zeroizeDekCopy(dek);
            applyFilters();
            return;
        }

        list.remove(idx);
        session.updateVault(vault);
        applyFilters();

        View root = getView();
        if (root == null) {
            SessionManager.zeroizeDekCopy(dek);
            return;
        }

        String titulo = credential.getTitle() != null ? credential.getTitle() : "Credencial";

        com.google.android.material.snackbar.Snackbar
                .make(root, "\"" + titulo + "\" eliminada", 5000)
                .setAction("Deshacer", v -> {
                    // Restaurar en memoria — dek sigue vivo hasta onDismissed
                    list.add(idx, credential);
                    session.updateVault(vault);
                    applyFilters();
                })
                .addCallback(new com.google.android.material.snackbar.Snackbar.Callback() {
                    @Override
                    public void onDismissed(
                            com.google.android.material.snackbar.Snackbar sb, int event) {
                        if (event == DISMISS_EVENT_ACTION) {
                            // Usuario pulsó "Deshacer" — limpiar dek sin persistir
                            SessionManager.zeroizeDekCopy(dek);
                            return;
                        }
                        // Persistir la eliminación
                        executor.execute(() -> {
                            try {
                                String encrypted = new KeyManager().cifrarVault(vault, dek);
                                VaultRepository repo = new VaultRepository(
                                        getContext() != null ? getContext() : root.getContext());
                                repo.saveLocalVaultOnly(encrypted);
                                if (credential.isSynced()) {
                                    long expectedVersion = session.getCloudVaultVersion();
                                    repo.uploadSyncedOnly(vault, dek, expectedVersion,
                                            new VaultRepository.Callback<Void>() {
                                                @Override public void onSuccess(Void r) {}
                                                @Override public void onError(Exception e) {
                                                    list.add(idx, credential);
                                                    session.updateVault(vault);
                                                    mainHandler.post(() -> {
                                                        if (!isAdded()) return;
                                                        applyFilters();
                                                        android.widget.Toast.makeText(
                                                                        requireContext(),
                                                                        "Error al eliminar. Inténtalo de nuevo.",
                                                                        android.widget.Toast.LENGTH_SHORT)
                                                                .show();
                                                    });
                                                }
                                            });
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "deleteCredential: error persistiendo", e);
                                list.add(idx, credential);
                                session.updateVault(vault);
                                mainHandler.post(() -> { if (isAdded()) applyFilters(); });
                            } finally {
                                // C-4 FIX: zerizar siempre el clon
                                SessionManager.zeroizeDekCopy(dek);
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

    // ── Filtrado ──────────────────────────────────────────────────────────

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