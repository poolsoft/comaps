package app.organicmaps.carlauncher.music;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Creates and compares stable identifiers for local music tracks.
 *
 * <p>MediaStore items are identified by their content URI. Files discovered by
 * direct storage scanning use their volume and relative path. The legacy
 * absolute path is retained only as a compatibility fallback.</p>
 */
public final class MusicTrackIdentity {

    private static final String MEDIA_STORE_PREFIX = "mediastore:";
    private static final String FILE_PREFIX = "file:";

    private MusicTrackIdentity() {
    }

    @NonNull
    public static String create(long id, @Nullable Uri contentUri,
            @Nullable String volumeId, @Nullable String relativePath, @Nullable String path) {
        if (contentUri != null && "content".equalsIgnoreCase(contentUri.getScheme())) {
            return MEDIA_STORE_PREFIX + contentUri;
        }
        String normalizedVolume = normalize(volumeId);
        String normalizedRelativePath = normalize(relativePath);
        if (!normalizedRelativePath.isEmpty()) {
            return FILE_PREFIX + normalizedVolume + ":" + normalizedRelativePath;
        }
        if (path != null && !path.trim().isEmpty()) {
            return FILE_PREFIX + normalize(path);
        }
        return FILE_PREFIX + normalizedVolume + ":" + id;
    }

    public static boolean matches(@Nullable MusicRepository.AudioTrack first,
            @Nullable MusicRepository.AudioTrack second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        if (first.getMediaId().equals(second.getMediaId())) {
            return true;
        }
        return first.getPath() != null && first.getPath().equals(second.getPath());
    }

    public static boolean matchesReference(@Nullable String reference,
            @Nullable MusicRepository.AudioTrack track) {
        if (reference == null || track == null) {
            return false;
        }
        if (reference.equals(track.getMediaId()) || reference.equals(track.getPath())) {
            return true;
        }
        // A removable volume may be mounted under another port after reboot.
        String savedRelativePath = reference.startsWith(FILE_PREFIX)
                ? extractFileRelativePath(reference)
                : MusicRepository.extractRelativePath(reference);
        return !savedRelativePath.isEmpty()
                && savedRelativePath.equalsIgnoreCase(track.getRelativePath());
    }

    @NonNull
    private static String extractFileRelativePath(@NonNull String mediaId) {
        int volumeSeparator = mediaId.indexOf(':', FILE_PREFIX.length());
        if (volumeSeparator < 0 || volumeSeparator + 1 >= mediaId.length()) {
            return "";
        }
        return mediaId.substring(volumeSeparator + 1);
    }

    @NonNull
    private static String normalize(@Nullable String value) {
        return value == null ? "" : value.replace('\\', '/').trim().toLowerCase(Locale.ROOT);
    }
}
