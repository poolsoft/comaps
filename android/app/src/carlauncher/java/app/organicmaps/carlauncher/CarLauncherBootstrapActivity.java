package app.organicmaps.carlauncher;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import app.organicmaps.BuildConfig;
import app.organicmaps.CarLauncherDownloadResourcesActivity;
import app.organicmaps.MwmApplication;
import app.organicmaps.R;
import app.organicmaps.downloader.DownloaderActivity;
import app.organicmaps.intent.Factory;
import app.organicmaps.sdk.location.LocationHelper;
import app.organicmaps.sdk.util.Config;
import app.organicmaps.sdk.util.LocationUtils;
import app.organicmaps.sdk.util.concurrency.UiThread;
import app.organicmaps.sdk.util.log.Logger;
import app.organicmaps.util.SharingUtils;
import app.organicmaps.util.Utils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.IOException;
import java.util.Objects;
import app.organicmaps.carlauncher.ui.AppDockFragment;
import app.organicmaps.carlauncher.ui.CarLayoutManager;
import app.organicmaps.carlauncher.ui.PanelContentManager;

/**
 * Car Launcher'a ait izole baslangic etkinligi.
 * CoMaps core ve ilk harita kaynaklarini hazirladiktan sonra hedefi her zaman
 * CarLauncherActivity olarak tutar; ana CoMaps SplashActivity'sini degistirmez.
 */
