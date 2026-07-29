package app.organicmaps.carlauncher.dock;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

public class AppPickerDialog {

    public interface OnAppSelectedListener {
        void onAppSelected(String packageName, String appName, Drawable icon);
    }

    private final Context context;
    private final OnAppSelectedListener listener;
    private final boolean onlyMusicApps;
    private BottomSheetDialog dialog;
    private String activePackage;

    public AppPickerDialog(@NonNull Context context, @NonNull OnAppSelectedListener listener) {
        this(context, false, listener);
    }

    public AppPickerDialog(@NonNull Context context, boolean onlyMusicApps, @NonNull OnAppSelectedListener listener) {
        this.context = context;
        this.onlyMusicApps = onlyMusicApps;
        this.listener = listener;
    }

    public void setActivePackage(String packageName) {
        this.activePackage = packageName;
    }

    public void show() {
        dialog = new BottomSheetDialog(context);
        
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(android.graphics.Color.parseColor("#1C1C1E"));
        root.setPadding(16, 24, 16, 16);
        
        View handle = new View(context);
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(96, 12);
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.bottomMargin = 32;
        handle.setBackgroundColor(android.graphics.Color.GRAY);
        root.addView(handle, handleLp);

        TextView titleView = new TextView(context);
        titleView.setText(onlyMusicApps ? context.getString(app.organicmaps.R.string.car_music_app_select) : context.getString(app.organicmaps.R.string.car_app_select));
        titleView.setTextColor(android.graphics.Color.WHITE);
        titleView.setTextSize(18);
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(0, 0, 0, 32);
        root.addView(titleView);

        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new GridLayoutManager(context, 4));
        AppInfoAdapter adapter = new AppInfoAdapter(app -> {
            if (listener != null) listener.onAppSelected(app.packageName, app.name, app.icon);
            dialog.dismiss();
        });
        recyclerView.setAdapter(adapter);
        root.addView(recyclerView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                (int)(context.getResources().getDisplayMetrics().heightPixels * 0.6f)));

        List<AppInfo> apps = getInstalledApps();
        adapter.setApps(apps);
        adapter.setActivePackage(this.activePackage);

        dialog.setContentView(root);
        View parent = (View) root.getParent();
        if (parent != null) parent.setBackgroundColor(android.graphics.Color.TRANSPARENT);

        dialog.setOnShowListener(d -> {
            com.google.android.material.bottomsheet.BottomSheetDialog bsd = (com.google.android.material.bottomsheet.BottomSheetDialog) d;
            View bottomSheetInternal = bsd.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheetInternal != null) {
                com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheetInternal).setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        dialog.show();
    }

    private static List<AppInfo> cachedAllApps = null;
    private static List<AppInfo> cachedMusicApps = null;

    private List<AppInfo> getInstalledApps() {
        if (onlyMusicApps && cachedMusicApps != null) return cachedMusicApps;
        if (!onlyMusicApps && cachedAllApps != null) return cachedAllApps;

        List<AppInfo> apps = new ArrayList<>();
        
        // AppDrawer'daki cache'lenmis uygulamalari ve ikonlari kullan (Turkce karakter yok)
        List<app.organicmaps.carlauncher.ui.AppDrawerFragment.AppItem> cachedList = 
                app.organicmaps.carlauncher.ui.AppDrawerFragment.getCachedApps();
        android.util.LruCache<String, Drawable> cacheIcons = 
                app.organicmaps.carlauncher.ui.AppDrawerFragment.getIconCache();

        if (onlyMusicApps) {
            AppInfo internalPlayer = new AppInfo();
            internalPlayer.name = "Dahili Muzik Calar";
            internalPlayer.packageName = "usage.internal.player";
            internalPlayer.icon = context.getResources().getDrawable(android.R.drawable.ic_media_play, null);
            apps.add(internalPlayer);
        }

        if (!onlyMusicApps) {
            // Dahili Sistem Uygulamalari (Picker uzerinden secilebilmesi icin)
            for (app.organicmaps.carlauncher.dock.InternalApp internalApp : app.organicmaps.carlauncher.dock.InternalApp.values()) {
                AppInfo app = new AppInfo();
                app.name = internalApp.getDefaultName();
                app.packageName = internalApp.getPackageName();
                app.icon = internalApp.getIcon(context);
                apps.add(app);
            }
        }

        if (cachedList != null && !cachedList.isEmpty()) {
            PackageManager pm = context.getPackageManager();
            List<String> musicPackages = new ArrayList<>();
            if (onlyMusicApps) {
                Intent musicIntent = new Intent("android.media.browse.MediaBrowserService");
                List<ResolveInfo> musicServices = pm.queryIntentServices(musicIntent, 0);
                for (ResolveInfo info : musicServices) musicPackages.add(info.serviceInfo.packageName);
                
                musicPackages.add("com.acloud.stub.localmusic");
                musicPackages.add("com.acloud.stub.extradio");
                musicPackages.add("com.hcn.AutoMediaPlayer");
                musicPackages.add("com.hcn.autoradio");
                musicPackages.add("com.zmarties.zlink");
                musicPackages.add("com.zmarties.zlink2");
                musicPackages.add("com.xyauto.zlink");
                musicPackages.add("com.zrun.zlink");
                musicPackages.add("com.xyauto.music");
                musicPackages.add("com.android.music");
                musicPackages.add("com.txznet.music");
                musicPackages.add("com.syd.music");
                musicPackages.add("com.mediatek.music");
                musicPackages.add("com.spotify.music");
                musicPackages.add("com.google.android.apps.youtube.music");
                musicPackages.add("com.google.android.youtube");
                musicPackages.add("com.apple.android.music");
                musicPackages.add("deezer.android.app");
                musicPackages.add("com.aspiro.tidal");
                musicPackages.add("com.soundcloud.android");
                musicPackages.add("tunein.player");
                musicPackages.add("org.videolan.vlc");
            }

            for (app.organicmaps.carlauncher.ui.AppDrawerFragment.AppItem item : cachedList) {
                // Dahili uygulamalari secici listesinde gosterme (Turkce karakter yok)
                if (app.organicmaps.carlauncher.dock.InternalApp.isInternalApp(item.packageName)) {
                    continue;
                }
                
                if (onlyMusicApps && !musicPackages.contains(item.packageName)) {
                    continue;
                }

                Drawable icon = null;
                if (cacheIcons != null) {
                    icon = cacheIcons.get(item.packageName);
                }
                if (icon == null) {
                    try {
                        icon = pm.getApplicationIcon(item.packageName);
                    } catch (Exception e) {
                        icon = context.getResources().getDrawable(android.R.drawable.sym_def_app_icon, null);
                    }
                }

                AppInfo app = new AppInfo();
                app.name = item.label;
                app.packageName = item.packageName;
                app.icon = icon;
                apps.add(app);
            }
            
            if (onlyMusicApps) {
                addFallbackStubApps(apps);
            }
            Collections.sort(apps, (a1, a2) -> a1.name.compareToIgnoreCase(a2.name));
            if (onlyMusicApps) cachedMusicApps = apps;
            else cachedAllApps = apps;
            return apps;
        }

        // Cache henuz yuklenmemisse fallback olarak senkron cagirir (Turkce karakter yok)
        PackageManager pm = context.getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<String> musicPackages = new ArrayList<>();
        if (onlyMusicApps) {
            Intent musicIntent = new Intent("android.media.browse.MediaBrowserService");
            List<ResolveInfo> musicServices = pm.queryIntentServices(musicIntent, 0);
            for (ResolveInfo info : musicServices) musicPackages.add(info.serviceInfo.packageName);
            
            musicPackages.add("com.acloud.stub.localmusic");
            musicPackages.add("com.acloud.stub.extradio");
            musicPackages.add("com.hcn.AutoMediaPlayer");
            musicPackages.add("com.hcn.autoradio");
            musicPackages.add("com.zmarties.zlink");
            musicPackages.add("com.zmarties.zlink2");
            musicPackages.add("com.xyauto.zlink");
            musicPackages.add("com.zrun.zlink");
            musicPackages.add("com.xyauto.music");
            musicPackages.add("com.android.music");
            musicPackages.add("com.txznet.music");
            musicPackages.add("com.syd.music");
            musicPackages.add("com.mediatek.music");
            musicPackages.add("com.spotify.music");
            musicPackages.add("com.google.android.apps.youtube.music");
            musicPackages.add("com.google.android.youtube");
            musicPackages.add("com.apple.android.music");
            musicPackages.add("deezer.android.app");
            musicPackages.add("com.aspiro.tidal");
            musicPackages.add("com.soundcloud.android");
            musicPackages.add("tunein.player");
            musicPackages.add("org.videolan.vlc");
        }

        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(mainIntent, 0);
        for (ResolveInfo info : resolveInfos) {
            try {
                String packageName = info.activityInfo.packageName;
                if (onlyMusicApps && !musicPackages.contains(packageName)) continue;
                ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                AppInfo app = new AppInfo();
                app.name = appInfo.loadLabel(pm).toString();
                app.packageName = packageName;
                app.icon = appInfo.loadIcon(pm);
                apps.add(app);
            } catch (Exception e) { }
        }

        if (onlyMusicApps) {
            addFallbackStubApps(apps);
        }
        Collections.sort(apps, (a1, a2) -> a1.name.compareToIgnoreCase(a2.name));
        if (onlyMusicApps) cachedMusicApps = apps;
        else cachedAllApps = apps;
        return apps;
    }

    private void addFallbackStubApps(List<AppInfo> apps) {
        String[][] stubs = {
            {"XYAuto Yerel Muzik", "com.acloud.stub.localmusic"},
            {"XYAuto Yerel Radyo", "com.acloud.stub.extradio"},
            {"HCN Yerel Muzik", "com.hcn.AutoMediaPlayer"},
            {"HCN Yerel Radyo", "com.hcn.autoradio"},
            {"ZLink", "com.zmarties.zlink"}
        };
        for (String[] stub : stubs) {
            boolean found = false;
            for (AppInfo existing : apps) {
                if (stub[1].equals(existing.packageName)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                AppInfo app = new AppInfo();
                app.name = stub[0];
                app.packageName = stub[1];
                app.icon = context.getResources().getDrawable(android.R.drawable.ic_media_play, null);
                apps.add(app);
            }
        }
    }

    public static void clearCache() {
        cachedAllApps = null;
        cachedMusicApps = null;
    }

    public static class AppInfo {
        public String name;
        public String packageName;
        public Drawable icon;
    }
}
