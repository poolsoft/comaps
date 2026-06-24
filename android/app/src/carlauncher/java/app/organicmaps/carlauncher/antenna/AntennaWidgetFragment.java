package app.organicmaps.carlauncher.antenna;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import app.organicmaps.MwmApplication;
import app.organicmaps.R;
import app.organicmaps.carlauncher.widgets.AntennaWidget;

public class AntennaWidgetFragment extends Fragment {

    private AntennaWidget antennaWidget;
    private FrameLayout widgetContainer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_antenna_widget_panel, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        widgetContainer = view.findViewById(R.id.antenna_widget_container);
        
        // Kapatma butonu aksiyonu (Turkce karakter yok)
        view.findViewById(R.id.btn_close_panel).setOnClickListener(v -> {
            if (getActivity() instanceof app.organicmaps.activities.MapActivity) {
                ((app.organicmaps.activities.MapActivity) getActivity()).setPanelContent(
                        app.organicmaps.carlauncher.ui.PanelContentManager.PanelContent.WIDGETS
                );
            }
        });

        // Mevcut AntennaWidget'i olustur ve container'a ekle (Turkce karakter yok)
        if (getContext() != null) {
            MwmApplication app = (MwmApplication) getContext().getApplicationContext();
            antennaWidget = new AntennaWidget(getContext(), app);
            
            View widgetView = antennaWidget.createView();
            if (widgetView != null) {
                widgetContainer.removeAllViews();
                widgetContainer.addView(widgetView);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (antennaWidget != null) {
            antennaWidget.onStart();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (antennaWidget != null) {
            antennaWidget.onStop();
        }
    }
}
