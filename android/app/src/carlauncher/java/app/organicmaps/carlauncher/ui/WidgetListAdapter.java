package app.organicmaps.carlauncher.ui;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import app.organicmaps.carlauncher.widgets.BaseWidget;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WidgetListAdapter extends RecyclerView.Adapter<WidgetListAdapter.WidgetViewHolder> {

    private static final int VIEW_TYPE_WIDGET = 0;
    private static final int VIEW_TYPE_ADD = 1;

    public interface OnWidgetActionListener {
        void onWidgetOrderChanged(List<BaseWidget> newOrder);
        void onWidgetRemoved(BaseWidget widget);
        void onAddWidgetClicked();
        void onWidgetLongClicked(View view, BaseWidget widget);
        void onStartDrag(RecyclerView.ViewHolder viewHolder);
    }

    private final List<BaseWidget> widgets;
    private boolean isHorizontalScroll; // Replaces isPortrait
    private boolean isMetroMode = false;
    private final OnWidgetActionListener actionListener;
    private int unitSize = 0;

    public WidgetListAdapter(List<BaseWidget> widgets, boolean isHorizontalScroll, OnWidgetActionListener actionListener) {
        this.widgets = widgets;
        this.isHorizontalScroll = isHorizontalScroll;
        this.actionListener = actionListener;
    }

    public BaseWidget getWidgetAt(int position) {
        if (position >= 0 && position < widgets.size()) {
            return widgets.get(position);
        }
        return null;
    }

    public void setUnitSize(int newSize, boolean newHorizontal) {
        boolean changed = (this.unitSize != newSize || this.isHorizontalScroll != newHorizontal);
        this.unitSize = newSize;
        this.isHorizontalScroll = newHorizontal;
        // Sadece deger degistiyse yenile
        if (changed) notifyDataSetChanged();
    }

    public void setMetroMode(boolean isMetro) {
        boolean changed = (this.isMetroMode != isMetro);
        this.isMetroMode = isMetro;
        if (changed) notifyDataSetChanged();
    }

    /**
     * Mevcut adapter'i tamamen yeniden olusturmak yerine listeyi guncelle.
     * onResume'da adapter'i resetlemek yerine bu cagrilmali.
     */
    public void refresh(List<BaseWidget> newWidgets) {
        this.widgets.clear();
        this.widgets.addAll(newWidgets);
        notifyDataSetChanged();
    }
    
    // Check Drag & Drop
    public void onItemMove(int fromPosition, int toPosition) {
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(widgets, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(widgets, i, i - 1);
            }
        }
        notifyItemMoved(fromPosition, toPosition);
        // DB update now handled in Fragment onDragEnd (clearView)
    }

    public List<BaseWidget> getWidgets() {
        return widgets;
    }

    @Override
    public int getItemViewType(int position) {
        return VIEW_TYPE_WIDGET;
    }

    @NonNull
    @Override
    public WidgetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        FrameLayout container = new FrameLayout(parent.getContext());
        
        ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 0); 
        
        container.setLayoutParams(params);
        return new WidgetViewHolder(container);
    }

    @Override
    public void onBindViewHolder(@NonNull WidgetViewHolder holder, int position) {
        int margin = dpToPx(holder.itemView.getContext(), 6);
        int marginTotal = margin * 2;
        
        // --- 1. Sizing Calculation ---
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
        if (params == null) {
            params = new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        params.setMargins(margin, margin, margin, margin);

        if (unitSize > 0) {
             if (isMetroMode) {
                // METRO MODE
                int widthMult = 1;
                int heightMult = 1;
                
                BaseWidget w = widgets.get(position);
                switch (w.getSize()) {
                    case MEDIUM: widthMult = 2; heightMult = 1; break;
                    case LARGE:  widthMult = 2; heightMult = 2; break;
                    default: widthMult = 1; heightMult = 1; break;
                }
                
                params.width = (unitSize * widthMult) - marginTotal;
                params.height = (unitSize * heightMult) - marginTotal;
                
            } else {
                // CLASSIC MODE
                if (isHorizontalScroll) {
                    params.width = unitSize - marginTotal; 
                    params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                } else {
                    // Classic Vertical: iOS stili sabit premium yukseklik standartlari (Turkce karakter yok)
                    int heightDp;
                    BaseWidget.WidgetSize size = widgets.get(position).getSize();
                    if (size == BaseWidget.WidgetSize.SMALL) {
                        heightDp = 110; 
                    } else if (size == BaseWidget.WidgetSize.MEDIUM) {
                        heightDp = 160;
                    } else { // LARGE
                        heightDp = 220;
                    }
                    params.height = dpToPx(holder.itemView.getContext(), heightDp);
                    params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                }
            }
        } else {
            // unitSize henuz olculmediyse bile dikey modda sabit yukseklikleri uygula (Turkce karakter yok)
            if (!isMetroMode && !isHorizontalScroll) {
                int heightDp;
                BaseWidget.WidgetSize size = widgets.get(position).getSize();
                if (size == BaseWidget.WidgetSize.SMALL) {
                    heightDp = 110; 
                } else if (size == BaseWidget.WidgetSize.MEDIUM) {
                    heightDp = 160;
                } else { // LARGE
                    heightDp = 220;
                }
                params.height = dpToPx(holder.itemView.getContext(), heightDp);
                params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            }
        }
        holder.itemView.setLayoutParams(params);

        // --- 2. Content Binding ---
        // Clean up
        holder.container.removeAllViews();
        
        // A. Add Widget View
        BaseWidget widget = widgets.get(position);
        View widgetView = widget.getRootView();
        if (widgetView == null) widgetView = widget.createView();
        
        if (widgetView != null) {
            if (widgetView.getParent() != null) ((ViewGroup)widgetView.getParent()).removeView(widgetView);
            holder.container.addView(widgetView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        
        // B. Add Gradient Overlay
        // REMOVED by request (User disabled field)
        /*
        if (holder.gradientOverlay.getParent() != null) ((ViewGroup)holder.gradientOverlay.getParent()).removeView(holder.gradientOverlay);
        holder.container.addView(holder.gradientOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        */
        
        // C. Add Drag Overlay (on top)
        if (holder.overlay.getParent() != null) ((ViewGroup)holder.overlay.getParent()).removeView(holder.overlay);
        holder.container.addView(holder.overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        
        // D. Listeners
        holder.itemView.setOnClickListener(null); 
        holder.itemView.setOnLongClickListener(v -> {
            if (actionListener != null) {
                actionListener.onStartDrag(holder);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return widgets.size();
    }
    
    private int dpToPx(android.content.Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    static class WidgetViewHolder extends RecyclerView.ViewHolder {
        FrameLayout container;
        FrameLayout overlay;
        //View gradientOverlay;

        public WidgetViewHolder(@NonNull View itemView) {
            super(itemView);
            container = (FrameLayout) itemView;

            // Gradient Overlay - NON-CLICKABLE
            // gradientOverlay = new View(itemView.getContext());
            // gradientOverlay.setBackgroundResource(app.organicmaps.R.drawable.bg_gradient_overlay);
            // gradientOverlay.setClickable(false);
            // gradientOverlay.setFocusable(false);

            // Create Overlay for Drag Feedback
            overlay = new FrameLayout(itemView.getContext());
            overlay.setClickable(false);
            overlay.setFocusable(false);
            
            // 1. Icon (Move Symbol)
            ImageView icon = new ImageView(itemView.getContext());
            icon.setImageResource(app.organicmaps.R.drawable.ic_action_settings); 
            
            icon.setColorFilter(0xFFFFFFFF); // White icon
            
            // Background for Icon (Circle)
            android.graphics.drawable.GradientDrawable iconBg = new android.graphics.drawable.GradientDrawable();
            iconBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            iconBg.setColor(0xAA000000); // Semi-transparent black capsule
            icon.setBackground(iconBg);
            
            // Padding for Icon
            int p = (int) (12 * itemView.getContext().getResources().getDisplayMetrics().density);
            icon.setPadding(p, p, p, p);
            
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            iconParams.gravity = android.view.Gravity.CENTER;
            overlay.addView(icon, iconParams);
            
            // 2. Border (on Overlay itself)
            android.graphics.drawable.GradientDrawable border = new android.graphics.drawable.GradientDrawable();
            border.setStroke((int) (4 * itemView.getContext().getResources().getDisplayMetrics().density), 
                             0xFFFF4081); // Thick Accent Border (Pink/Red)
            border.setColor(0x66000000); // Darken content background
            overlay.setBackground(border);
            
            overlay.setVisibility(View.GONE);
            
            // Views are added in onBindViewHolder
        }

        public void setDragState(boolean isDragging) {
            overlay.setVisibility(isDragging ? View.VISIBLE : View.GONE);
            if (isDragging) {
                itemView.animate().scaleX(1.05f).scaleY(1.05f).setDuration(100).start();
                itemView.setElevation(10f); // Shadow
            } else {
                 itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                 itemView.setElevation(0f);
            }
        }
    }
}
