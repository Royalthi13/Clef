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

import com.example.clef.R;
import com.example.clef.crypto.KeyManager;
import com.example.clef.data.model.Credential;
import com.example.clef.data.model.Vault;
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

    private final ExecutorService executor    = Executors.newSingleThreadExecutor();
    private final Handler         mainHandler = new Handler(Looper.getMainLooper());

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
        adapter.setOnDeleteListener((position, credential) ->
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Eliminar credencial")
                        .setMessage("¿Eliminar \"" + credential.getTitle() + "\"?\nEsta acción no se puede deshacer.")
                        .setPositiveButton("Eliminar", (dialog, which) -> deleteCredential(credential))
                        .setNegativeButton("Cancelar", null)
                        .show()
        );

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
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) applyFilters();
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