package app.organicmaps.carlauncher.ui;

import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import app.organicmaps.carlauncher.widgets.BaseWidget;

import java.util.List;

public class WidgetPagerAdapter extends RecyclerView.Adapter<WidgetPagerAdapter.WidgetViewHolder> {

    private final List<BaseWidget> widgets;

    public WidgetPagerAdapter(List<BaseWidget> widgets) {
        this.widgets = widgets;
    }

    @NonNull
    @Override
    public WidgetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Create a FrameLayout container for the widget
        FrameLayout container = new FrameLayout(parent.getContext());
        container.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        
        // Add padding if needed
        int padding = 4; // basic padding
        container.setPadding(padding, padding, padding, padding);
        
        return new WidgetViewHolder(container);
    }

    @Override
    public void onBindViewHolder(@NonNull WidgetViewHolder holder, int position) {
        BaseWidget widget = widgets.get(position);
        android.view.View widgetView = widget.getRootView();
        
        if (widgetView == null) {
            widgetView = widget.createView();
        }

        if (widgetView != null) {
            // Remove from previous parent if exists
            if (widgetView.getParent() != null) {
                ((ViewGroup) widgetView.getParent()).removeView(widgetView);
            }

            // Clean container
            holder.container.removeAllViews();

            // Layout Params: Match Parent for Paged Mode
            holder.container.addView(widgetView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    @Override
    public int getItemCount() {
        return widgets.size();
    }

    static class WidgetViewHolder extends RecyclerView.ViewHolder {
        FrameLayout container;

        public WidgetViewHolder(@NonNull android.view.View itemView) {
            super(itemView);
            container = (FrameLayout) itemView;
        }
    }
}
