package com.example.comaps.aar;

import javax.naming.Context;
import javax.swing.text.View;

import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import app.organicmaps.sdk.display.DisplayType;

public class ExampleMapFragment extends Fragment implements SurfaceHolder.Callback {
    @NonNull
    private final app.organicmaps.sdk.Map mMap = new app.organicmaps.sdk.Map(DisplayType.Device);

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (DemoApplication.organicMapsInstance != null) {
            mMap.setLocationHelper(DemoApplication.organicMapsInstance.getLocationHelper());
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        SurfaceView sv = new SurfaceView(requireContext());
        sv.getHolder().addCallback(this);
        return sv;
    }

    @Override
    public void onStart() {
        super.onStart();
        mMap.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        mMap.onStop();
    }

    @Override
    public void onPause() {
        super.onPause();
        mMap.onPause(requireContext());
    }

    @Override
    public void onResume() {
        super.onResume();
        mMap.onResume();
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        final DisplayMetrics dm = requireContext().getResources().getDisplayMetrics();
        mMap.onSurfaceCreated(requireContext(), holder.getSurface(), holder.getSurfaceFrame(), dm.densityDpi);
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        mMap.onSurfaceChanged(requireContext(), holder.getSurface(), holder.getSurfaceFrame(), holder.isCreating());
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        mMap.onSurfaceDestroyed(requireActivity().isChangingConfigurations(), true);
    }
}
