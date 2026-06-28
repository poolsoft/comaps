package app.organicmaps.carlauncher.ui;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import app.organicmaps.R;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;

/**
 * CoMaps Auto (Car Launcher) guncelleme ve APK indirme/yukleme yardimci sinifi.
 * DownloadManager yerine dogrudan HttpURLConnection ile bagimsiz indirme yapilir.
 * Ekranda anlik ilerleme yuzdesi gosteren ProgressDialog entegre edilmistir.
 * Kod icerisinde kesinlikle Turkce karakter kullanilmamistir.
 */
public class UpdaterHelper {

    // Latest Release altindaki version.json'a ulasacagiz.
    private static final String VERSION_JSON_URL = "https://github.com/poolsoft/comaps/releases/latest/download/version.json";

    // Indirme durumunu takip eden ve mukerrer tiklamalari onleyen bayrak
    private static boolean isDownloading = false;

    public static void checkUpdates(Context context, boolean showToastIfLatest) {
        if (isDownloading) {
            Toast.makeText(context, context.getString(R.string.car_update_downloading_in_progress), Toast.LENGTH_LONG).show();
            return;
        }
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                URL url = new URL(VERSION_JSON_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setInstanceFollowRedirects(true);
                
                int status = conn.getResponseCode();
                int redirectCount = 0;
                while ((status == HttpURLConnection.HTTP_MOVED_TEMP || 
                        status == HttpURLConnection.HTTP_MOVED_PERM || 
                        status == HttpURLConnection.HTTP_SEE_OTHER ||
                        status == 307 || status == 308) && redirectCount < 5) {
                    String newUrl = conn.getHeaderField("Location");
                    if (newUrl == null) break;
                    if (newUrl.startsWith("/")) {
                        newUrl = url.getProtocol() + "://" + url.getHost() + newUrl;
                    }
                    url = new URL(newUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);
                    conn.setInstanceFollowRedirects(true);
                    status = conn.getResponseCode();
                    redirectCount++;
                }

                if (status != HttpURLConnection.HTTP_OK) {
                    throw new Exception("HTTP Hata Kodu: " + status);
                }
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JSONObject json = new JSONObject(response.toString());
                int latestVersionCode = json.getInt("versionCode");
                String latestVersionName = json.getString("versionName");
                String apkUrl = json.getString("apkUrl");

                PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                long currentVersionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? pInfo.getLongVersionCode() : pInfo.versionCode;

                new Handler(Looper.getMainLooper()).post(() -> {
                    if (latestVersionCode > currentVersionCode) {
                        showUpdateDialog(context, latestVersionName, apkUrl);
                    } else if (showToastIfLatest) {
                        Toast.makeText(context, context.getString(R.string.car_update_app_up_to_date, pInfo.versionName), Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (showToastIfLatest) {
                        Toast.makeText(context, context.getString(R.string.car_update_check_failed), Toast.LENGTH_SHORT).show();
                    }
                    android.util.Log.e("Updater", "Check update error", e);
                });
            }
        });
    }

