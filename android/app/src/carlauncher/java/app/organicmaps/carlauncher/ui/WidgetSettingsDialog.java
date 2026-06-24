package app.organicmaps.carlauncher.ui;

import android.app.Dialog;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import app.organicmaps.carlauncher.widgets.BaseWidget;
import app.organicmaps.carlauncher.widgets.WidgetManager;

import java.util.List;

/**
 * Widget ayarlari dialog.
 * Widget gorunurluk ve siralama ayarlari.
 */
public class WidgetSettingsDialog extends Dialog {

    private final WidgetManager widgetManager;
    private final Runnable onSaveCallback;
    private LinearLayout widgetListContainer;

    public WidgetSettingsDialog(@NonNull Context context,
            @NonNull WidgetManager widgetManager,
            @NonNull Runnable onSaveCallback) {
        super(context);
        this.widgetManager = widgetManager;
        this.onSaveCallback = onSaveCallback;

        setupDialog();
    }

    private void setupDialog() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Ana layout
        LinearLayout mainLayout = new LinearLayout(getContext());
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(0xFF1A1A1A);
        mainLayout.setPadding(24, 24, 24, 24);

        // Baslik
        TextView title = new TextView(getContext());
        title.setText("Widget Ayarlari");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 24);
        mainLayout.addView(title);

        // Widget listesi (scrollable)
        ScrollView scrollView = new ScrollView(getContext());
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f));

        widgetListContainer = new LinearLayout(getContext());
        widgetListContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(widgetListContainer);
        mainLayout.addView(scrollView);

        // Widget'lari listele
        populateWidgetList();

        // Button container
        LinearLayout buttonContainer = new LinearLayout(getContext());
        buttonContainer.setOrientation(LinearLayout.HORIZONTAL);
        buttonContainer.setGravity(Gravity.CENTER);
        buttonContainer.setPadding(0, 24, 0, 0);

        // Kaydet button
        Button saveButton = new Button(getContext());
        saveButton.setText("Kaydet");
        saveButton.setTextColor(0xFFFFFFFF);
        saveButton.setBackgroundColor(0xFF4CAF50);
        saveButton.setPadding(32, 16, 32, 16);
        saveButton.setOnClickListener(v -> {
            saveWidgetSettings();
            dismiss();
        });

        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        saveParams.setMargins(0, 0, 8, 0);
        buttonContainer.addView(saveButton, saveParams);

        // Iptal button
        Button cancelButton = new Button(getContext());
        cancelButton.setText("Iptal");
        cancelButton.setTextColor(0xFFFFFFFF);
        cancelButton.setBackgroundColor(0xFF757575);
        cancelButton.setPadding(32, 16, 32, 16);
        cancelButton.setOnClickListener(v -> dismiss());

        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        cancelParams.setMargins(8, 0, 0, 0);
        buttonContainer.addView(cancelButton, cancelParams);

        mainLayout.addView(buttonContainer);

        setContentView(mainLayout);

        // Dialog size
        Window window = getWindow();
        if (window != null) {
            window.setLayout(
                    (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.8),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void populateWidgetList() {
        widgetListContainer.removeAllViews();

        List<BaseWidget> allWidgets = widgetManager.getAllWidgets();

        for (int i = 0; i < allWidgets.size(); i++) {
            BaseWidget widget = allWidgets.get(i);
            View widgetItem = createWidgetItem(widget, i);
            widgetListContainer.addView(widgetItem);
        }
    }

    private View createWidgetItem(BaseWidget widget, int index) {
        LinearLayout itemLayout = new LinearLayout(getContext());
        itemLayout.setOrientation(LinearLayout.HORIZONTAL);
        itemLayout.setGravity(Gravity.CENTER_VERTICAL);
        itemLayout.setPadding(16, 12, 16, 12);
        itemLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // Yukaricevir button (ilk widget disinda)
        ImageButton btnUp = new ImageButton(getContext());
        btnUp.setImageResource(android.R.drawable.arrow_up_float);
        btnUp.setBackgroundColor(0x00000000);
        btnUp.setEnabled(index > 0);
        btnUp.setAlpha(index > 0 ? 1.0f : 0.3f);
        btnUp.setOnClickListener(v -> moveWidget(index, index - 1));

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(48, 48);
        btnParams.setMargins(0, 0, 8, 0);
        itemLayout.addView(btnUp, btnParams);

        // Asagi button (son widget disinda)
        ImageButton btnDown = new ImageButton(getContext());
        btnDown.setImageResource(android.R.drawable.arrow_down_float);
        btnDown.setBackgroundColor(0x00000000);
        btnDown.setEnabled(index < widgetManager.getAllWidgets().size() - 1);
        btnDown.setAlpha(index < widgetManager.getAllWidgets().size() - 1 ? 1.0f : 0.3f);
        btnDown.setOnClickListener(v -> moveWidget(index, index + 1));

        btnParams = new LinearLayout.LayoutParams(48, 48);
        btnParams.setMargins(0, 0, 8, 0);
        itemLayout.addView(btnDown, btnParams);

        // Widget ismi
        TextView widgetName = new TextView(getContext());
        widgetName.setText(widget.getTitle());
        widgetName.setTextColor(0xFFFFFFFF);
        widgetName.setTextSize(16);

        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        itemLayout.addView(widgetName, nameParams);

        // Checkbox (gorunum)
        CheckBox checkBox = new CheckBox(getContext());
        checkBox.setChecked(widget.isVisible());
        checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            widget.setVisible(isChecked);
        });
        itemLayout.addView(checkBox);

        return itemLayout;
    }

    private void moveWidget(int fromIndex, int toIndex) {
        widgetManager.moveWidget(fromIndex, toIndex);
        populateWidgetList(); // Listeyi yenile
    }

    private void saveWidgetSettings() {
        widgetManager.saveWidgetConfig();
        if (onSaveCallback != null) {
            onSaveCallback.run();
        }
    }
}
