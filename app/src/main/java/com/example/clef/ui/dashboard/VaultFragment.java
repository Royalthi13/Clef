package com.example.clef.ui.dashboard;

import android.os.Bundle;
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

import java.util.Collections;

public class VaultFragment extends Fragment {

    private VaultAdapter adapter;
    private RecyclerView recyclerView;
    private View layoutEmpty;

    private TextInputEditText etSearch;
    private ChipGroup chipGroupCategories;

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

        // Enlazamos las variables con el XML
        recyclerView = view.findViewById(R.id.recyclerViewVault);
        layoutEmpty = view.findViewById(R.id.layoutEmpty);
        etSearch = view.findViewById(R.id.etSearch);
        chipGroupCategories = view.findViewById(R.id.chipGroupCategories);
        for (Credential.Category cat : Credential.Category.values()) {
            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(requireContext());
            chip.setText(getString(cat.getLabelRes()));
            chip.setCheckable(true);
            chip.setClickable(true);
            chip.setTag(cat);
            chipGroupCategories.addView(chip);
        }
        chipGroupCategories.setOnCheckedStateChangeListener((group, checkedIds) -> applyFilters());
        // Configuramos la lista
        adapter = new VaultAdapter(requireContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        // Configuramos el botón +
        view.findViewById(R.id.fabAdd).setOnClickListener(v -> openAddDialog());
    }

    @Override
    public void onResume() {
        super.onResume();
        // Recargamos cada vez que el fragmento vuelve a ser visible,
        // así la lista se actualiza tras añadir una credencial o tras importar
        loadCredentials();
    }

    private void loadCredentials() {
        applyFilters();
    }

    private void showList(java.util.List<com.example.clef.data.model.Credential> credentials) {
        adapter.setCredentials(credentials);


        boolean isEmpty = credentials.isEmpty();

        // Si está vacío, mostramos el layoutEmpty y escondemos la lista
        layoutEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }
    private void applyFilters() {
        Vault vault = SessionManager.getInstance().getVault();
        if (vault == null) { showList(Collections.emptyList()); return; }

        int checkedId = chipGroupCategories.getCheckedChipId();
        java.util.List<Credential> list = new java.util.ArrayList<>(vault.getCredentials());

        // Filtro por categoría (chipAll = R.id.chipAll, el resto tienen tag Category)
        if (checkedId != View.NO_ID && checkedId != R.id.chipAll) {
            com.google.android.material.chip.Chip chip = chipGroupCategories.findViewById(checkedId);
            if (chip != null && chip.getTag() instanceof Credential.Category) {
                Credential.Category cat = (Credential.Category) chip.getTag();
                list.removeIf(c -> c.getCategory() != cat);
            }
        }

        showList(list);
    }
    private void openAddDialog() {
        AddItemDialog dialog = AddItemDialog.newInstance();
        dialog.setOnCredentialSavedListener(this::loadCredentials);
        dialog.show(getChildFragmentManager(), "add_item");
    }

}
