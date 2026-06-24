package app.organicmaps.carlauncher;

import android.os.Bundle;
import androidx.annotation.Nullable;
import app.organicmaps.MwmActivity;
import app.organicmaps.R;

public class CarLauncherActivity extends MwmActivity implements CarLauncherInterface {
    
    @Override
    protected void onSafeCreate(@Nullable Bundle savedInstanceState) {
        super.onSafeCreate(savedInstanceState);
        
        // Base layout'u ekle
        setContentView(R.layout.activity_car_launcher);
    }

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
