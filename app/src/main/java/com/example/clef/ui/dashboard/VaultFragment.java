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

    // Executor compartido — se cierra en onDestroyView
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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

        // Chips de categoría dinámicos
        for (Credential.Category cat : Credential.Category.values()) {
            com.google.android.material.chip.Chip chip =
                    new com.google.android.material.chip.Chip(requireContext());
            chip.setText(getString(cat.getLabelRes()));
            chip.setCheckable(true);
            chip.setClickable(true);
            chip.setTag(cat);
            chipGroupCategories.addView(chip);
        }

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

        view.findViewById(R.id.fabAdd).setOnClickListener(v -> openAddDialog());

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

    // ── Borrado correcto ───────────────────────────────────────────────────────

    /**
     * Borra una credencial de la bóveda.
     *
     * IMPORTANTE: No usamos indexOf(credential) porque Credential no sobreescribe
     * equals() y el objeto puede ser una instancia distinta tras reconstruir desde JSON.
     * En su lugar buscamos por referencia de objeto con un bucle explícito,
     * y si no funciona, buscamos por título+username como fallback.
     */
    private void deleteCredential(Credential credential) {
        SessionManager session = SessionManager.getInstance();
        byte[] dek   = session.getDek();
        Vault  vault = session.getVault();
        if (dek == null || vault == null) return;

        List<Credential> list = vault.getCredentials();

        // Buscar por referencia primero
        int idx = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == credential) { idx = i; break; }
        }

        // Fallback: buscar por título + usuario si no encontramos por referencia
        if (idx < 0) {
            for (int i = 0; i < list.size(); i++) {
                Credential c = list.get(i);
                boolean titleMatch = safeEquals(c.getTitle(),    credential.getTitle());
                boolean userMatch  = safeEquals(c.getUsername(), credential.getUsername());
                if (titleMatch && userMatch) { idx = i; break; }
            }
        }

        if (idx < 0) {
            android.widget.Toast.makeText(requireContext(),
                    "No se encontró la credencial", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        final int finalIdx = idx;
        final Credential removed = list.get(finalIdx);
        vault.removeCredential(finalIdx);

        executor.execute(() -> {
            try {
                KeyManager km = new KeyManager();
                String encrypted = km.cifrarVault(vault, dek);
                new VaultRepository(requireContext())
                        .saveVault(encrypted, new VaultRepository.Callback<Void>() {
                            @Override
                            public void onSuccess(Void r) {
                                session.updateVault(vault);
                                mainHandler.post(() -> {
                                    if (isAdded()) applyFilters();
                                });
                            }
                            @Override
                            public void onError(Exception e) {
                                // Revertir el borrado en memoria
                                vault.getCredentials().add(finalIdx, removed);
                                mainHandler.post(() -> {
                                    if (isAdded()) {
                                        applyFilters();
                                        android.widget.Toast.makeText(requireContext(),
                                                "Error al eliminar. Inténtalo de nuevo.",
                                                android.widget.Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        });
            } catch (Exception e) {
                vault.getCredentials().add(finalIdx, removed);
                mainHandler.post(() -> {
                    if (isAdded()) applyFilters();
                });
            }
        });
    }

    private boolean safeEquals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    // ── Filtrado combinado (categoría + texto) ─────────────────────────────────

    private void applyFilters() {
        Vault vault = SessionManager.getInstance().getVault();
        if (vault == null) { showList(Collections.emptyList()); return; }

        List<Credential> list = new ArrayList<>(vault.getCredentials());

        // Filtro por categoría
        int checkedId = chipGroupCategories.getCheckedChipId();
        if (checkedId != View.NO_ID && checkedId != R.id.chipAll) {
            com.google.android.material.chip.Chip chip = chipGroupCategories.findViewById(checkedId);
            if (chip != null && chip.getTag() instanceof Credential.Category) {
                Credential.Category cat = (Credential.Category) chip.getTag();
                list.removeIf(c -> c.getCategory() != cat);
            }
        }

        // Filtro por texto (título o usuario)
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