package app.organicmaps.carlauncher.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceViewHolder;
import androidx.core.content.ContextCompat;

import app.organicmaps.R;

public class CarLauncherPreferenceCategory extends PreferenceCategory {

    private Drawable customIcon;

    public CarLauncherPreferenceCategory(Context context, AttributeSet attrs) {
        super(context, attrs);
        
        // Attrs'den app:icon degerini manuel olarak alalim ki Preference sistemine gondermeyelim
        int iconResId = attrs.getAttributeResourceValue("http://schemas.android.com/apk/res-auto", "icon", 0);
        if (iconResId == 0) {
            iconResId = attrs.getAttributeResourceValue("http://schemas.android.com/apk/res/android", "icon", 0);
        }
        
        if (iconResId != 0) {
            customIcon = ContextCompat.getDrawable(context, iconResId);
        }
        
        setLayoutResource(R.layout.carlauncher_preference_category);
        
        // Bu hamle kilit noktadir! 
        // AndroidX Preference sistemi "bunun ikonu yok" sanacak ve 
        // altindakileri saga dogru kaydirmayacak (isIconSpaceReserved iptal olacak).
        super.setIcon(null); 
    }

    @Override
    public Drawable getIcon() {
        return customIcon;
    }

    @Override
    public void setIcon(Drawable icon) {
        this.customIcon = icon;
        notifyChanged();
    }

    @Override
    public void setIcon(int iconResId) {
        if (iconResId != 0) {
            this.customIcon = ContextCompat.getDrawable(getContext(), iconResId);
            notifyChanged();
        }
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        
        ImageView iconView = (ImageView) holder.findViewById(android.R.id.icon);
        if (iconView != null) {
            if (customIcon != null) {
                iconView.setImageDrawable(customIcon);
                iconView.setVisibility(android.view.View.VISIBLE);
            } else {
                iconView.setVisibility(android.view.View.GONE);
            }
        }
        
        // Basligi kendi layoutumuzdan bulup rengini vs setleyebiliriz
        // (Zaten XML'de colorAccent atadik, ama ek islem yapilabilir)
    }
}
