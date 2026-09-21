package app.organicmaps.carlauncher.startup;

import android.os.Build;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import app.organicmaps.sdk.util.log.Logger;

/**
 * Low-end MediaTek 8227L car head units (AC8227L SoC, Mali-450 MP GPU) have limited
 * OpenGL ES 2.0-class hardware without Vulkan support. To prevent driver segfaults
 * and freeze-crashes during map rendering, this workaround configures safe
 * rendering keys in settings.ini before the C++ core initializes:
 * - PreferredGraphicsAPI=OpenGLES3
 * - VulkanForbidden=true
 * - Allow3d=false
 * - Buildings3d=false
 * - TrafficSimplifiedColors=true
 * - Antialiasing=false
 * - AutoZoom=false
 *
 * Runs from ResourceLocaleGuardProvider, i.e. before Application.onCreate().
 */
public final class GraphicsApiWorkaround
{
  private static final String TAG = GraphicsApiWorkaround.class.getSimpleName();

  private static final Map<String, String> SAFE_SETTINGS = new LinkedHashMap<>();
  static
  {
    SAFE_SETTINGS.put("PreferredGraphicsAPI", "OpenGLES3");
    SAFE_SETTINGS.put("VulkanForbidden", "true");
    SAFE_SETTINGS.put("Allow3d", "false");
    SAFE_SETTINGS.put("Buildings3d", "false");
    SAFE_SETTINGS.put("TrafficSimplifiedColors", "true");
    SAFE_SETTINGS.put("Antialiasing", "false");
    SAFE_SETTINGS.put("AutoZoom", "false");
  }

  private GraphicsApiWorkaround() {}

  public static void applyIfNeeded(@NonNull android.content.Context context)
  {
    try
    {
      if (!isAffectedDevice())
        return;

      File settingsFile = new File(context.getFilesDir(), "settings.ini");
      boolean changed = updateSettingsFile(settingsFile);
      if (changed)
      {
        Logger.i(TAG, "Applied safe low-end graphics configuration to settings.ini for device="
            + Build.DEVICE + ", model=" + Build.MODEL + ", hw=" + Build.HARDWARE);
      }
    }
    catch (Exception e)
    {
      // Never block startup because of this workaround.
      Logger.e(TAG, "Failed to seed preferred graphics settings", e);
    }
  }

  public static boolean isAffectedDevice()
  {
    String hw = Build.HARDWARE != null ? Build.HARDWARE.toLowerCase(Locale.ROOT) : "";
    String dev = Build.DEVICE != null ? Build.DEVICE.toLowerCase(Locale.ROOT) : "";
    String board = Build.BOARD != null ? Build.BOARD.toLowerCase(Locale.ROOT) : "";
    String model = Build.MODEL != null ? Build.MODEL.toLowerCase(Locale.ROOT) : "";
    String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER.toLowerCase(Locale.ROOT) : "";

    return hw.contains("8227") || dev.contains("8227") || board.contains("8227")
        || model.contains("8227") || hw.contains("ac8227") || dev.contains("8227l_demo")
        || manufacturer.contains("alps") || dev.contains("xyauto")
        || model.contains("l9211b");
  }

  private static boolean updateSettingsFile(@NonNull File settingsFile) throws IOException
  {
    Map<String, String> pending = new LinkedHashMap<>(SAFE_SETTINGS);
    List<String> outputLines = new ArrayList<>();
    boolean modified = false;

    if (settingsFile.exists())
    {
      List<String> currentLines = Files.readAllLines(settingsFile.toPath(), StandardCharsets.UTF_8);
      for (String line : currentLines)
      {
        int eqIndex = line.indexOf('=');
        if (eqIndex > 0)
        {
          String key = line.substring(0, eqIndex).trim();
          if (pending.containsKey(key))
          {
            String desiredValue = pending.remove(key);
            String currentValue = line.substring(eqIndex + 1).trim();
            if (!desiredValue.equals(currentValue))
            {
              outputLines.add(key + "=" + desiredValue);
              modified = true;
              continue;
            }
          }
        }
        outputLines.add(line);
      }
    }

    // Append any keys that were not found in the file.
    for (Map.Entry<String, String> entry : pending.entrySet())
    {
      outputLines.add(entry.getKey() + "=" + entry.getValue());
      modified = true;
    }

    if (modified || !settingsFile.exists())
    {
      File parent = settingsFile.getParentFile();
      if (parent != null && !parent.exists())
        parent.mkdirs();

      Files.write(settingsFile.toPath(), outputLines, StandardCharsets.UTF_8);
      return true;
    }

    return false;
  }
}