    private static void showUpdateDialog(Context context, String versionName, String apkUrl) {
        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.car_update_new_version_available))
                .setMessage(context.getString(R.string.car_update_dialog_message, versionName))
                .setPositiveButton(context.getString(R.string.car_update_download_install), (dialog, which) -> downloadAndInstallApk(context, apkUrl, versionName))
                .setNegativeButton(context.getString(R.string.car_update_later), null)
                .show();
    }

    private static void downloadAndInstallApk(Context context, String url, String versionName) {
        if (isDownloading) {
            Toast.makeText(context, context.getString(R.string.car_update_downloading_in_progress), Toast.LENGTH_LONG).show();
            return;
        }
        isDownloading = true;

        String fileName = "CoMapsAuto_v" + versionName + ".apk";

        // Indirme baslamadan once eski indirilmis APK varsa siliyoruz
        try {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File oldApk = new File(downloadsDir, fileName);
            if (oldApk.exists()) {
                oldApk.delete();
            }
        } catch (Exception e) {
            android.util.Log.e("Updater", "Eski APK silinirken hata olustu", e);
        }

        ProgressDialog progressDialog = new ProgressDialog(context);
        progressDialog.setTitle(context.getString(R.string.car_update_title));
        progressDialog.setMessage(context.getString(R.string.car_update_downloading_msg));
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(false);
        progressDialog.setMax(100);
        progressDialog.setProgress(0);
        progressDialog.show();

        Executors.newSingleThreadExecutor().execute(() -> {
            File tempApk = null;
            try {
                URL downloadUrl = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) downloadUrl.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setInstanceFollowRedirects(true);

                int status = conn.getResponseCode();
                int redirectCount = 0;
                while ((status == HttpURLConnection.HTTP_MOVED_TEMP || 
                        status == HttpURLConnection.HTTP_MOVED_PERM || 
                        status == HttpURLConnection.HTTP_SEE_OTHER ||
                        status == 307 || status == 308) && redirectCount < 5) {
                    String newUrl = conn.getHeaderField("Location");
                    if (newUrl == null) break;
                    if (newUrl.startsWith("/")) {
                        newUrl = downloadUrl.getProtocol() + "://" + downloadUrl.getHost() + newUrl;
                    }
                    downloadUrl = new URL(newUrl);
                    conn = (downloadUrl.getProtocol().equalsIgnoreCase("https")) ?
                        (HttpURLConnection) downloadUrl.openConnection() :
                        (HttpURLConnection) downloadUrl.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);
                    conn.setInstanceFollowRedirects(true);
                    status = conn.getResponseCode();
                    redirectCount++;
                }

                if (status != HttpURLConnection.HTTP_OK) {
                    throw new Exception("HTTP Hata Kodu: " + status);
                }

                int fileLength = conn.getContentLength();

                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs();
                }
                tempApk = new File(downloadsDir, fileName);

                java.io.InputStream input = new java.io.BufferedInputStream(conn.getInputStream(), 8192);
                java.io.FileOutputStream output = new java.io.FileOutputStream(tempApk);

                byte[] data = new byte[4096];
                int count;
                long total = 0;
                long lastUpdateTime = 0;
                
                while ((count = input.read(data)) != -1) {
                    total += count;
                    output.write(data, 0, count);
                    
                    if (fileLength > 0) {
                        final int progress = (int) (total * 100 / fileLength);
                        long now = System.currentTimeMillis();
                        if (now - lastUpdateTime > 500) { 
                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (progressDialog.isShowing()) {
                                    progressDialog.setProgress(progress);
                                }
                            });
                            lastUpdateTime = now;
                        }
                    }
                }

                output.flush();
                output.close();
                input.close();

                final File finalApk = tempApk;
                new Handler(Looper.getMainLooper()).post(() -> {
                    isDownloading = false;
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    Toast.makeText(context, context.getString(R.string.car_update_download_complete), Toast.LENGTH_SHORT).show();
                    installApkDirectly(context, finalApk);
                });

            } catch (Exception e) {
                if (tempApk != null && tempApk.exists()) {
                    tempApk.delete();
                }
                new Handler(Looper.getMainLooper()).post(() -> {
                    isDownloading = false;
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    Toast.makeText(context, context.getString(R.string.car_update_download_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                });
                android.util.Log.e("Updater", "Download error", e);
            }
        });
    }

    private static void installApkDirectly(Context context, File apkFile) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(context, context.getString(R.string.car_update_install_permission_required), Toast.LENGTH_LONG).show();
                try {
                    Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                    intent.setData(Uri.parse("package:" + context.getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } catch (Exception e) {
                    android.util.Log.e("Updater", "Ayarlar acilamadi", e);
                }
                return;
            }
        }

        if (apkFile != null && apkFile.exists()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Uri contentUri = null;
                try {
                    contentUri = FileProvider.getUriForFile(context, 
                        context.getPackageName() + ".provider", 
                        apkFile);
                } catch (Exception e) {
                    android.util.Log.e("Updater", "FileProvider URI olusturulurken hata", e);
                }

                if (contentUri != null) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(contentUri, "application/vnd.android.package-archive");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } else {
                    Toast.makeText(context, context.getString(R.string.car_update_uri_creation_failed), Toast.LENGTH_LONG).show();
                }
            } else {
                Uri apkUri = Uri.fromFile(apkFile);
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        } else {
            Toast.makeText(context, context.getString(R.string.car_update_apk_not_found), Toast.LENGTH_LONG).show();
        }
    }
}
