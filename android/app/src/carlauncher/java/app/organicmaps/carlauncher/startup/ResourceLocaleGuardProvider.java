package app.organicmaps.carlauncher.startup;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * CoMaps builds default drawable names during Application.onCreate() with the
 * process default locale. In Turkish, "ISLAM".toLowerCase() becomes "ıslam",
 * which is not a valid/generated Android resource name. Providers are created
 * before Application.onCreate(), so keep resource-name normalization locale
 * neutral for that narrow startup window and restore the user's locale on the
 * first main-loop turn.
 */
public final class ResourceLocaleGuardProvider extends ContentProvider
{
  @Override
  public boolean onCreate()
  {
    Locale originalLocale = Locale.getDefault();
    Locale.setDefault(Locale.ROOT);
    new Handler(Looper.getMainLooper()).post(() -> Locale.setDefault(originalLocale));
    return true;
  }

  @Nullable
  @Override
  public Cursor query(@NonNull Uri uri, @Nullable String[] projection,
                      @Nullable String selection, @Nullable String[] selectionArgs,
                      @Nullable String sortOrder)
  {
    return null;
  }

  @Nullable
  @Override
  public String getType(@NonNull Uri uri)
  {
    return null;
  }

  @Nullable
  @Override
  public Uri insert(@NonNull Uri uri, @Nullable ContentValues values)
  {
    return null;
  }

  @Override
  public int delete(@NonNull Uri uri, @Nullable String selection,
                    @Nullable String[] selectionArgs)
  {
    return 0;
  }

  @Override
  public int update(@NonNull Uri uri, @Nullable ContentValues values,
                    @Nullable String selection, @Nullable String[] selectionArgs)
  {
    return 0;
  }
}
