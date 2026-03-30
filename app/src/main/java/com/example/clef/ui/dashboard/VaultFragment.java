package com.example.clef.ui.dashboard;

import android.os.Bundle;
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
import com.example.clef.data.model.Credential;
import com.example.clef.data.model.Vault;
import com.example.clef.utils.SessionManager;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VaultFragment extends Fragment {

    private VaultAdapter      adapter;
    private RecyclerView      recyclerView;
    private View              layoutEmpty;
    private TextInputEditText etSearch;
    private ChipGroup         chipGroupCategories;

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

        // Refiltrar al cambiar categoría
        chipGroupCategories.setOnCheckedStateChangeListener((group, checkedIds) -> applyFilters());

        adapter = new VaultAdapter(requireContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        view.findViewById(R.id.fabAdd).setOnClickListener(v -> openAddDialog());

        // Búsqueda en tiempo real — antes no tenía TextWatcher
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                applyFilters();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        applyFilters();
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
                String title    = c.getTitle()    != null ? c.getTitle()   .toLowerCase() : "";
                String username = c.getUsername() != null ? c.getUsername().toLowerCase() : "";
                return !title.contains(query) && !username.contains(query);
            });
        }

        showList(list);
    }

    private void showList(List<Credential> credentials) {
        adapter.setCredentials(credentials);
        boolean empty = credentials.isEmpty();
        layoutEmpty  .setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView .setVisibility(empty ? View.GONE    : View.VISIBLE);
    }

    private void openAddDialog() {
        AddItemDialog dialog = AddItemDialog.newInstance();
        dialog.setOnCredentialSavedListener(this::applyFilters);
        dialog.show(getChildFragmentManager(), "add_item");
    }
}