package com.example.clef.ui.settings;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Configuration;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.clef.R;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

public class HelpSupportSheet {

    public static void show(Context context) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_help_sheet, null);
        dialog.setContentView(view);

        dialog.setOnShowListener(d -> {
            FrameLayout bs = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bs == null) return;
            BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bs);
            if (context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            } else {
                behavior.setPeekHeight(BottomSheetBehavior.PEEK_HEIGHT_AUTO);
            }
        });

        setupFaqItem(view, R.id.rowFaq1, R.id.tvFaq1Answer, R.id.ivFaq1Chevron);
        setupFaqItem(view, R.id.rowFaq2, R.id.tvFaq2Answer, R.id.ivFaq2Chevron);
        setupFaqItem(view, R.id.rowFaq3, R.id.tvFaq3Answer, R.id.ivFaq3Chevron);
        setupFaqItem(view, R.id.rowFaq4, R.id.tvFaq4Answer, R.id.ivFaq4Chevron);
        setupFaqItem(view, R.id.rowFaq5, R.id.tvFaq5Answer, R.id.ivFaq5Chevron);

        MaterialButton btnCopy = view.findViewById(R.id.btnCopyEmail);
        btnCopy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("email",
                        context.getString(R.string.help_contact_email)));
                Toast.makeText(context,
                        context.getString(R.string.help_contact_copied),
                        Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private static void setupFaqItem(View root, int rowId, int answerId, int chevronId) {
        View row         = root.findViewById(rowId);
        TextView answer  = root.findViewById(answerId);
        ImageView chevron = root.findViewById(chevronId);

        chevron.setRotation(90f);

        row.setOnClickListener(v -> {
            boolean expanded = answer.getVisibility() == View.VISIBLE;
            if (expanded) {
                answer.setVisibility(View.GONE);
                chevron.animate().rotation(90f).setDuration(200).start();
            } else {
                answer.setVisibility(View.VISIBLE);
                chevron.animate().rotation(270f).setDuration(200).start();
            }
        });
    }
}
