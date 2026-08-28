package app.organicmaps.carlauncher.music;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import androidx.annotation.Nullable;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Asynchronously decodes MediaStore artwork without blocking the launcher UI thread. */
public final class LocalAlbumArtLoader {
    public interface Callback { void onLoaded(@Nullable Bitmap bitmap); }

    private static volatile LocalAlbumArtLoader instance;
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> memoryCache = new LruCache<String, Bitmap>(4 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            return Math.max(1, bitmap.getByteCount() / 1024);
        }
    };

    private LocalAlbumArtLoader(Context context) {
        this.context = context.getApplicationContext();
    }

    public static LocalAlbumArtLoader getInstance(Context context) {
        if (instance == null) {
            synchronized (LocalAlbumArtLoader.class) {
                if (instance == null) instance = new LocalAlbumArtLoader(context);
            }
        }
        return instance;
    }

    public void load(@Nullable Uri uri, Callback callback) {
        if (uri == null || Uri.EMPTY.equals(uri)) {
            callback.onLoaded(null);
            return;
        }
        String key = uri.toString();
        Bitmap cached = memoryCache.get(key);
        if (cached != null && !cached.isRecycled()) {
            callback.onLoaded(cached);
            return;
        }
        executor.execute(() -> {
            Bitmap bitmap = decodeSampled(uri, 512);
            if (bitmap != null) memoryCache.put(key, bitmap);
            mainHandler.post(() -> callback.onLoaded(bitmap));
        });
    }

    @Nullable
    private Bitmap decodeSampled(Uri uri, int maxSize) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(input, null, bounds);
            }
            int sample = 1;
            while (bounds.outWidth / sample > maxSize || bounds.outHeight / sample > maxSize) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(input, null, options);
            }
        } catch (Exception ignored) {
            return null;
        }
    }
}
