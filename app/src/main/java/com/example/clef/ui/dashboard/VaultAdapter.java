package com.example.clef.ui.dashboard;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.clef.R;
import com.example.clef.data.model.Credential;
import com.example.clef.utils.ClipboardHelper;

import java.util.ArrayList;
import java.util.List;

public class VaultAdapter extends RecyclerView.Adapter<VaultAdapter.ViewHolder> {

    // Tenemos dos listas: la original completa, y la que estamos mostrando ahora mismo
    private final List<Credential> allItems = new ArrayList<>();
    private final List<Credential> displayedItems = new ArrayList<>();
    private final Context context;

    public VaultAdapter(Context context) {
        this.context = context;
    }

    // Guarda la lista completa y la muestra.
    public void setCredentials(List<Credential> credentials) {
        allItems.clear();
        allItems.addAll(credentials);

        displayedItems.clear();
        displayedItems.addAll(credentials);
        notifyDataSetChanged();
    }

    // Metodo para el buscador futuro
    public void filterList(List<Credential> filteredList) {
        displayedItems.clear();
        displayedItems.addAll(filteredList);
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

        //  Tratamiento seguro del título para evitar cuelgues
        String rawTitle = credential.getTitle();
        final String title = (rawTitle == null || rawTitle.trim().isEmpty()) ? "Sin Título" : rawTitle;

        //  Extraemos la inicial de forma segura
        String initial = title.substring(0, 1).toUpperCase();
        holder.tvInitial.setText(initial);

        //  Rellenamos los textos
        holder.tvTitle.setText(title);
        holder.tvUsername.setText(credential.getUsername());
        holder.ivSyncStatus.setVisibility(credential.isSynced() ? View.VISIBLE : View.GONE);

        //  Configuramos el botón de copiar
        holder.btnCopy.setOnClickListener(v ->
                ClipboardHelper.copySensitive(
                        context,
                        title, // Simplificamos el nombre que se copia
                        credential.getPassword()
                )
        );
    }

    @Override
    public int getItemCount() {
        return displayedItems.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivSyncStatus;
        final TextView    tvInitial;
        final TextView    tvTitle;
        final TextView    tvUsername;
        final ImageButton btnCopy;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvInitial  = itemView.findViewById(R.id.tvInitial);
            tvTitle    = itemView.findViewById(R.id.tvTitle);
            tvUsername = itemView.findViewById(R.id.tvUsername);
            ivSyncStatus = itemView.findViewById(R.id.ivSyncStatus);
            btnCopy    = itemView.findViewById(R.id.btnCopyPassword);
        }
    }
}
