package app.organicmaps.carlauncher.backup;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.bookmarks.data.BookmarkManager;
import app.organicmaps.sdk.util.concurrency.ThreadPool;

/**
 * Car Launcher Cevrimdisi USB Yedekleme ve Geri Yukleme Yoneticisi.
 * Haritalar (.mwm), yer imleri / favoriler (bookmarks) ve ayarlari yuksek
 * hizda (256 KB tampon, NO_COMPRESSION) depolar ve arabaya aktarir.
 */
public class LauncherBackupManager {
    private static final String TAG = "LauncherBackupManager";
    private static final String SETTINGS_FILE_NAME = "carlauncher_settings.json";
    private static final String SETTINGS_INI_NAME = "settings.ini";
    private static final int BUFFER_SIZE = 256 * 1024; // 256 KB ultra hizli I/O tamponu

    public interface BackupCallback {
        void onProgress(String message);
        void onSuccess();
        void onError(String error);
    }

    // ==========================================
    // EXPORT (DISA AKTARMA / YEDEK ALMA)
    // ==========================================

    public static void exportToZip(Context context, Uri zipUri, BackupCallback callback) {
        ThreadPool.getStorage().execute(() -> {
            try {
                postProgress(callback, "Ayarlar hazirlaniyor...");
                JSONObject settingsJson = exportSettingsToJson(context);

                postProgress(callback, "Yedek paketi olusturuluyor...");
                try (OutputStream os = context.getContentResolver().openOutputStream(zipUri);
                     ZipOutputStream zos = new ZipOutputStream(os)) {

                    // Harita dosyalari (.mwm) zaten sikistirilmis binary veridir.
                    // NO_COMPRESSION kullanarak CPU kilitlenmesini engelliyoruz ve saniyeler icinde yaziyoruz.
                    zos.setLevel(Deflater.NO_COMPRESSION);

                    // 1. Car Launcher ve Uygulama Tercihleri
                    zos.putNextEntry(new ZipEntry(SETTINGS_FILE_NAME));
                    zos.write(settingsJson.toString(2).getBytes("UTF-8"));
                    zos.closeEntry();

                    // 2. Organic Maps Native Ayarlari (settings.ini)
                    try {
                        String settingsDir = Framework.nativeGetSettingsDir();
                        File settingsIni = new File(settingsDir, SETTINGS_INI_NAME);
                        if (settingsIni.exists() && settingsIni.isFile()) {
                            zos.putNextEntry(new ZipEntry(SETTINGS_INI_NAME));
                            try (FileInputStream fis = new FileInputStream(settingsIni)) {
                                copyStream(fis, zos);
                            }
                            zos.closeEntry();
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "settings.ini yedeklenemedi", e);
                    }

                    // 3. Yer Imleri ve Favoriler (Bookmarks)
                    try {
                        String bookmarkDir = Framework.nativeGetBookmarkDir();
                        File bmkFolder = new File(bookmarkDir);
                        if (bmkFolder.exists() && bmkFolder.isDirectory()) {
                            postProgress(callback, "Yer imleri ve favoriler yedekleniyor...");
                            zipDirectory(bmkFolder, "bookmarks", zos, callback);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Bookmarks yedeklenemedi", e);
                    }

                    // 4. Haritalar (.mwm)
                    postProgress(callback, "Haritalar paketleniyor...");
                    File writableDir = new File(Framework.nativeGetWritableDir());
                    zipDirectory(writableDir, "maps", zos, callback);
                }

                postSuccess(callback);
            } catch (Exception e) {
                Log.e(TAG, "Export to zip failed", e);
                postError(callback, e.getMessage());
            }
        });
    }

    public static void exportToFolder(Context context, Uri treeUri, BackupCallback callback) {
        ThreadPool.getStorage().execute(() -> {
            try {
                DocumentFile rootDir = DocumentFile.fromTreeUri(context, treeUri);
                if (rootDir == null) throw new Exception("Klasor bulunamadi.");

                DocumentFile backupDir = rootDir.createDirectory("CoMaps_Backup_" + System.currentTimeMillis());
                if (backupDir == null) throw new Exception("Yedek klasoru olusturulamadi.");

                postProgress(callback, "Ayarlar kaydediliyor...");
                JSONObject settingsJson = exportSettingsToJson(context);
                DocumentFile settingsFile = backupDir.createFile("application/json", SETTINGS_FILE_NAME);
                if (settingsFile != null) {
                    try (OutputStream os = context.getContentResolver().openOutputStream(settingsFile.getUri())) {
                        if (os != null) os.write(settingsJson.toString(2).getBytes("UTF-8"));
                    }
                }

                // Native settings.ini
                try {
                    String settingsDir = Framework.nativeGetSettingsDir();
                    File settingsIni = new File(settingsDir, SETTINGS_INI_NAME);
                    if (settingsIni.exists() && settingsIni.isFile()) {
                        DocumentFile iniDoc = backupDir.createFile("text/plain", SETTINGS_INI_NAME);
                        if (iniDoc != null) {
                            try (FileInputStream fis = new FileInputStream(settingsIni);
                                 OutputStream os = context.getContentResolver().openOutputStream(iniDoc.getUri())) {
                                if (os != null) copyStream(fis, os);
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "settings.ini klasore aktarilamadi", e);
                }

                // Yer Imleri (Bookmarks)
                try {
                    String bookmarkDir = Framework.nativeGetBookmarkDir();
                    File bmkFolder = new File(bookmarkDir);
                    if (bmkFolder.exists() && bmkFolder.isDirectory()) {
                        postProgress(callback, "Yer imleri kaydediliyor...");
                        DocumentFile bmkDocDir = backupDir.createDirectory("bookmarks");
                        if (bmkDocDir != null) {
                            copyDirectoryToDocumentFile(context, bmkFolder, bmkDocDir, callback);
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Bookmarks klasore aktarilamadi", e);
                }

                // Haritalar
                postProgress(callback, "Haritalar kopyalaniyor...");
                DocumentFile mapsDir = backupDir.createDirectory("maps");
                if (mapsDir != null) {
                    File writableDir = new File(Framework.nativeGetWritableDir());
                    copyDirectoryToDocumentFile(context, writableDir, mapsDir, callback);
                }

                postSuccess(callback);
            } catch (Exception e) {
                Log.e(TAG, "Export to folder failed", e);
                postError(callback, e.getMessage());
            }
        });
    }

    // ==========================================
    // IMPORT (ICE AKTARMA / GERI YUKLEME)
    // ==========================================

    /**
     * Uygulamanin calismak icin bekledigi harita veri surumunu (YYMMDD) dinamik olarak dondurur.
     */
    public static String getRequiredDataVersion(Context context) {
        try {
            app.organicmaps.MwmApplication appInstance = context != null ? app.organicmaps.MwmApplication.from(context) : app.organicmaps.MwmApplication.sInstance;
            if (appInstance != null && appInstance.getOrganicMaps().arePlatformAndCoreInitialized()) {
                java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyMMdd", Locale.US);
                String version = fmt.format(Framework.getDataVersion());
                if (version != null && !version.isEmpty()) {
                    return version;
                }
            }
        } catch (Throwable ignored) {}

        if (context != null) {
            try (InputStream is = context.getAssets().open("countries.txt");
                 java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("\"v\":")) {
                        String vStr = line.replaceAll("[^0-9]", "");
                        if (!vStr.isEmpty()) {
                            return vStr;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        return "260830";
    }

    /**
     * Storage, haritalari yalnizca countries veritabani surumuyle eslesen
     * klasorde arar (orn. files/260830/). Import edilen .mwm dosyalarini o
     * klasore yazariz; kok veya yedekteki farkli surum klasorleri motor
     * tarafindan yok sayilir.
     */
    public static File getMapsTargetDir(Context context) {
        String writablePath = null;
        try {
            app.organicmaps.MwmApplication appInstance = context != null ? app.organicmaps.MwmApplication.from(context) : app.organicmaps.MwmApplication.sInstance;
            if (appInstance != null && appInstance.getOrganicMaps().arePlatformAndCoreInitialized()) {
                writablePath = Framework.nativeGetWritableDir();
            }
        } catch (Throwable ignored) {}
        if (writablePath == null || writablePath.isEmpty()) {
            File ext = context != null ? context.getExternalFilesDir(null) : null;
            writablePath = ext != null ? ext.getAbsolutePath() : (context != null ? context.getFilesDir().getAbsolutePath() : "");
        }
        File writableDir = new File(writablePath);

        String version = getRequiredDataVersion(context);
        File targetDir = (version != null && !version.isEmpty()) ? new File(writableDir, version) : writableDir;
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        return targetDir;
    }

    /**
     * USB bellekten veya dosya seciciden secilen serbest .mwm haritalarini aktarir.
     */
    public static void importMapFiles(Context context, List<Uri> mapUris, BackupCallback callback) {
        ThreadPool.getStorage().execute(() -> {
            try {
                if (mapUris == null || mapUris.isEmpty()) {
                    throw new Exception("Harita dosyasi secilmedi.");
                }
                File targetDir = getMapsTargetDir(context);
                if (!targetDir.exists() && !targetDir.mkdirs()) {
                    throw new Exception("Harita hedef dizini olusturulamadi.");
                }

                int totalCount = mapUris.size();
                int imported = 0;
                for (int i = 0; i < totalCount; i++) {
                    Uri uri = mapUris.get(i);
                    DocumentFile document = DocumentFile.fromSingleUri(context, uri);
                    String name = document != null ? document.getName() : null;
                    if (name == null || !name.toLowerCase(Locale.US).endsWith(".mwm")) {
                        continue;
                    }
                    postProgress(callback, "Harita aktariliyor (" + (i + 1) + "/" + totalCount + "): " + name);
                    File target = new File(targetDir, name);
                    File partial = new File(targetDir, name + ".importing");
                    try (InputStream input = context.getContentResolver().openInputStream(uri);
                         FileOutputStream output = new FileOutputStream(partial)) {
                        if (input == null) throw new Exception("Dosya okunamadi: " + name);
                        copyStream(input, output);
                    }
                    if (target.exists() && !target.delete()) {
                        partial.delete();
                        throw new Exception("Eski harita silinemedi: " + name);
                    }
                    if (!partial.renameTo(target)) {
                        partial.delete();
                        throw new Exception("Harita kaydedilemedi: " + name);
                    }
                    imported++;
                }

                if (imported == 0) {
                    throw new Exception("Gecerli .mwm harita dosyasi bulunamadi.");
                }

                int finalCount = imported;
                postProgress(callback, finalCount + " harita basariyla yuklendi, motor yenileniyor...");
                reloadEngines(callback);
            } catch (Exception e) {
                Log.e(TAG, "Raw map import failed", e);
                postError(callback, e.getMessage());
            }
        });
    }

    /**
     * ZIP yedek dosyasindan harita, yer imleri ve ayarlari geri yukler.
     */
    public static void importFromZip(Context context, Uri zipUri, BackupCallback callback) {
        ThreadPool.getStorage().execute(() -> {
            try {
                postProgress(callback, "ZIP dosyasi taraniyor...");
                File targetMapDir = getMapsTargetDir(context);
                File bookmarkDir = new File(Framework.nativeGetBookmarkDir());
                if (!bookmarkDir.exists()) bookmarkDir.mkdirs();
                File settingsDir = new File(Framework.nativeGetSettingsDir());
                if (!settingsDir.exists()) settingsDir.mkdirs();

                try (InputStream is = context.getContentResolver().openInputStream(zipUri);
                     ZipInputStream zis = new ZipInputStream(is)) {

                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        String entryName = entry.getName();
                        if (entry.isDirectory()) {
                            zis.closeEntry();
                            continue;
                        }

                        if (entryName.equals(SETTINGS_FILE_NAME)) {
                            postProgress(callback, "Uygulama ayarlari geri yukleniyor...");
                            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                            copyStream(zis, bos);
                            String jsonStr = bos.toString("UTF-8");
                            importSettingsFromJson(context, new JSONObject(jsonStr));
                        } else if (entryName.equals(SETTINGS_INI_NAME)) {
                            postProgress(callback, "Harita ayarlari geri yukleniyor...");
                            File targetIni = new File(settingsDir, SETTINGS_INI_NAME);
                            try (FileOutputStream fos = new FileOutputStream(targetIni)) {
                                copyStream(zis, fos);
                            }
                        } else if (entryName.startsWith("bookmarks/")) {
                            String fileName = new File(entryName).getName();
                            if (!fileName.isEmpty()) {
                                postProgress(callback, "Yer imi aktariliyor: " + fileName);
                                File targetBmk = new File(bookmarkDir, fileName);
                                try (FileOutputStream fos = new FileOutputStream(targetBmk)) {
                                    copyStream(zis, fos);
                                }
                            }
                        } else if (entryName.startsWith("maps/") || entryName.toLowerCase(Locale.US).endsWith(".mwm")) {
                            String fileName = new File(entryName).getName();
                            if (fileName.toLowerCase(Locale.US).endsWith(".mwm")) {
                                postProgress(callback, "Harita aktariliyor: " + fileName);
                                File targetFile = new File(targetMapDir, fileName);
                                File partial = new File(targetMapDir, fileName + ".importing");
                                try (FileOutputStream fos = new FileOutputStream(partial)) {
                                    copyStream(zis, fos);
                                }
                                if (targetFile.exists()) targetFile.delete();
                                partial.renameTo(targetFile);
                            }
                        }
                        zis.closeEntry();
                    }
                }

                postProgress(callback, "Veriler yenileniyor...");
                reloadEngines(callback);
            } catch (Exception e) {
                Log.e(TAG, "Import from zip failed", e);
                postError(callback, e.getMessage());
            }
        });
    }

    /**
     * Secilen klasorden (USB bellek kok veya alt klasor) harita, yer imleri ve ayarlari geri yukler.
     * Klasorde 'maps' alt klasoru olmasa bile klasordeki ve alt klasorlerdeki tum .mwm dosyalarini otomatik bulur.
     */
    public static void importFromFolder(Context context, Uri treeUri, BackupCallback callback) {
        ThreadPool.getStorage().execute(() -> {
            try {
                DocumentFile backupDir = DocumentFile.fromTreeUri(context, treeUri);
                if (backupDir == null) throw new Exception("Yedek klasoru okunamadi.");

                postProgress(callback, "Klasor inceleniyor...");

                // 1. Ayarlar
                DocumentFile settingsFile = backupDir.findFile(SETTINGS_FILE_NAME);
                if (settingsFile != null) {
                    postProgress(callback, "Ayarlar geri yukleniyor...");
                    try (InputStream is = context.getContentResolver().openInputStream(settingsFile.getUri())) {
                        if (is != null) {
                            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                            copyStream(is, bos);
                            String jsonStr = bos.toString("UTF-8");
                            importSettingsFromJson(context, new JSONObject(jsonStr));
                        }
                    }
                }

                // 2. settings.ini
                DocumentFile iniFile = backupDir.findFile(SETTINGS_INI_NAME);
                if (iniFile != null) {
                    try {
                        File settingsDir = new File(Framework.nativeGetSettingsDir());
                        if (!settingsDir.exists()) settingsDir.mkdirs();
                        File targetIni = new File(settingsDir, SETTINGS_INI_NAME);
                        try (InputStream is = context.getContentResolver().openInputStream(iniFile.getUri());
                             FileOutputStream fos = new FileOutputStream(targetIni)) {
                            if (is != null) copyStream(is, fos);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "settings.ini yuklenemedi", e);
                    }
                }

                // 3. Yer Imleri (Bookmarks)
                DocumentFile bmkDir = backupDir.findFile("bookmarks");
                if (bmkDir != null && bmkDir.isDirectory()) {
                    postProgress(callback, "Yer imleri geri yukleniyor...");
                    File targetBmkDir = new File(Framework.nativeGetBookmarkDir());
                    if (!targetBmkDir.exists()) targetBmkDir.mkdirs();
                    copyDocumentFileToDirectory(context, bmkDir, targetBmkDir, callback);
                }

                // 4. Haritalar:
                // Once "maps" alt klasorune bakar, yoksa secilen klasordeki tum .mwm dosyalarini tarar.
                DocumentFile mapsDir = backupDir.findFile("maps");
                File targetMapDir = getMapsTargetDir(context);
                if (!targetMapDir.exists()) targetMapDir.mkdirs();

                postProgress(callback, "Haritalar taranip aktariliyor...");
                if (mapsDir != null && mapsDir.isDirectory()) {
                    copyDocumentFileToDirectory(context, mapsDir, targetMapDir, callback);
                } else {
                    // maps alt klasoru yoksa, klasor icindeki tum .mwm dosyalarini rekursif topla ve kopyala
                    scanAndCopyMwmFiles(context, backupDir, targetMapDir, callback);
                }

                postProgress(callback, "Veriler yenileniyor...");
                reloadEngines(callback);
            } catch (Exception e) {
                Log.e(TAG, "Import from folder failed", e);
                postError(callback, e.getMessage());
            }
        });
    }

    // ==========================================
    // YARDIMCI VE TAMAMLAYICI METOTLAR
    // ==========================================

    private static void reloadEngines(BackupCallback callback) {
        new Handler(Looper.getMainLooper()).post(() -> {
            boolean coreReady = false;
            try {
                if (app.organicmaps.MwmApplication.sInstance != null) {
                    coreReady = app.organicmaps.MwmApplication.sInstance.getOrganicMaps().arePlatformAndCoreInitialized();
                }
            } catch (Throwable ignored) {}

            if (coreReady) {
                try {
                    // Haritalari aninda bellekte yenile
                    Framework.nativeReloadWorldMaps();
                } catch (Throwable t) {
                    Log.w(TAG, "nativeReloadWorldMaps hatasi", t);
                }
                try {
                    // Yer imlerini aninda bellekte yenile
                    BookmarkManager.loadBookmarks();
                } catch (Throwable t) {
                    Log.w(TAG, "BookmarkManager.loadBookmarks hatasi", t);
                }
            }
            if (callback != null) callback.onSuccess();
        });
    }

    private static JSONObject exportSettingsToJson(Context context) throws Exception {
        JSONObject root = new JSONObject();

        // 1. Car Launcher spesifik ayarlari
        SharedPreferences carPrefs = context.getSharedPreferences("car_launcher_prefs", Context.MODE_PRIVATE);
        JSONObject carJson = new JSONObject();
        for (Map.Entry<String, ?> entry : carPrefs.getAll().entrySet()) {
            carJson.put(entry.getKey(), entry.getValue());
        }
        root.put("car_launcher_prefs", carJson);

        // 2. Varsayilan tercihler
        SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        JSONObject defaultJson = new JSONObject();
        for (Map.Entry<String, ?> entry : defaultPrefs.getAll().entrySet()) {
            defaultJson.put(entry.getKey(), entry.getValue());
        }
        root.put("default_prefs", defaultJson);

        return root;
    }

    private static void importSettingsFromJson(Context context, JSONObject json) throws Exception {
        if (json.has("car_launcher_prefs")) {
            JSONObject carJson = json.getJSONObject("car_launcher_prefs");
            applyJsonToPrefs(context.getSharedPreferences("car_launcher_prefs", Context.MODE_PRIVATE), carJson);
        }
        if (json.has("default_prefs")) {
            JSONObject defJson = json.getJSONObject("default_prefs");
            applyJsonToPrefs(PreferenceManager.getDefaultSharedPreferences(context), defJson);
        } else {
            // Eski tek katmanli json destegi
            applyJsonToPrefs(PreferenceManager.getDefaultSharedPreferences(context), json);
        }
    }

    private static void applyJsonToPrefs(SharedPreferences prefs, JSONObject json) throws Exception {
        SharedPreferences.Editor editor = prefs.edit();
        java.util.Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key.equals("car_launcher_prefs") || key.equals("default_prefs")) continue;
            Object value = json.get(key);
            if (value instanceof Boolean) {
                editor.putBoolean(key, (Boolean) value);
            } else if (value instanceof Integer) {
                editor.putInt(key, (Integer) value);
            } else if (value instanceof Float) {
                editor.putFloat(key, (Float) value);
            } else if (value instanceof Double) {
                editor.putFloat(key, ((Double) value).floatValue());
            } else if (value instanceof Long) {
                editor.putLong(key, (Long) value);
            } else if (value instanceof String) {
                editor.putString(key, (String) value);
            }
        }
        editor.apply();
    }

    private static void zipDirectory(File dir, String basePath, ZipOutputStream zos, BackupCallback callback) throws Exception {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            String name = file.getName();
            if (name.equals("cache") || name.equals("log") || name.endsWith(".tmp") || name.endsWith(".importing")) {
                continue;
            }

            String entryPath = basePath + "/" + name;
            if (file.isDirectory()) {
                zipDirectory(file, entryPath, zos, callback);
            } else {
                postProgress(callback, "Paketleniyor: " + name);
                zos.putNextEntry(new ZipEntry(entryPath));
                try (FileInputStream fis = new FileInputStream(file)) {
                    copyStream(fis, zos);
                }
                zos.closeEntry();
            }
        }
    }

    private static void copyDirectoryToDocumentFile(Context context, File srcDir, DocumentFile destDir, BackupCallback callback) throws Exception {
        File[] files = srcDir.listFiles();
        if (files == null) return;

        for (File file : files) {
            String name = file.getName();
            if (name.equals("cache") || name.equals("log") || name.endsWith(".tmp") || name.endsWith(".importing")) {
                continue;
            }

            if (file.isDirectory()) {
                DocumentFile newDir = destDir.findFile(name);
                if (newDir == null) newDir = destDir.createDirectory(name);
                if (newDir != null) {
                    copyDirectoryToDocumentFile(context, file, newDir, callback);
                }
            } else {
                postProgress(callback, "Kopyalaniyor: " + name);
                DocumentFile newFile = destDir.findFile(name);
                if (newFile != null) newFile.delete();
                newFile = destDir.createFile("application/octet-stream", name);
                if (newFile != null) {
                    try (FileInputStream fis = new FileInputStream(file);
                         OutputStream os = context.getContentResolver().openOutputStream(newFile.getUri())) {
                        if (os != null) copyStream(fis, os);
                    }
                }
            }
        }
    }

    private static void copyDocumentFileToDirectory(Context context, DocumentFile srcDir, File destDir, BackupCallback callback) throws Exception {
        DocumentFile[] files = srcDir.listFiles();
        if (!destDir.exists()) destDir.mkdirs();

        for (DocumentFile file : files) {
            String name = file.getName() != null ? file.getName() : "unknown";
            if (file.isDirectory()) {
                // Klasor yapisini koru veya alt klasordeki dosyalari tara
                File subDir = new File(destDir, name);
                copyDocumentFileToDirectory(context, file, subDir, callback);
            } else {
                postProgress(callback, "Aktariliyor: " + name);
                File newFile = new File(destDir, name);
                File partial = new File(destDir, name + ".importing");
                try (InputStream is = context.getContentResolver().openInputStream(file.getUri());
                     FileOutputStream fos = new FileOutputStream(partial)) {
                    if (is != null) copyStream(is, fos);
                }
                if (newFile.exists()) newFile.delete();
                partial.renameTo(newFile);
            }
        }
    }

    private static void scanAndCopyMwmFiles(Context context, DocumentFile dir, File destDir, BackupCallback callback) throws Exception {
        DocumentFile[] files = dir.listFiles();
        for (DocumentFile file : files) {
            if (file.isDirectory()) {
                scanAndCopyMwmFiles(context, file, destDir, callback);
            } else {
                String name = file.getName();
                if (name != null && name.toLowerCase(Locale.US).endsWith(".mwm")) {
                    postProgress(callback, "Harita aktariliyor: " + name);
                    File newFile = new File(destDir, name);
                    File partial = new File(destDir, name + ".importing");
                    try (InputStream is = context.getContentResolver().openInputStream(file.getUri());
                         FileOutputStream fos = new FileOutputStream(partial)) {
                        if (is != null) copyStream(is, fos);
                    }
                    if (newFile.exists()) newFile.delete();
                    partial.renameTo(newFile);
                }
            }
        }
    }

    private static void copyStream(InputStream is, OutputStream os) throws Exception {
        byte[] buffer = new byte[BUFFER_SIZE];
        int length;
        while ((length = is.read(buffer)) > 0) {
            os.write(buffer, 0, length);
        }
    }

    private static void postProgress(BackupCallback callback, String msg) {
        if (callback != null) {
            new Handler(Looper.getMainLooper()).post(() -> callback.onProgress(msg));
        }
    }

    private static void postSuccess(BackupCallback callback) {
        if (callback != null) {
            new Handler(Looper.getMainLooper()).post(callback::onSuccess);
        }
    }

    private static void postError(BackupCallback callback, String error) {
        if (callback != null) {
            new Handler(Looper.getMainLooper()).post(() -> callback.onError(error));
        }
    }
}
