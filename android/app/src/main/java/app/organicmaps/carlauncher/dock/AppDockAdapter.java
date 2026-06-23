package app.organicmaps.carlauncher.dock;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.organicmaps.carlauncher.CarLauncherSettings;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * App Dock RecyclerView adapter.
 */
public class AppDockAdapter extends RecyclerView.Adapter<AppDockAdapter.ViewHolder> {

    private final Context context;
    private final List<AppShortcut> shortcuts;
    private final OnShortcutListener listener;
    private boolean isEditMode = false;
    private boolean isVerticalMode = false;

    public interface OnShortcutListener {
        void onShortcutClick(AppShortcut shortcut);

        void onShortcutLongClick(AppShortcut shortcut);

        void onRemoveClick(AppShortcut shortcut);
    }

    public AppDockAdapter(@NonNull Context context, @NonNull OnShortcutListener listener) {
        this.context = context;
        this.shortcuts = new ArrayList<>();
        this.listener = listener;
    }

    public void setShortcuts(List<AppShortcut> shortcuts) {
        this.shortcuts.clear();
        this.shortcuts.addAll(shortcuts);
        notifyDataSetChanged();
    }

    public void setEditMode(boolean editMode) {
        this.isEditMode = editMode;
        notifyDataSetChanged();
    }

    public boolean isEditMode() {
        return isEditMode;
    }

    public void setVerticalMode(boolean verticalMode) {
        if (this.isVerticalMode == verticalMode) return;
        this.isVerticalMode = verticalMode;
        notifyDataSetChanged();
    }

    public void onItemMove(int fromPosition, int toPosition) {
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                java.util.Collections.swap(shortcuts, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                java.util.Collections.swap(shortcuts, i, i - 1);
            }
        }
        notifyItemMoved(fromPosition, toPosition);
    }

    /**
     * DockSize (0-100) ayarina gore olceklendirilmis ikon boyutu.
     * 0=min(0.3x), 50=normal(1.0x), 100=max(1.7x)
     */
    private int getScaledIconSize() {
        int baseSize = (int) context.getResources().getDimension(app.organicmaps.R.dimen.dock_icon_size);
        CarLauncherSettings settings = new CarLauncherSettings(context);
        int dockSizePercent = settings.getDockSize();
        float scale = 0.3f + (dockSizePercent / 100.0f) * 1.4f;
        return (int) (baseSize * scale);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int iconSize = getScaledIconSize();
        int itemSize = iconSize + dpToPx(16); // Sabit dokunma alani
        int itemWidth = itemSize;
        int itemHeight = itemSize;

        LinearLayout itemView = new LinearLayout(context);
        itemView.setOrientation(LinearLayout.VERTICAL);
        itemView.setLayoutParams(new RecyclerView.LayoutParams(itemWidth, itemHeight));
        itemView.setGravity(android.view.Gravity.CENTER);
        int padding = isVerticalMode ? dpToPx(6) : dpToPx(8);
        itemView.setPadding(padding, padding, padding, padding);
        itemView.setBackgroundResource(app.organicmaps.R.drawable.bg_dock_item_ripple);

        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        int iconSize = getScaledIconSize();
        int itemSize = iconSize + dpToPx(16); // Sabit dokunma alani
        int itemWidth = itemSize;
        int itemHeight = itemSize;
        
        ViewGroup.LayoutParams lp = holder.itemView.getLayoutParams();
        if (lp != null) {
            lp.width = itemWidth;
            lp.height = itemHeight;
            holder.itemView.setLayoutParams(lp);
        }

        AppShortcut shortcut = shortcuts.get(position);
        holder.bind(shortcut);
    }

    @Override
    public int getItemCount() {
        return shortcuts.size();
    }

    private int dpToPx(int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private final ImageView iconView;
        private final ImageView removeButton;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            LinearLayout container = (LinearLayout) itemView;

            // Remove button (edit mode'da gorunur)
            removeButton = new ImageView(context);
            removeButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            removeButton.setLayoutParams(new LinearLayout.LayoutParams(
                    dpToPx(20), dpToPx(20))); // Reduced size
            removeButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
            removeButton.setColorFilter(0xFFFF0000);
            removeButton.setVisibility(View.GONE);
            container.addView(removeButton);

            // Icon — boyut dockSize ayarina gore olceklendirilir
            int iconSize = AppDockAdapter.this.getScaledIconSize();
            iconView = new ImageView(context);
            iconView.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
            iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            container.addView(iconView);

            // itemView'ın kendi ripple'ı var, animate kodunu dokunma hissiyatı (basılma) olarak koruyabiliriz ama onClickListener çalışmasını bozmaması için dönüş değerini false tutuyoruz.
            itemView.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100).start();
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                        break;
                }
                return false; 
            });
        }

        public void bind(AppShortcut shortcut) {
            // Dinamik olarak guncel ikon boyutunu ata (ViewHolder yeniden kullanildiginda boyutun guncellenmesi icin)
            int iconSize = getScaledIconSize();
            ViewGroup.LayoutParams lp = iconView.getLayoutParams();
            if (lp != null) {
                lp.width = iconSize;
                lp.height = iconSize;
                iconView.setLayoutParams(lp);
            } else {
                iconView.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
            }

            iconView.setImageDrawable(shortcut.getIcon());

            // Edit mode kontrolu
            removeButton.setVisibility(isEditMode ? View.VISIBLE : View.GONE);

            // Click listener
            itemView.setOnClickListener(v -> {
                if (isEditMode) {
                    // Edit mode'da click ignore
                } else {
                    if (listener != null) {
                        listener.onShortcutClick(shortcut);
                    }
                }
            });

            // Long click listener
            itemView.setOnLongClickListener(v -> {
                if (!isEditMode && listener != null) {
                    listener.onShortcutLongClick(shortcut);
                }
                return true;
            });

            // Remove button click
            removeButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onRemoveClick(shortcut);
                }
            });
        }

        private void launchApp(AppShortcut shortcut) {
            try {
                // 1. Check for internal Overlay Manager first
                if (shortcut.getLaunchMode() == LaunchMode.OVERLAY) {
                    // Check if we have an OverlayWindowManager in context (MwmActivity)
                    // Or send broadcast to open overlay
                    if (context instanceof app.organicmaps.carlauncher.CarLauncherInterface) {
                        // TODO: Make interface support openOverlay
                    }
                    // Fallback: Just open standard for now, but user expects Overlay
                    // Use the OverlayWindowManager we saw earlier? We don't have reference here.
                    // But wait, user said "overlay" mode.
                    // Let's launch with PiP flag (auto-enter-pip) if supported OR use standard
                    // intent
                    Intent intent = context.getPackageManager()
                            .getLaunchIntentForPackage(shortcut.getPackageName());
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        // Try to force into freeform/pip via options if OS supports
                        android.app.ActivityOptions options = android.app.ActivityOptions.makeBasic();
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                            // Attempt to launch in stack (Display ID or Stack ID)
                            // For now, let's just assume simple launch if custom overlay not ready
                            // But actually, maybe I should use the OverlayWindowManager logic?
                            // Since I can't easily embed another app, I will use Multi-Window flag.
                            intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
                        }
                        context.startActivity(intent, options.toBundle());
                    }
                    return;
                }

                // Normal & Split-Screen
                Intent intent = context.getPackageManager()
                        .getLaunchIntentForPackage(shortcut.getPackageName());
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    if (shortcut.getLaunchMode() == LaunchMode.SPLIT_SCREEN) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
                            intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                        }
                    }

                    context.startActivity(intent);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
