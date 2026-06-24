package app.organicmaps.carlauncher.dock;
// Sync Fix: AppInfoAdapter for CarLauncher
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class AppInfoAdapter extends RecyclerView.Adapter<AppInfoAdapter.ViewHolder> {

    public interface OnAppClickListener {
        void onAppClick(AppPickerDialog.AppInfo app);
    }

    private final List<AppPickerDialog.AppInfo> apps = new ArrayList<>();
    private final OnAppClickListener listener;

    public AppInfoAdapter(OnAppClickListener listener) {
        this.listener = listener;
    }

    public void setApps(List<AppPickerDialog.AppInfo> newApps) {
        apps.clear();
        apps.addAll(newApps);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        float density = parent.getContext().getResources().getDisplayMetrics().density;
        int iconSize = (int) (60 * density);
        int paddingVertical = (int) (16 * density);

        LinearLayout itemView = new LinearLayout(parent.getContext());
        itemView.setOrientation(LinearLayout.VERTICAL);
        itemView.setGravity(android.view.Gravity.CENTER);
        itemView.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT));
        itemView.setPadding(0, paddingVertical, 0, paddingVertical);
        itemView.setClickable(true);
        itemView.setFocusable(true);
        itemView.setBackgroundResource(android.R.drawable.list_selector_background);

        ImageView iconView = new ImageView(parent.getContext());
        iconView.setId(View.generateViewId());
        iconView.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
        itemView.addView(iconView);

        TextView nameView = new TextView(parent.getContext());
        nameView.setId(View.generateViewId());
        nameView.setTextColor(android.graphics.Color.WHITE);
        nameView.setTextSize(13);
        nameView.setGravity(android.view.Gravity.CENTER);
        nameView.setLines(1);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (8 * density);
        itemView.addView(nameView, lp);

        return new ViewHolder(itemView, iconView, nameView);
    }

    private static class LinearLayout extends android.widget.LinearLayout {
        public LinearLayout(Context context) { super(context); }
    }

    private String activePackage;

    public void setActivePackage(String pkg) {
        this.activePackage = pkg;
        notifyDataSetChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppPickerDialog.AppInfo app = apps.get(position);
        holder.nameView.setText(app.name);
        holder.iconView.setImageDrawable(app.icon);
        holder.itemView.setOnClickListener(v -> listener.onAppClick(app));

        // Highlight logic
        if (app.packageName != null && app.packageName.equals(activePackage)) {
            holder.itemView.setBackgroundColor(android.graphics.Color.parseColor("#334488FF"));
        } else {
            holder.itemView.setBackgroundResource(android.R.drawable.list_selector_background);
        }
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView iconView;
        final TextView nameView;

        ViewHolder(View itemView, ImageView iconView, TextView nameView) {
            super(itemView);
            this.iconView = iconView;
            this.nameView = nameView;
        }
    }
}
