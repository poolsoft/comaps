package app.organicmaps.carlauncher.backup;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.util.concurrency.ThreadPool;

public class LauncherBackupManager {
    private static final String TAG = "LauncherBackupManager";
    private static final String SETTINGS_FILE_NAME = "carlauncher_settings.json";

    public interface BackupCallback {
        void onProgress(String message);
        void onSuccess();
        void onError(String error);
    }

    // ==========================================
    // EXPORT
    // ==========================================

    public static void exportToZip(Context context, Uri zipUri, BackupCallback callback) {
        ThreadPool.getStorage().execute(() -> {
            try {
                postProgress(callback, "Ayarlar kaydediliyor...");
                JSONObject settings = exportSettingsToJson(context);
                
                postProgress(callback, "Haritalar zipleniyor (Bu islem uzun surebilir)...");
                try (OutputStream os = context.getContentResolver().openOutputStream(zipUri);
                     ZipOutputStream zos = new ZipOutputStream(os)) {
                    
                    // Write settings
                    zos.putNextEntry(new ZipEntry(SETTINGS_FILE_NAME));
                    zos.write(settings.toString(2).getBytes());
                    zos.closeEntry();
                    
                    // Write Maps and Data
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
                JSONObject settings = exportSettingsToJson(context);
                DocumentFile settingsFile = backupDir.createFile("application/json", SETTINGS_FILE_NAME);
                try (OutputStream os = context.getContentResolver().openOutputStream(settingsFile.getUri())) {
                    os.write(settings.toString(2).getBytes());
                }

                postProgress(callback, "Haritalar kopyalaniyor (Bu islem uzun surebilir)...");
                DocumentFile mapsDir = backupDir.createDirectory("maps");
                File writableDir = new File(Framework.nativeGetWritableDir());
                copyDirectoryToDocumentFile(context, writableDir, mapsDir, callback);

                postSuccess(callback);
            } catch (Exception e) {
                Log.e(TAG, "Export to folder failed", e);
                postError(callback, e.getMessage());
            }
        });
    }

    // ==========================================
    // IMPORT
    // ==========================================

    /** Imports raw CoMaps map files selected from USB or a document provider. */
    public static void importMapFiles(Context context, List<Uri> mapUris,
                                      BackupCallback callback) {
        ThreadPool.getStorage().execute(() -> {
            try {
                if (mapUris == null || mapUris.isEmpty()) {
                    throw new Exception("Harita dosyasi secilmedi.");
                }
                File writableDir = new File(Framework.nativeGetWritableDir());
                if (!writableDir.exists() && !writableDir.mkdirs()) {
                    throw new Exception("Harita dizini olusturulamadi.");
                }
                int imported = 0;
                for (Uri uri : mapUris) {
                    DocumentFile document = DocumentFile.fromSingleUri(context, uri);
                    String name = document != null ? document.getName() : null;
                    if (name == null || !name.toLowerCase(java.util.Locale.US).contains(".mwm")) {
                        continue;
                    }
                    postProgress(callback, "Harita aktariliyor: " + name);
                    File target = new File(writableDir, name);
                    File partial = new File(writableDir, name + ".importing");
                    try (InputStream input = context.getContentResolver().openInputStream(uri);
                         FileOutputStream output = new FileOutputStream(partial)) {
                        if (input == null) throw new Exception("Dosya okunamadi: " + name);
                        copyStream(input, output);
                    }
                    if (target.exists() && !target.delete()) {
                        partial.delete();
                        throw new Exception("Eski harita degistirilemedi: " + name);
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
                int importedCount = imported;
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (app.organicmaps.sdk.DownloadResourcesLegacyActivity
                            .nativeGetBytesToDownload() == 0) {
                        Framework.nativeReloadWorldMaps();
                    }
                    postProgress(callback, importedCount + " harita kaydedildi.");
                    if (callback != null) callback.onSuccess();
                });
            } catch (Exception e) {
                Log.e(TAG, "Raw map import failed", e);
                postError(callback, e.getMessage());
            }
        });
    }

    public static void importFromZip(Context context, Uri zipUri, BackupCallback callback) {
        ThreadPool.getStorage().execute(() -> {
            try {
                postProgress(callback, "Zipten okunuyor (Bu islem uzun surebilir)...");
                File writableDir = new File(Framework.nativeGetWritableDir());
                
                try (InputStream is = context.getContentResolver().openInputStream(zipUri);
                     ZipInputStream zis = new ZipInputStream(is)) {
                    
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        if (entry.getName().equals(SETTINGS_FILE_NAME)) {
                            postProgress(callback, "Ayarlar geri yukleniyor...");
                            byte[] buffer = new byte[(int) entry.getSize()];
                            int read = 0;
                            while (read < buffer.length) {
                                int result = zis.read(buffer, read, buffer.length - read);
                                if (result == -1) break;
                                read += result;
                            }
                            String jsonStr = new String(buffer);
                            importSettingsFromJson(context, new JSONObject(jsonStr));
                        } else if (entry.getName().startsWith("maps/")) {
                            String relPath = entry.getName().substring(5); // remove "maps/"
                            if (relPath.isEmpty()) continue;
                            File targetFile = new File(writableDir, relPath);
                            if (entry.isDirectory()) {
                                targetFile.mkdirs();
                            } else {
                                targetFile.getParentFile().mkdirs();
                                try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                                    copyStream(zis, fos);
                                }
                            }
                        }
                        zis.closeEntry();
                    }
                }
                
                postMapImportSuccess(callback);
            } catch (Exception e) {
                Log.e(TAG, "Import from zip failed", e);
                postError(callback, e.getMessage());
            }
        });
    }

    public static void importFromFolder(Context context, Uri treeUri, BackupCallback callback) {
        ThreadPool.getStorage().execute(() -> {
            try {
                DocumentFile backupDir = DocumentFile.fromTreeUri(context, treeUri);
                if (backupDir == null) throw new Exception("Yedek klasoru okunamadi.");

                DocumentFile settingsFile = backupDir.findFile(SETTINGS_FILE_NAME);
                if (settingsFile != null) {
                    postProgress(callback, "Ayarlar geri yukleniyor...");
                    try (InputStream is = context.getContentResolver().openInputStream(settingsFile.getUri())) {
                        byte[] buffer = new byte[is.available()];
                        is.read(buffer);
                        String jsonStr = new String(buffer);
                        importSettingsFromJson(context, new JSONObject(jsonStr));
                    }
                }

                DocumentFile mapsDir = backupDir.findFile("maps");
                if (mapsDir != null) {
                    postProgress(callback, "Haritalar geri yukleniyor (Bu islem uzun surebilir)...");
                    File writableDir = new File(Framework.nativeGetWritableDir());
                    copyDocumentFileToDirectory(context, mapsDir, writableDir, callback);
                }

                postMapImportSuccess(callback);
            } catch (Exception e) {
                Log.e(TAG, "Import from folder failed", e);
                postError(callback, e.getMessage());
            }
        });
    }

    // ==========================================
    // HELPERS
    // ==========================================

    private static JSONObject exportSettingsToJson(Context context) throws Exception {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Map<String, ?> allEntries = prefs.getAll();
        JSONObject json = new JSONObject();
        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            json.put(entry.getKey(), entry.getValue());
        }
        return json;
    }

    private static void importSettingsFromJson(Context context, JSONObject json) throws Exception {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        
        java.util.Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = json.get(key);
            
            if (value instanceof Boolean) {
                editor.putBoolean(key, (Boolean) value);
            } else if (value instanceof Integer) {
                editor.putInt(key, (Integer) value);
            } else if (value instanceof Float) {
                editor.putFloat(key, (Float) value);
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
            // Ignore temporary files or cached data if needed
            if (file.getName().equals("cache") || file.getName().equals("log")) continue;
            
            String entryPath = basePath + "/" + file.getName();
            if (file.isDirectory()) {
                zipDirectory(file, entryPath, zos, callback);
            } else {
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
            if (file.getName().equals("cache") || file.getName().equals("log")) continue;
            
            if (file.isDirectory()) {
                DocumentFile newDir = destDir.createDirectory(file.getName());
                copyDirectoryToDocumentFile(context, file, newDir, callback);
            } else {
                String mime = "application/octet-stream";
                DocumentFile newFile = destDir.createFile(mime, file.getName());
                try (FileInputStream fis = new FileInputStream(file);
                     OutputStream os = context.getContentResolver().openOutputStream(newFile.getUri())) {
                    copyStream(fis, os);
                }
            }
        }
    }

    private static void copyDocumentFileToDirectory(Context context, DocumentFile srcDir, File destDir, BackupCallback callback) throws Exception {
        DocumentFile[] files = srcDir.listFiles();
        if (!destDir.exists()) destDir.mkdirs();
        
        for (DocumentFile file : files) {
            if (file.isDirectory()) {
                File newDir = new File(destDir, file.getName());
                copyDocumentFileToDirectory(context, file, newDir, callback);
            } else {
                File newFile = new File(destDir, file.getName());
                try (InputStream is = context.getContentResolver().openInputStream(file.getUri());
                     FileOutputStream fos = new FileOutputStream(newFile)) {
                    copyStream(is, fos);
                }
            }
        }
    }

    private static void copyStream(InputStream is, OutputStream os) throws Exception {
        byte[] buffer = new byte[8192];
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

    private static void postMapImportSuccess(BackupCallback callback) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (app.organicmaps.sdk.DownloadResourcesLegacyActivity
                    .nativeGetBytesToDownload() == 0) {
                Framework.nativeReloadWorldMaps();
            }
            if (callback != null) callback.onSuccess();
        });
    }

    private static void postError(BackupCallback callback, String error) {
        if (callback != null) {
            new Handler(Looper.getMainLooper()).post(() -> callback.onError(error));
        }
    }
}
