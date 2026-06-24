package app.organicmaps.carlauncher.ui;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import app.organicmaps.R;

/**
 * Duvar Kagidi secimi icin premium diyalog arayuzu.
 * Kod icerisinde kesinlikle Turkce karakter kullanilmamistir.
 */
public class WallpaperChooserDialog extends DialogFragment {

    public interface OnWallpaperSelectedListener {
        void onWallpaperSelected(String style);
        void onPickCustomWallpaper();
        void onSetSystemWallpaper();
        void onOpenSystemWallpaperChooser();
    }

    private OnWallpaperSelectedListener listener;

    public void setOnWallpaperSelectedListener(OnWallpaperSelectedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_wallpaper_chooser, container, false);

        // Modern Gradient
        view.findViewById(R.id.btn_wallpaper_modern).setOnClickListener(v -> {
            if (listener != null) listener.onWallpaperSelected("modern");
            dismiss();
        });

        // Carbon Black
        view.findViewById(R.id.btn_wallpaper_carbon).setOnClickListener(v -> {
            if (listener != null) listener.onWallpaperSelected("carbon");
            dismiss();
        });

        // Deep Space
        view.findViewById(R.id.btn_wallpaper_space).setOnClickListener(v -> {
            if (listener != null) listener.onWallpaperSelected("space");
            dismiss();
        });

        // Current System Wallpaper
        view.findViewById(R.id.btn_wallpaper_system).setOnClickListener(v -> {
            if (listener != null) listener.onSetSystemWallpaper();
            dismiss();
        });

        // Set New System Wallpaper
        view.findViewById(R.id.btn_wallpaper_system_chooser).setOnClickListener(v -> {
            if (listener != null) listener.onOpenSystemWallpaperChooser();
            dismiss();
        });

        // Custom Gallery Image
        view.findViewById(R.id.btn_wallpaper_custom).setOnClickListener(v -> {
            if (listener != null) listener.onPickCustomWallpaper();
            dismiss();
        });

        // Cancel Button
        view.findViewById(R.id.btn_close).setOnClickListener(v -> dismiss());

        return view;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        Window window = dialog.getWindow();
        if (window != null) {
            window.requestFeature(Window.FEATURE_NO_TITLE);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        return dialog;
    }
}
