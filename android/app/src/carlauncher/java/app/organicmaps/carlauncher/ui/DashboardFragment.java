package app.organicmaps.carlauncher.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import app.organicmaps.MwmApplication;
import app.organicmaps.carlauncher.telemetry.TelemetryManager;
import app.organicmaps.carlauncher.widgets.view.FuturisticSpeedometerView;

public class DashboardFragment extends Fragment implements TelemetryManager.TelemetryListener {

    private FuturisticSpeedometerView speedometerView;
    private TelemetryManager telemetryManager;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        telemetryManager = TelemetryManager.getInstance((MwmApplication) requireActivity().getApplication());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Root container (Arka plan yari seffaf veya gradient olabilir)
        FrameLayout root = new FrameLayout(requireContext());
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(0xFF111111); // Dark arka plan

        // Center Content
        LinearLayout contentLayout = new LinearLayout(requireContext());
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setGravity(android.view.Gravity.CENTER);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        root.addView(contentLayout, clp);

        // Speedometer View (Buyuk orta)
        speedometerView = new FuturisticSpeedometerView(requireContext());
        // Custom ayarlamalar yapilabilir.
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(
                dpToPx(350), dpToPx(350) // Kocaman spor kadran
        );
        spLp.bottomMargin = dpToPx(24);
        contentLayout.addView(speedometerView, spLp);

        // Alt Bilgi Paneli (Rakim ve Yon) tamamen kaldirildi (Kullanici istegi)

        // Sag Ust Kapat Butonu (Map'e donus)
        ImageView closeBtn = new ImageView(requireContext());
        closeBtn.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        closeBtn.setColorFilter(0xFFFFFFFF);
        // bg_circle_translucent_white yoksa direkt padding verip background kaldiririz
        closeBtn.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(dpToPx(48), dpToPx(48));
        closeLp.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
        closeLp.topMargin = dpToPx(16);
        closeLp.rightMargin = dpToPx(16);
        closeBtn.setOnClickListener(v -> {
            if (getActivity() instanceof app.organicmaps.activities.MapActivity) {
                ((app.organicmaps.activities.MapActivity) getActivity()).getPanelContentManager()
                        .setContent(PanelContentManager.PanelContent.WIDGETS);
            }
        });
        root.addView(closeBtn, closeLp);

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        telemetryManager.addListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        telemetryManager.removeListener(this);
    }

    @Override
    public void onTelemetryUpdated(TelemetryManager.LocationState loc, TelemetryManager.NavigationState nav, TelemetryManager.ObdState obd) {
        if (speedometerView != null) {
            speedometerView.setSpeed(loc.speedKmh);
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