public class CarLauncherBootstrapActivity extends AppCompatActivity
    implements CarLauncherInterface, AppDockFragment.OnAppDockListener
{
  private static final String TAG = CarLauncherBootstrapActivity.class.getSimpleName();

  private static final long DELAY = 100;

  private boolean mCanceled = false;
  private boolean mShellFirstFrameScheduled;
  private boolean mCoreInitRequested;
  private CarLayoutManager mLayoutManager;
  private PanelContentManager mPanelContentManager;
  private int mLayoutMode;

  @SuppressWarnings("NotNullFieldNotInitialized")
  @NonNull
  private ActivityResultLauncher<Intent> mApiRequest;
  @SuppressWarnings("NotNullFieldNotInitialized")
  @NonNull
  private ActivityResultLauncher<String[]> mPermissionRequest;
  @NonNull
  private ActivityResultLauncher<SharingUtils.SharingIntent> mShareLauncher;

  @NonNull
  private final Runnable mInitCoreDelayedTask = this::init;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState)
  {
    CarCrashLogger.recordStartupStage("Bootstrap.onCreate.beforeSuper");
    super.onCreate(savedInstanceState);
    CarCrashLogger.recordStartupStage("Bootstrap.onCreate.afterSuper");

    Intent intent = getIntent();
    Log.i("CarLauncherLifecycle", "Bootstrap.onCreate called. Action: "
        + (intent != null ? intent.getAction() : "null") 
        + ", isTaskRoot: " + isTaskRoot() + ", TaskId: " + getTaskId() 
        + ", Flags: " + (intent != null ? intent.getFlags() : 0));

    UiThread.cancelDelayedTasks(mInitCoreDelayedTask);
    setContentView(R.layout.activity_car_launcher);

    mLayoutManager = new CarLayoutManager(this);
    mPanelContentManager = new PanelContentManager(getSupportFragmentManager(), R.id.widget_panel);
    mPanelContentManager.setOnFullScreenStateChangeListener(fullScreen -> {
      mLayoutManager.setContentFullScreen(fullScreen);
      mLayoutManager.applyLayout(true, mLayoutMode);
    });
    mLayoutManager.applyLayout(true, mLayoutMode);
    mPanelContentManager.setContent(PanelContentManager.PanelContent.WIDGETS);

    FrameLayout mapContainer = findViewById(R.id.car_map_container);
    if (mapContainer != null)
    {
      ProgressBar progress = new ProgressBar(this);
      FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
          FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
      params.gravity = android.view.Gravity.CENTER;
      mapContainer.addView(progress, params);
    }

    ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root_layout), new OnApplyWindowInsetsListener() {
      @NonNull
      @Override
      public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets)
      {
        Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
        v.setPadding(0, 0, 0, systemBars.bottom);
        return insets;
      }
    });
    mPermissionRequest = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                                                   result -> Config.setLocationRequested());
    mApiRequest = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
      setResult(result.getResultCode(), result.getData());
      finish();
    });
    mShareLauncher = SharingUtils.RegisterLauncher(this);

    // NOT: src/main/ surumundeki isCarDisplayUsed() yonlendirmesi burada YOKTUR.
    // CarLauncher dogrudan head unit uzerinde calisir.
    // isCarDisplayUsed() true olsa bile MapPlaceholderActivity'ye gitme, devam et.
  }

  @Override
  protected void onResume()
  {
    super.onResume();
    CarCrashLogger.recordStartupStage("Bootstrap.onResume");
    Log.i("CarLauncherLifecycle", "Bootstrap.onResume called. mCanceled=" + mCanceled);
    if (mCanceled)
      return;
    if (!Config.isLocationRequested() && !LocationUtils.checkLocationPermission(this))
    {
      Logger.d(TAG, "Requesting location permissions");
      mPermissionRequest.launch(new String[] {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION});
      return;
    }

    scheduleCoreAfterShellFrame();
  }

  private void scheduleCoreAfterShellFrame()
  {
    if (mShellFirstFrameScheduled)
    {
      UiThread.runLater(mInitCoreDelayedTask, DELAY);
      return;
    }
    mShellFirstFrameScheduled = true;
    View root = findViewById(R.id.root_layout);
    if (root == null)
    {
      UiThread.runLater(mInitCoreDelayedTask, DELAY);
      return;
    }
    root.post(() -> android.view.Choreographer.getInstance().postFrameCallback(frameTimeNanos -> {
      if (isFinishing() || isDestroyed() || mCanceled)
        return;
      CarCrashLogger.recordStartupStage("Bootstrap.shell.firstFrame");
      getSupportFragmentManager().beginTransaction()
          .replace(R.id.app_dock, new AppDockFragment(), "bootstrap_dock")
          .commitAllowingStateLoss();
      UiThread.runLater(mInitCoreDelayedTask, DELAY);
    }));
  }

  @Override
  protected void onPause()
  {
    super.onPause();
    Log.i("CarLauncherLifecycle", "Bootstrap.onPause called.");
    UiThread.cancelDelayedTasks(mInitCoreDelayedTask);
  }

  @Override
  protected void onDestroy()
  {
    super.onDestroy();
    Log.i("CarLauncherLifecycle", "Bootstrap.onDestroy called.");
    mPermissionRequest.unregister();
    mPermissionRequest = null;
    mApiRequest.unregister();
    mApiRequest = null;
  }

  private void showFatalErrorDialog(@StringRes int titleId, @StringRes int messageId, Exception error)
  {
    mCanceled = true;
    new MaterialAlertDialogBuilder(this, R.style.MwmTheme_M3_AlertDialog)
        .setTitle(titleId)
        .setMessage(messageId)
        .setPositiveButton(
            R.string.report_a_bug,
            (dialog, which) -> Utils.sendBugReport(mShareLauncher, this, "Fatal Error", Log.getStackTraceString(error)))
        .setCancelable(false)
        .show();
  }

  private void init()
  {
    if (mCoreInitRequested)
      return;
    mCoreInitRequested = true;
    CarCrashLogger.recordStartupStage("Bootstrap.initCore.begin");
    MwmApplication app = MwmApplication.from(this);
    boolean asyncContinue = false;
    try
    {
      asyncContinue = app.initOrganicMaps(this::processNavigation);
      CarCrashLogger.recordStartupStage("Bootstrap.initCore.returned async=" + asyncContinue);
    }
    catch (IOException error)
    {
      CarCrashLogger.recordStartupStage("Bootstrap.initCore.IOException " + error);
      showFatalErrorDialog(R.string.dialog_error_storage_title, R.string.dialog_error_storage_message, error);
      return;
    }

    if (Config.isFirstLaunch(this) && LocationUtils.checkLocationPermission(this))
    {
      final LocationHelper locationHelper = app.getLocationHelper();
      locationHelper.onEnteredIntoFirstRun();
      if (!locationHelper.isActive())
        locationHelper.start();
    }

    if (!asyncContinue)
      processNavigation();
  }

  // Called from MwmApplication::nativeInitFramework like callback.
  @Keep
  @SuppressWarnings({"unused", "unchecked"})
  public void processNavigation()
  {
    CarCrashLogger.recordStartupStage("Bootstrap.processNavigation");
    Log.i("CarLauncherLifecycle", "Bootstrap.processNavigation called.");
    
    if (isDestroyed())
    {
      Logger.w(TAG, "Ignore late callback from core because activity is already destroyed");
      return;
    }

    final Intent sourceIntent = Objects.requireNonNull(getIntent());
    final Intent intent = new Intent(sourceIntent);

    if (isManageSpaceActivity(intent))
    {
      intent.setComponent(new ComponentName(this, DownloaderActivity.class));
    }
    else
    {
      boolean hasResources = true;
      try
      {
        hasResources = app.organicmaps.sdk.DownloadResourcesLegacyActivity.nativeGetBytesToDownload() == 0;
      }
      catch (Exception ignored) {}

      if (hasResources)
      {
        CarCrashLogger.recordStartupStage("Bootstrap.target.CarLauncherActivity");
        intent.setComponent(new ComponentName(this, CarLauncherActivity.class));
      }
      else
      {
        CarCrashLogger.recordStartupStage("Bootstrap.target.CarLauncherDownloadResourcesActivity");
        CarCrashLogger.recordMemory("resources_missing");
        intent.setComponent(new ComponentName(this, CarLauncherDownloadResourcesActivity.class));
      }
    }

    // FLAG_ACTIVITY_NEW_TASK and FLAG_ACTIVITY_RESET_TASK_IF_NEEDED break the cold start.
    // https://github.com/organicmaps/organicmaps/pull/7287
    // FORWARD_RESULT_FLAG conflicts with the ActivityResultLauncher.
    // https://github.com/organicmaps/organicmaps/issues/8984
    intent.setFlags(intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);

    if (Factory.isStartedForApiResult(intent))
    {
      // Wait for the result from MwmActivity for API callers.
      mApiRequest.launch(intent);
      return;
    }

    Config.setFirstStartDialogSeen(this);
    startActivity(intent);
    finish();
  }

  private boolean isManageSpaceActivity(Intent intent)
  {
    var component = intent.getComponent();

    if (!Intent.ACTION_VIEW.equals(intent.getAction()))
      return false;
    if (component == null)
      return false;

    var manageSpaceActivityName = BuildConfig.APPLICATION_ID + ".ManageSpaceActivity";

    return manageSpaceActivityName.equals(component.getClassName());
  }

  @Override public void openAppDrawer() { setPanelContent(PanelContentManager.PanelContent.APP_DRAWER); }
  @Override public void closeAppDrawer() { setPanelContent(PanelContentManager.PanelContent.WIDGETS); }
  @Override public void openCarLauncherSettings() { setPanelContent(PanelContentManager.PanelContent.SETTINGS); }
  @Override public void openMusicPlayer() { setPanelContent(PanelContentManager.PanelContent.MUSIC); }
  @Override public void openWeatherDashboard() { setPanelContent(PanelContentManager.PanelContent.WEATHER); }
  @Override public void openAntennaAlignmentInPanel() { }
  @Override public void openAntennaAlignmentFullscreen() { }
  @Override public void setPanelContent(PanelContentManager.PanelContent content)
  {
    if (mPanelContentManager != null)
      mPanelContentManager.setContent(content);
  }
  @Override public Object getMapView() { return null; }
  @Override public PanelContentManager getPanelContentManager() { return mPanelContentManager; }
  @Override public void onLayoutModeToggle()
  {
    mLayoutMode = (mLayoutMode + 1) % 2;
    if (mLayoutManager != null)
      mLayoutManager.applyLayout(true, mLayoutMode);
  }
  @Override public void onDesktopModeToggle() { }
  @Override public boolean isDesktopMode() { return false; }
  @Override public int getLayoutMode() { return mLayoutMode; }
  @Override public boolean isWidgetPanelOpen() { return true; }
  @Override public void applyNightDimMode() { }
  @Override public void applyStatusBarVisibility() { }
  @Override public void checkAndRefreshDockFragmentIfNeeded() { }
  @Override public void onAppDrawerOpen() { openAppDrawer(); }
}
