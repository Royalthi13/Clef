package com.example.clef.ui.dashboard;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.clef.R;
import com.example.clef.data.model.Credential;
import com.example.clef.utils.ClipboardHelper;

import java.util.ArrayList;
import java.util.List;

public class VaultAdapter extends RecyclerView.Adapter<VaultAdapter.ViewHolder> {

    private final List<Credential> items = new ArrayList<>();
    private final Context context;

    public VaultAdapter(Context context) {
        this.context = context;
    }

    /** Reemplaza la lista completa y notifica al RecyclerView. */
    public void setCredentials(List<Credential> credentials) {
        items.clear();
        items.addAll(credentials);
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
        Credential credential = items.get(position);

        // Inicial del título para el avatar
        String title = credential.getTitle() != null ? credential.getTitle() : "?";
        holder.tvInitial.setText(String.valueOf(title.charAt(0)).toUpperCase());
        holder.tvTitle.setText(title);
        holder.tvUsername.setText(credential.getUsername());

        holder.btnCopy.setOnClickListener(v ->
                ClipboardHelper.copySensitive(
                        context,
                        context.getString(R.string.credential_copy_label, title),
                        credential.getPassword()
                )
        );
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView    tvInitial;
        final TextView    tvTitle;
        final TextView    tvUsername;
        final ImageButton btnCopy;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvInitial  = itemView.findViewById(R.id.tvInitial);
            tvTitle    = itemView.findViewById(R.id.tvTitle);
            tvUsername = itemView.findViewById(R.id.tvUsername);
            btnCopy    = itemView.findViewById(R.id.btnCopyPassword);
        }
    }
}
