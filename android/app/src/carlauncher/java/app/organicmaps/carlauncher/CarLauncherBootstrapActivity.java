package app.organicmaps.carlauncher;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
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

/**
 * Car Launcher'a ait izole baslangic etkinligi.
 * CoMaps core ve ilk harita kaynaklarini hazirladiktan sonra hedefi her zaman
 * CarLauncherActivity olarak tutar; ana CoMaps SplashActivity'sini degistirmez.
 */
public class CarLauncherBootstrapActivity extends AppCompatActivity
{
  private static final String TAG = CarLauncherBootstrapActivity.class.getSimpleName();

  private static final long DELAY = 100;

  private boolean mCanceled = false;

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
    super.onCreate(savedInstanceState);

    Intent intent = getIntent();
    Log.i("CarLauncherLifecycle", "Bootstrap.onCreate called. Action: "
        + (intent != null ? intent.getAction() : "null") 
        + ", isTaskRoot: " + isTaskRoot() + ", TaskId: " + getTaskId() 
        + ", Flags: " + (intent != null ? intent.getFlags() : 0));

    UiThread.cancelDelayedTasks(mInitCoreDelayedTask);
    setContentView(R.layout.activity_splash);

    ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root_view), new OnApplyWindowInsetsListener() {
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
    Log.i("CarLauncherLifecycle", "Bootstrap.onResume called. mCanceled=" + mCanceled);
    if (mCanceled)
      return;
    if (!Config.isLocationRequested() && !LocationUtils.checkLocationPermission(this))
    {
      Logger.d(TAG, "Requesting location permissions");
      mPermissionRequest.launch(new String[] {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION});
      return;
    }

    UiThread.runLater(mInitCoreDelayedTask, DELAY);
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
    MwmApplication app = MwmApplication.from(this);
    boolean asyncContinue = false;
    try
    {
      asyncContinue = app.initOrganicMaps(this::processNavigation);
    }
    catch (IOException error)
    {
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
        intent.setComponent(new ComponentName(this, CarLauncherActivity.class));
      }
      else
      {
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
}
