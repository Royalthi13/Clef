package com.example.clef.ui.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.clef.R;

public class GeneratorFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // De momento, inflamos un layout vacío o el mismo de settings para no dar error
        // Luego crearemos su propio diseño visual (fragment_generator.xml)
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }
}
