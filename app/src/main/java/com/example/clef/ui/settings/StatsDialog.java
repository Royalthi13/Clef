package com.example.clef.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.clef.R;
import com.example.clef.data.model.Credential;
import com.example.clef.data.model.Vault;
import com.example.clef.utils.ExpiryHelper;
import com.example.clef.utils.PasswordStrengthHelper;
import com.example.clef.utils.SessionManager;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;

public class StatsDialog extends BottomSheetDialogFragment {

    public static StatsDialog newInstance() { return new StatsDialog(); }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView tvTotal   = view.findViewById(R.id.tvStatTotal);
        TextView tvCloud   = view.findViewById(R.id.tvStatCloud);
        TextView tvExpired = view.findViewById(R.id.tvStatExpired);
        TextView tvWeak    = view.findViewById(R.id.tvStatWeak);

        Vault vault = SessionManager.getInstance().getVault();
        if (vault == null) {
            tvTotal.setText("0");
            tvCloud.setText("0");
            tvExpired.setText("0");
            tvWeak.setText("0");
            return;
        }

        List<Credential> credentials = vault.getCredentials();

        android.content.SharedPreferences prefs = requireContext()
                .getSharedPreferences(ExpiryHelper.PREFS_NAME, android.content.Context.MODE_PRIVATE);
        long periodMs = prefs.getLong(ExpiryHelper.PREF_PERIOD, ExpiryHelper.PERIOD_ONE_YEAR);

        int total   = credentials.size();
        int cloud   = 0;
        int expired = 0;
        int weak    = 0;

        for (Credential c : credentials) {
            if (c.isSynced()) cloud++;

            if (ExpiryHelper.getStatus(c.getUpdatedAt(), periodMs) == ExpiryHelper.Status.EXPIRED) {
                expired++;
            }

            if (PasswordStrengthHelper.evaluate(c.getPassword())
                    == PasswordStrengthHelper.Strength.WEAK) {
                weak++;
            }
        }

        tvTotal  .setText(String.valueOf(total));
        tvCloud  .setText(String.valueOf(cloud));
        tvExpired.setText(String.valueOf(expired));
        tvWeak   .setText(String.valueOf(weak));
    }
}
