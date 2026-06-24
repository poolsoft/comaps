package app.organicmaps.carlauncher.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.FrameLayout;
import android.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import app.organicmaps.activities.MapActivity;
import app.organicmaps.carlauncher.CarLauncherInterface;
import app.organicmaps.carlauncher.dock.AppDockManager;
import app.organicmaps.carlauncher.dock.AppShortcut;
import app.organicmaps.carlauncher.dock.LaunchMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AppDrawerFragment extends Fragment {

    public static final String TAG = "AppDrawerFragment";

    private RecyclerView recyclerView;
    private AppDrawerAdapter adapter;
    private View loadingView;
    private static List<AppItem> cachedApps; // Static Cache to prevent reloading
    private BroadcastReceiver packageReceiver; // Paket degisikliklerini izlemek icin alici (Turkce karakter yok)
    private final android.os.Handler searchHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable searchRunnable;
    
    // Asynchronous LruCache for holding app icons (Turkce karakter yok)
    private static android.util.LruCache<String, Drawable> iconCache;

    public static void clearCache() {
        cachedApps = null;
        if (iconCache != null) {
            iconCache.evictAll();
        }
    }

    public static class AppItem { // Public static class
        public String label;
        public String packageName;
    }

    // Kolay ve guvenli sekilde uygulama ikonunu yukleme yardimcisi (Turkce karakter yok)
    public static Drawable getAppIcon(Context context, String packageName) {
        if (context == null || packageName == null) {
            return null;
        }
        if (app.organicmaps.carlauncher.dock.InternalApp.isInternalApp(packageName)) {
            app.organicmaps.carlauncher.dock.InternalApp app = app.organicmaps.carlauncher.dock.InternalApp.fromPackageName(packageName);
            if (app != null) return app.getIcon(context);
        } else {
            try {
                return context.getPackageManager().getApplicationIcon(packageName);
            } catch (Exception e) {
                // fallback
            }
        }
        // Varsayilan sistem ikonunu don (Turkce karakter yok)
        try {
            return context.getPackageManager().getDefaultActivityIcon();
        } catch (Exception e) {
            return null;
        }
    }

    // Arka planda ikon yukleyen asenkron gorev sinifi (Turkce karakter yok)
    private static class LoadIconTask extends AsyncTask<Void, Void, Drawable> {
        private final java.lang.ref.WeakReference<ImageView> imageViewReference;
        private final Context context;
        private final String packageName;

        LoadIconTask(ImageView imageView, Context context, String packageName) {
            this.imageViewReference = new java.lang.ref.WeakReference<>(imageView);
            this.context = context.getApplicationContext();
            this.packageName = packageName;
        }

        @Override
        protected Drawable doInBackground(Void... voids) {
            return getAppIcon(context, packageName);
        }

        @Override
        protected void onPostExecute(Drawable drawable) {
            if (drawable != null && iconCache != null) {
                iconCache.put(packageName, drawable);
            }
            ImageView imageView = imageViewReference.get();
            if (imageView != null) {
                String tag = (String) imageView.getTag();
                if (tag != null && tag.equals(packageName)) {
                    imageView.setImageDrawable(drawable);
                }
            }
        }
    }

    private final android.content.ComponentCallbacks2 componentCallbacks = new android.content.ComponentCallbacks2() {
        @Override
        public void onTrimMemory(int level) {
            if (level >= TRIM_MEMORY_MODERATE) {
                clearCache();
            }
        }

        @Override
        public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
        }

        @Override
        public void onLowMemory() {
            clearCache();
        }
    };

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            context.registerComponentCallbacks(componentCallbacks);
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        try {
            if (getContext() != null) {
                getContext().unregisterComponentCallbacks(componentCallbacks);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize Icon Cache dynamically if not exists (Turkce karakter yok)
        if (iconCache == null) {
            int cacheSize = 60; // Dusuk RAM durumlarinda guvenli varsayilan
            try {
                android.app.ActivityManager am = (android.app.ActivityManager) requireContext().getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    int memoryClass = am.getMemoryClass();
                    cacheSize = Math.max(40, Math.min(150, memoryClass / 2));
                }
            } catch (Exception e) {
                // fallback
            }
            iconCache = new android.util.LruCache<>(cacheSize);
        }

        // Paket degisikliklerini dinlemek icin alici (Turkce karakter yok)
        packageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                clearCache();
                if (isAdded() && !isDetached()) {
                    loadApps();
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        if (getContext() != null) {
            getContext().registerReceiver(packageReceiver, filter);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }
        if (packageReceiver != null && getContext() != null) {
            try {
                getContext().unregisterReceiver(packageReceiver);
            } catch (Exception e) {
                // ignore
            }
        }
        try {
            if (getContext() != null) {
                getContext().unregisterComponentCallbacks(componentCallbacks);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(app.organicmaps.R.layout.fragment_app_drawer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Bind Views
        recyclerView = view.findViewById(app.organicmaps.R.id.apps_recycler_view);
        loadingView = view.findViewById(app.organicmaps.R.id.loading_progress);

        android.view.View closeBtn = view.findViewById(app.organicmaps.R.id.btn_close_drawer);
        android.widget.EditText searchInput = view.findViewById(app.organicmaps.R.id.search_input);

        // Logic
        closeBtn.setOnClickListener(v -> closeDrawer());

        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 4));

        // Search Filter (Debounced)
        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }
                searchRunnable = () -> {
                    if (adapter != null) {
                        adapter.filter(s.toString());
                    }
                };
                searchHandler.postDelayed(searchRunnable, 200); // 200ms gecikme (Debounce)
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });

        loadApps();
    }

    private void closeDrawer() {
        if (getActivity() instanceof CarLauncherInterface) {
            ((CarLauncherInterface) getActivity()).closeAppDrawer();
        }
    }

    private void loadApps() {
        // Cache varsa ve doluysa tekrar yukleme
        if (cachedApps != null && !cachedApps.isEmpty()) {
            if (loadingView != null)
                loadingView.setVisibility(View.GONE);
            if (adapter == null) {
                adapter = new AppDrawerAdapter(cachedApps);
                recyclerView.setAdapter(adapter);
            }
            return;
        }

        // Cache yoksa yukle
        // Cache yoksa yukle
        if (loadingView != null)
            loadingView.setVisibility(View.VISIBLE);
        if (getContext() != null) {
            new LoadAppsTask(getContext()).execute();
        }
    }

    // Broadcast Receiver for App Updates
    private class LoadAppsTask extends AsyncTask<Void, Void, List<AppItem>> {
        private final Context context;

        public LoadAppsTask(Context context) {
            this.context = context.getApplicationContext(); // Use application context
        }

        @Override
        protected List<AppItem> doInBackground(Void... voids) {
            List<AppItem> apps = new ArrayList<>();
            if (context == null)
                return apps;
            PackageManager pm = context.getPackageManager();

            Intent intent = new Intent(Intent.ACTION_MAIN, null);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);

            List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);

            for (ResolveInfo info : activities) {
                AppItem item = new AppItem();
                item.label = info.loadLabel(pm).toString();
                item.packageName = info.activityInfo.packageName;
                apps.add(item);
            }

            Collections.sort(apps, (a, b) -> a.label.compareToIgnoreCase(b.label));

            // Add internal apps at the beginning
            List<AppItem> internalApps = getInternalApps();
            apps.addAll(0, internalApps);

            return apps;
        }

        private List<AppItem> getInternalApps() {
            List<AppItem> internal = new ArrayList<>();
            for (app.organicmaps.carlauncher.dock.InternalApp app : app.organicmaps.carlauncher.dock.InternalApp.values()) {
                AppItem item = new AppItem();
                item.label = app.getDefaultName();
                item.packageName = app.getPackageName();
                internal.add(item);
            }
            return internal;
        }

        @Override
        protected void onPostExecute(List<AppItem> appItems) {
            cachedApps = appItems; // Cache'e kaydet
            if (loadingView != null)
                loadingView.setVisibility(View.GONE);
            adapter = new AppDrawerAdapter(appItems);
            if (recyclerView != null)
                recyclerView.setAdapter(adapter);
        }
    }



    private class AppDrawerAdapter extends RecyclerView.Adapter<AppDrawerAdapter.ViewHolder> {

        private List<AppItem> originalApps;
        private List<AppItem> displayedApps;

        AppDrawerAdapter(List<AppItem> apps) {
            this.originalApps = new ArrayList<>(apps);
            this.displayedApps = new ArrayList<>(apps);
        }

        void filter(String query) {
            displayedApps.clear();
            if (android.text.TextUtils.isEmpty(query)) {
                displayedApps.addAll(originalApps);
            } else {
                String q = query.toLowerCase();
                for (AppItem item : originalApps) {
                    if (item.label.toLowerCase().contains(q)) {
                        displayedApps.add(item);
                    }
                }
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // Using ID directly might fail if R is not imported correctly, but following
            // pattern
            View view = LayoutInflater.from(parent.getContext()).inflate(app.organicmaps.R.layout.item_app_drawer,
                    parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppItem item = displayedApps.get(position);
            holder.textView.setText(item.label);
            
            // Geri donusum sirasinda yanlis ikon gosterilmesini engelle (Turkce karakter yok)
            holder.iconView.setTag(item.packageName);

            Drawable cachedIcon = iconCache != null ? iconCache.get(item.packageName) : null;
            if (cachedIcon != null) {
                holder.iconView.setImageDrawable(cachedIcon);
            } else {
                // Varsayilan bos/placeholder ikon set et (Turkce karakter yok)
                holder.iconView.setImageDrawable(null);
                if (holder.iconView.getContext() != null) {
                    new LoadIconTask(holder.iconView, holder.iconView.getContext(), item.packageName).execute();
                }
            }

            holder.itemView.setOnClickListener(v -> {
                launchApp(item.packageName);
                closeDrawer();
            });

            holder.itemView.setOnLongClickListener(v -> {
                showAppOptions(item);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return displayedApps.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView iconView;
            TextView textView;

            ViewHolder(View itemView) {
                super(itemView);
                iconView = itemView.findViewById(app.organicmaps.R.id.app_icon);
                textView = itemView.findViewById(app.organicmaps.R.id.app_label);
            }
        }
    }

    private void showAppOptions(AppItem item) {
        String[] options = { "Dock'a Ekle (Standart)", "Dock'a Ekle (Split-Screen)", "Dock'a Ekle (Overlay)", "Uygulama Bilgisi" };

        new AlertDialog.Builder(getContext())
                .setTitle(item.label)
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            addToDock(item, LaunchMode.FULL_SCREEN);
                            break;
                        case 1:
                            addToDock(item, LaunchMode.SPLIT_SCREEN);
                            break;
                        case 2:
                            addToDock(item, LaunchMode.OVERLAY);
                            break;
                        case 3:
                            showAppInfo(item.packageName);
                            break;
                    }
                })
                .setNegativeButton("Iptal", null)
                .show();
    }

    private void addToDock(AppItem item, LaunchMode mode) {
        AppDockManager dockManager = new AppDockManager(getContext());
        dockManager.loadShortcuts();

        if (!dockManager.canAddMore()) {
            new AlertDialog.Builder(getContext())
                    .setTitle("Hata")
                    .setMessage("Dock dolu! Maksimum " + dockManager.getMaxShortcuts() + " kisayol.")
                    .setPositiveButton("Tamam", null)
                    .show();
            return;
        }

        int order = dockManager.getShortcuts().size();
        Drawable icon = iconCache != null ? iconCache.get(item.packageName) : null;
        if (icon == null && getContext() != null) {
            icon = getAppIcon(getContext(), item.packageName);
        }
        AppShortcut shortcut = new AppShortcut(item.packageName, item.label, icon, order, mode);

        if (dockManager.addShortcut(shortcut)) {
            Toast.makeText(getContext(), item.label + " dock'a eklendi.", Toast.LENGTH_SHORT).show();

            // Send broadcast to refresh Dock (Both Local and Global for safety)
            Intent updateIntent = new Intent("net.osmand.carlauncher.DOCK_UPDATED");
            updateIntent.setPackage(getContext().getPackageName()); // Security
            getContext().sendBroadcast(updateIntent);
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(getContext()).sendBroadcast(updateIntent);
        }
        closeDrawer();
    }

    private void launchApp(String packageName) {
        // Handle internal apps
        if (app.organicmaps.carlauncher.dock.InternalApp.isInternalApp(packageName)) {
            app.organicmaps.carlauncher.dock.InternalAppLauncher.launch(getContext(), packageName);
            return;
        }

        try {
            Intent intent = getContext().getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    public static List<AppItem> getCachedApps() {
        return cachedApps;
    }

    public static android.util.LruCache<String, Drawable> getIconCache() {
        return iconCache;
    }

    private void showAppInfo(String packageName) {
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
