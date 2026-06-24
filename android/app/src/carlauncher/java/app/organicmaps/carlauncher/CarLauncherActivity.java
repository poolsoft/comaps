package app.organicmaps.carlauncher;

import android.os.Bundle;
import android.util.Log;
import androidx.annotation.Nullable;
import app.organicmaps.MwmActivity;
import app.organicmaps.R;
import app.organicmaps.carlauncher.telemetry.TelemetryManager;

import android.widget.TextView;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.graphics.Bitmap;
import app.organicmaps.carlauncher.music.MusicManager;

public class CarLauncherActivity extends MwmActivity implements CarLauncherInterface, TelemetryManager.TelemetryListener, MusicManager.MusicUIListener {
    
    private TelemetryManager telemetryManager;
    private MusicManager musicManager;

    private TextView speedValueText;
    private TextView trackTitleText;
    private TextView trackArtistText;
    private ImageView albumArtImage;
    private TextView navDistanceText;

    @Override
    protected void onSafeCreate(@Nullable Bundle savedInstanceState) {
        super.onSafeCreate(savedInstanceState);
        setContentView(R.layout.activity_car_launcher);

        telemetryManager = TelemetryManager.getInstance(this);
        musicManager = MusicManager.getInstance(this);

        speedValueText = findViewById(R.id.speed_value);
        trackTitleText = findViewById(R.id.track_title);
        trackArtistText = findViewById(R.id.track_artist);
        albumArtImage = findViewById(R.id.album_art);
        navDistanceText = findViewById(R.id.nav_distance);

        findViewById(R.id.btn_play_pause).setOnClickListener(v -> musicManager.togglePlayPause());
        findViewById(R.id.btn_next).setOnClickListener(v -> musicManager.skipToNext());
        findViewById(R.id.btn_prev).setOnClickListener(v -> musicManager.skipToPrevious());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (telemetryManager != null) telemetryManager.addListener(this);
        if (musicManager != null) musicManager.addListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (telemetryManager != null) telemetryManager.removeListener(this);
        if (musicManager != null) musicManager.removeListener(this);
    }

    @Override
    public void onTelemetryUpdated(TelemetryManager.LocationState loc, TelemetryManager.NavigationState nav, TelemetryManager.ObdState obd) {
        runOnUiThread(() -> {
            if (speedValueText != null) {
                speedValueText.setText(String.valueOf((int) loc.speedKmh));
            }
            if (navDistanceText != null) {
                navDistanceText.setText(nav.distanceStr != null ? nav.distanceStr : "-- m");
            }
        });
    }

    @Override
    public void onTrackChanged(String title, String artist, Bitmap albumArt, String packageName) {
        runOnUiThread(() -> {
            if (trackTitleText != null) trackTitleText.setText(title);
            if (trackArtistText != null) trackArtistText.setText(artist);
            if (albumArtImage != null) {
                if (albumArt != null) albumArtImage.setImageBitmap(albumArt);
                else albumArtImage.setImageResource(android.R.drawable.ic_media_play);
            }
        });
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        runOnUiThread(() -> {
            ImageButton playBtn = findViewById(R.id.btn_play_pause);
            if (playBtn != null) {
                playBtn.setImageResource(isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
            }
        });
    }

    @Override
    public void onSourceChanged(boolean isInternal) { }

    @Override
    public void openAppDrawer() {}

    @Override
    public void closeAppDrawer() {}

    @Override
    public void openMusicPlayer() {}

    @Override
    public void openWeatherDashboard() {}

    @Override
    public void onLayoutModeToggle() {}

    @Override
    public void onDesktopModeToggle() {}

    @Override
    public void openCarLauncherSettings() {}
}
