package app.organicmaps.carlauncher.widgets;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

/**
 * Workspace grid'inde uygulama kisayollarini (Spotify, Haritalar vb.) 
 * 1x1 boyutunda gostermek ve baslatmak icin ozel widget sinifi.
 *
 * Kod icerisinde kesinlikle Turkce karakter kullanilmamistir.
 */
public class AppShortcutWidget extends BaseWidget {

    private final String packageName;

    public AppShortcutWidget(@NonNull Context context, @NonNull String packageName, @NonNull String label) {
        super(context, "shortcut_" + packageName, label);
        this.packageName = packageName;
        this.spanX = 1;
        this.spanY = 1;
        this.size = WidgetSize.SMALL;
    }

    public String getPackageName() {
        return packageName;
    }

    @Override
    public void setSize(WidgetSize size) {
        // Kisayollar her zaman 1x1 kalir, boyut degisikligini yoksayiyoruz.
        this.size = WidgetSize.SMALL;
        this.spanX = 1;
        this.spanY = 1;
    }

    @NonNull
    @Override
    public View createView() {
        Context ctx = getContext();
        if (ctx == null) {
            ctx = context;
        }

        // Ana Yerlesim (Dinamik Olceklenebilir Dikey Kutu)
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        
        // Kenar padding'ini kucuk tutuyoruz (2dp) ki ikon ve metne maksimum alan kalsin.
        int padding = dpToPx(2);
        layout.setPadding(padding, padding, padding, padding);

        // Uygulama Ikonu (Agirlik = 0.65f)
        ImageView iconView = new ImageView(ctx);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        
        // Ikon yuksekligini dikeyde esnek yapiyoruz. Boylece ikon dikey alanin yaklasik %65'ini kaplar.
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                0, 
                0.65f);
        iconLp.bottomMargin = dpToPx(4);
        iconView.setLayoutParams(iconLp);

        Drawable icon = app.organicmaps.carlauncher.ui.AppDrawerFragment.getAppIcon(ctx, packageName);
        if (icon != null) {
            iconView.setImageDrawable(icon);
        } else {
            iconView.setImageResource(android.R.drawable.sym_def_app_icon);
        }
        layout.addView(iconView);

        // Uygulama Adi (Kucuk ve Net, Agirlik = 0.25f)
        TextView labelView = new TextView(ctx);
        labelView.setText(title);
        labelView.setTextColor(Color.WHITE);
        labelView.setTextSize(10); // Okunabilir sabit kucuk punto
        labelView.setGravity(Gravity.CENTER);
        labelView.setSingleLine(true);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        
        // Metin alanini da dikeyde esnek yapiyoruz (Agirlik = 0.25f)
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                0.25f);
        labelView.setLayoutParams(labelLp);
        layout.addView(labelView);

        // Tiklama Dinleyicisi (Uygulamayi baslatir)
        layout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Eger edit modundaysak uygulamayi baslatma (tasimaya engel olmamak icin)
                if (app.organicmaps.carlauncher.widgets.WorkspacePageAdapter.isEditMode) {
                    return;
                }
                
                // Dahili uygulamalari ac (Turkce karakter yok)
                if (app.organicmaps.carlauncher.dock.InternalApp.isInternalApp(packageName)) {
                    app.organicmaps.carlauncher.dock.InternalAppLauncher.launch(v.getContext(), packageName);
                    return;
                }

                try {
                    Intent launchIntent = v.getContext().getPackageManager().getLaunchIntentForPackage(packageName);
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        v.getContext().startActivity(launchIntent);
                    } else {
                        Toast.makeText(v.getContext(), 
                                title + " baslatilamadi.", 
                                Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(v.getContext(), 
                            "Hata: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        rootView = layout;
        return layout;
    }

    @Override
    public void update() {
        // Kisayollarin dinamik veri guncelleme ihtiyaci yoktur.
    }

    private int dpToPx(int dp) {
        Context ctx = getContext();
        if (ctx == null) {
            ctx = context;
        }
        float density = ctx.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
