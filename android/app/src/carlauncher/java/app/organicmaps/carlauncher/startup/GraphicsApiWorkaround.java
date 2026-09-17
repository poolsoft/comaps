package app.organicmaps.carlauncher.startup;

import android.os.Build;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import app.organicmaps.sdk.util.log.Logger;

/**
 * Low-end MediaTek 8227L car head units (AC8227L SoC, Mali-450 MP GPU) advertise
 * Vulkan 1.0 support, but their drivers hang or crash during Drape engine
 * creation. The native default graphics API is Vulkan, so force the stable
 * OpenGLES path for this hardware by seeding settings.ini before the core
 * reads it (nativeSetSettingsDir happens in OrganicMaps' constructor).
 *
 * Runs from ResourceLocaleGuardProvider, i.e. before Application.onCreate(),
 * which is early enough for every core entry point in this app.
 */
public final class GraphicsApiWorkaround
{
  private static final String TAG = GraphicsApiWorkaround.class.getSimpleName();
  private static final String KEY = "PreferredGraphicsAPI";
  private static final String VALUE = "OpenGLES3";

  private GraphicsApiWorkaround() {}

  public static void applyIfNeeded(@NonNull android.content.Context context)
  {
    try
    {
      if (!isAffectedDevice())
        return;

      File settingsFile = new File(context.getFilesDir(), "settings.ini");
      if (containsPreferredGraphicsApi(settingsFile))
        return;

      appendEntry(settingsFile);
      Logger.i(TAG, "Seeded " + KEY + "=" + VALUE + " for affected head unit (hw="
          + Build.HARDWARE + ", device=" + Build.DEVICE + ")");
    }
    catch (Exception e)
    {
      // Never block startup because of this workaround.
      Logger.e(TAG, "Failed to seed preferred graphics API", e);
    }
  }

  private static boolean isAffectedDevice()
  {
    return "ac8227l".equalsIgnoreCase(Build.HARDWARE)
        || "8227l_demo".equalsIgnoreCase(Build.DEVICE)
        || "mt8227".equalsIgnoreCase(Build.HARDWARE);
  }

  private static boolean containsPreferredGraphicsApi(@NonNull File settingsFile) throws IOException
  {
    if (!settingsFile.exists())
      return false;
    List<String> lines = Files.readAllLines(settingsFile.toPath(), StandardCharsets.UTF_8);
    for (String line : lines)
    {
      if (line.startsWith(KEY + "="))
        return true;
    }
    return false;
  }

  private static void appendEntry(@NonNull File settingsFile) throws IOException
  {
    // Match the native key=value format written by StringStorageBase::Save().
    String entry = KEY + "=" + VALUE + "\n";
    Files.write(settingsFile.toPath(), entry.getBytes(StandardCharsets.UTF_8),
        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
  }
}
