package app.organicmaps.carlauncher.ui;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import app.organicmaps.R;
import app.organicmaps.carlauncher.widgets.BaseWidget;

import java.util.Collections;
import java.util.List;

public class WidgetControlAdapter extends RecyclerView.Adapter<WidgetControlAdapter.ControlViewHolder> {

    public interface OnControlActionListener {
        void onStartDrag(RecyclerView.ViewHolder viewHolder);
        void onDeleteClicked(BaseWidget widget, int position);
        void onConfigClicked(BaseWidget widget, int position);
    }

    private final List<BaseWidget> widgets;
    private final OnControlActionListener listener;

    public WidgetControlAdapter(List<BaseWidget> widgets, OnControlActionListener listener) {
        this.widgets = widgets;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ControlViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_widget_control_row, parent, false);
        return new ControlViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ControlViewHolder holder, int position) {
        BaseWidget widget = widgets.get(position);

        // Name
        // Name with ID for validation
        String dispName = widget.getTitle();
        if (widget.getId().contains("_")) {
             // Show short hash for unique widgets
             String hash = widget.getId().substring(widget.getId().lastIndexOf("_") + 1);
             if (hash.length() > 4) hash = hash.substring(hash.length() - 4);
             dispName += " #" + hash;
        }
        holder.nameText.setText(dispName);

        // Size Buttons State
        updateSizeButtons(holder, widget.getSize());

        // Listeners for Size
        holder.btnS.setOnClickListener(v -> {
            widget.setSize(BaseWidget.WidgetSize.SMALL);
            updateSizeButtons(holder, BaseWidget.WidgetSize.SMALL);
        });
        holder.btnM.setOnClickListener(v -> {
            widget.setSize(BaseWidget.WidgetSize.MEDIUM);
            updateSizeButtons(holder, BaseWidget.WidgetSize.MEDIUM);
        });
        holder.btnL.setOnClickListener(v -> {
            widget.setSize(BaseWidget.WidgetSize.LARGE);
            updateSizeButtons(holder, BaseWidget.WidgetSize.LARGE);
        });

        // Delete
        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDeleteClicked(widget, holder.getAdapterPosition());
        });

        // Config
        if (widget.isConfigurable()) {
            holder.btnConfig.setVisibility(View.VISIBLE);
            holder.btnConfig.setOnClickListener(v -> {
                if (listener != null) listener.onConfigClicked(widget, holder.getAdapterPosition());
            });
        } else {
            holder.btnConfig.setVisibility(View.GONE);
        }

        // Drag Handle
        holder.dragHandle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                if (listener != null) listener.onStartDrag(holder);
            }
            return false;
        });
    }

    private void updateSizeButtons(ControlViewHolder holder, BaseWidget.WidgetSize size) {
        int selectedBg = Color.parseColor("#FF2196F3"); // Blue
        int unselectedBg = Color.TRANSPARENT;
        int selectedTextColor = Color.WHITE;
        int unselectedTextColor = Color.parseColor("#AAAAAA");
        
        // Small
        boolean isSmall = (size == BaseWidget.WidgetSize.SMALL);
        holder.btnS.setBackgroundColor(isSmall ? selectedBg : unselectedBg);
        holder.btnS.setTextColor(isSmall ? selectedTextColor : unselectedTextColor);
        
        // Medium
        boolean isMedium = (size == BaseWidget.WidgetSize.MEDIUM);
        holder.btnM.setBackgroundColor(isMedium ? selectedBg : unselectedBg);
        holder.btnM.setTextColor(isMedium ? selectedTextColor : unselectedTextColor);

        // Large
        boolean isLarge = (size == BaseWidget.WidgetSize.LARGE);
        holder.btnL.setBackgroundColor(isLarge ? selectedBg : unselectedBg);
        holder.btnL.setTextColor(isLarge ? selectedTextColor : unselectedTextColor);
    }

    @Override
    public int getItemCount() {
        return widgets.size();
    }

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
    }

    static class ControlViewHolder extends RecyclerView.ViewHolder {
        public final ImageView dragHandle;
        public final TextView nameText;
        public final TextView btnS, btnM, btnL;
        public final ImageView btnDelete, btnConfig;

        public ControlViewHolder(@NonNull View itemView) {
            super(itemView);
            dragHandle = itemView.findViewById(R.id.drag_handle);
            nameText = itemView.findViewById(R.id.widget_name);
            btnS = itemView.findViewById(R.id.btn_size_s);
            btnM = itemView.findViewById(R.id.btn_size_m);
            btnL = itemView.findViewById(R.id.btn_size_l);
            btnDelete = itemView.findViewById(R.id.btn_delete);
            btnConfig = itemView.findViewById(R.id.btn_config);
        }
    }
}
