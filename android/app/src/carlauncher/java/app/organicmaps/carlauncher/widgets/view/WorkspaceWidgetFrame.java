package app.organicmaps.carlauncher.widgets.view;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;

import app.organicmaps.carlauncher.widgets.BaseWidget;
import app.organicmaps.carlauncher.widgets.WidgetManager;

/**
 * Premium Duzenleme Modu icin Widget Sarmalayici Sinif.
 * Duzenleme modundayken titreme animasyonu, kenar cizgileri,
 * tasima (Drag & Drop), boyutlandirma (Resize), ayar ve silme butonlari sunar.
 * Butun kontrol butonlari Canvas ile jilet keskinliginde programatik cizilmistir.
 * Kod icerisinde kesinlikle Turkce karakter kullanilmamistir.
 */
public class WorkspaceWidgetFrame extends FrameLayout {

    private final BaseWidget widget;
    private final WorkspaceCellLayout parentLayout;
    private final FragmentManager fragmentManager;
    private final Runnable onWidgetsChanged;

    private boolean isEditMode = false;
    private ObjectAnimator shakeAnimator;

    private FrameLayout overlayContainer;
    private DragHandleView dragHandle;
    private WhiteDotHandleView leftHandle;
    private WhiteDotHandleView rightHandle;
    private WhiteDotHandleView topHandle;
    private WhiteDotHandleView bottomHandle;
    private DeleteButtonView deleteBtn;
    private ConfigButtonView configBtn;
    private DoneButtonView doneBtn;

    private Paint borderPaint;
    private RectF borderRect;

    public WorkspaceWidgetFrame(@NonNull Context context, 
                                @NonNull BaseWidget widget, 
                                @NonNull WorkspaceCellLayout parentLayout,
                                @NonNull FragmentManager fragmentManager,
                                @NonNull Runnable onWidgetsChanged) {
        super(context);
        this.widget = widget;
        this.parentLayout = parentLayout;
        this.fragmentManager = fragmentManager;
        this.onWidgetsChanged = onWidgetsChanged;
        
        init(context);
    }

    private void init(Context context) {
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);

        // Kenar cizimi icin Paint nesnesi (Duz Ince Beyaz)
        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dpToPx(1.5f));
        borderPaint.setColor(Color.WHITE); // Duz Beyaz

        borderRect = new RectF();

        // Edit Arayuzu Butonlari ve Tutamaclari
        overlayContainer = new FrameLayout(context);
        overlayContainer.setClipChildren(false);
        overlayContainer.setClipToPadding(false);
        overlayContainer.setVisibility(GONE);
        overlayContainer.setClickable(true); // Dokunmalari asil widget'a gecirmeyip yutar
        overlayContainer.setFocusable(true);

        int handleSize = dpToPx(32); // 32dp dokunma alani

        // Sol Tutamac
        leftHandle = new WhiteDotHandleView(context);
        LayoutParams leftParams = new LayoutParams(handleSize, handleSize);
        leftParams.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
        leftParams.leftMargin = -dpToPx(16); // Tam sÄ±nÄ±r Ã§izgisine ortala
        leftHandle.setLayoutParams(leftParams);
        leftHandle.setOnTouchListener(new OnTouchListener() {
            private float initialX;
            private int initialCellX;
            private int initialSpanX;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = event.getRawX();
                        initialCellX = widget.getCellX();
                        initialSpanX = widget.getSpanX();
                        getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - initialX;
                        int cellSize = getCellSize();
                        if (cellSize > 0) {
                            int gridDeltaX = Math.round(deltaX / cellSize);
                            int maxPossibleCellX = initialCellX + initialSpanX - 3;
                            int newCellX = Math.max(0, Math.min(maxPossibleCellX, initialCellX + gridDeltaX));
                            int newSpanX = initialCellX + initialSpanX - newCellX;
                            if (newCellX != widget.getCellX() || newSpanX != widget.getSpanX()) {
                                updateWidgetSize(newSpanX, widget.getSpanY(), newCellX, widget.getCellY());
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        onResizeCompleted();
                        return true;
                }
                return false;
            }
        });
        overlayContainer.addView(leftHandle);

        // Sag Tutamac
        rightHandle = new WhiteDotHandleView(context);
        LayoutParams rightParams = new LayoutParams(handleSize, handleSize);
        rightParams.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        rightParams.rightMargin = -dpToPx(16);
        rightHandle.setLayoutParams(rightParams);
        rightHandle.setOnTouchListener(new OnTouchListener() {
            private float initialX;
            private int initialSpanX;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = event.getRawX();
                        initialSpanX = widget.getSpanX();
                        getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - initialX;
                        int cellSize = getCellSize();
                        int colCount = getColCount();
                        if (cellSize > 0) {
                            int gridDeltaX = Math.round(deltaX / cellSize);
                            int newSpanX = Math.max(3, Math.min(colCount - widget.getCellX(), initialSpanX + gridDeltaX));
                            if (newSpanX != widget.getSpanX()) {
                                updateWidgetSize(newSpanX, widget.getSpanY(), widget.getCellX(), widget.getCellY());
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        onResizeCompleted();
                        return true;
                }
                return false;
            }
        });
        overlayContainer.addView(rightHandle);

        // Ust Tutamac
        topHandle = new WhiteDotHandleView(context);
        LayoutParams topParams = new LayoutParams(handleSize, handleSize);
        topParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        topParams.topMargin = -dpToPx(16);
        topHandle.setLayoutParams(topParams);
        topHandle.setOnTouchListener(new OnTouchListener() {
            private float initialY;
            private int initialCellY;
            private int initialSpanY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialY = event.getRawY();
                        initialCellY = widget.getCellY();
                        initialSpanY = widget.getSpanY();
                        getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float deltaY = event.getRawY() - initialY;
                        int cellSize = getCellSize();
                        if (cellSize > 0) {
                            int gridDeltaY = Math.round(deltaY / cellSize);
                            int maxPossibleCellY = initialCellY + initialSpanY - 3;
                            int newCellY = Math.max(0, Math.min(maxPossibleCellY, initialCellY + gridDeltaY));
                            int newSpanY = initialCellY + initialSpanY - newCellY;
                            if (newCellY != widget.getCellY() || newSpanY != widget.getSpanY()) {
                                updateWidgetSize(widget.getSpanX(), newSpanY, widget.getCellX(), newCellY);
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        onResizeCompleted();
                        return true;
                }
                return false;
            }
        });
        overlayContainer.addView(topHandle);

        // Alt Tutamac
        bottomHandle = new WhiteDotHandleView(context);
        LayoutParams bottomParams = new LayoutParams(handleSize, handleSize);
        bottomParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        bottomParams.bottomMargin = -dpToPx(16);
        bottomHandle.setLayoutParams(bottomParams);
        bottomHandle.setOnTouchListener(new OnTouchListener() {
            private float initialY;
            private int initialSpanY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialY = event.getRawY();
                        initialSpanY = widget.getSpanY();
                        getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float deltaY = event.getRawY() - initialY;
                        int cellSize = getCellSize();
                        int rowCount = getRowCount();
                        if (cellSize > 0) {
                            int gridDeltaY = Math.round(deltaY / cellSize);
                            int newSpanY = Math.max(3, Math.min(rowCount - widget.getCellY(), initialSpanY + gridDeltaY));
                            if (newSpanY != widget.getSpanY()) {
                                updateWidgetSize(widget.getSpanX(), newSpanY, widget.getCellX(), widget.getCellY());
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        onResizeCompleted();
                        return true;
                }
                return false;
            }
        });
        overlayContainer.addView(bottomHandle);

        // 1x1 Uygulama kisayollari icin boyutlandirma butonlarini gizle
        if (widget.getId() != null && widget.getId().startsWith("shortcut_")) {
            leftHandle.setVisibility(GONE);
            rightHandle.setVisibility(GONE);
            topHandle.setVisibility(GONE);
            bottomHandle.setVisibility(GONE);
        }

        // Ust Sag Kose Kontrol Paneli (Ayarlar ve Silme Yan Yana)
        LinearLayout topControls = new LinearLayout(context);
        topControls.setOrientation(LinearLayout.HORIZONTAL);
        LayoutParams topControlsParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        topControlsParams.gravity = Gravity.TOP | Gravity.RIGHT;
        topControlsParams.topMargin = dpToPx(4);
        topControlsParams.rightMargin = dpToPx(4);
        topControls.setLayoutParams(topControlsParams);

        int btnSize = dpToPx(28); // Zarif ve ergonomik boyut (28dp)

        // 3. Config Button (Ayarlar Butonu)
        configBtn = new ConfigButtonView(context);
        LinearLayout.LayoutParams configLp = new LinearLayout.LayoutParams(btnSize, btnSize);
        configLp.rightMargin = dpToPx(4);
        configBtn.setLayoutParams(configLp);
        configBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (widget.isConfigurable()) {
                    widget.openConfig(fragmentManager);
                }
            }
        });
        topControls.addView(configBtn);

        // 4. Delete Button (Kapat/Sil Butonu)
        deleteBtn = new DeleteButtonView(context);
        LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(btnSize, btnSize);
        deleteBtn.setLayoutParams(deleteLp);
        deleteBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                widget.setVisible(false);
                WidgetManager.getInstance(getContext()).saveWidgetConfig();
                
                // Sayfadaki diger gorunur widget'lari kontrol et (Son widget silindiyse Duzenleme Modunu kapat)
                java.util.List<BaseWidget> list = WidgetManager.getInstance(getContext()).getAllWidgets();
                boolean hasVisibleWidgetsOnPage = false;
                for (BaseWidget w : list) {
                    if (w.isVisible() && w.getPageIndex() == widget.getPageIndex()) {
                        hasVisibleWidgetsOnPage = true;
                        break;
                    }
                }
                if (!hasVisibleWidgetsOnPage) {
                    app.organicmaps.carlauncher.widgets.WorkspacePageAdapter.isEditMode = false;
                }
                
                if (onWidgetsChanged != null) {
                    onWidgetsChanged.run();
                }
                Toast.makeText(getContext(), widget.getTitle() + " kaldirildi.", Toast.LENGTH_SHORT).show();
            }
        });
        topControls.addView(deleteBtn);

        // 5. Done Button (Duzenlemeyi Bitir / Tamam Butonu - Sol Alt Kose)
        doneBtn = new DoneButtonView(context);
        int doneSize = dpToPx(28); // Zarif ve ergonomik boyut (28dp)
        LayoutParams doneParams = new LayoutParams(doneSize, doneSize);
        doneParams.gravity = Gravity.BOTTOM | Gravity.LEFT;
        doneBtn.setLayoutParams(doneParams);
        doneBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                v.post(() -> {
                    app.organicmaps.carlauncher.widgets.WorkspacePageAdapter.exitEditMode();
                    Toast.makeText(getContext(), "Duzenlemeler Kaydedildi.", Toast.LENGTH_SHORT).show();
                });
            }
        });
        overlayContainer.addView(doneBtn);

        overlayContainer.addView(topControls);

        // Govdeden Premium Tasima (Drag & Drop) Dinleyicisi
        overlayContainer.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    float x = event.getX();
                    float y = event.getY();
                    
                    // Butonlarin sinirlari icinde mi kontrol et. Eger butonlara dokunulduysa dokunmayi yutma.
                    boolean inTopControls = x >= (getWidth() - topControls.getWidth() - dpToPx(8)) 
                            && y <= (topControls.getHeight() + dpToPx(8));
                            
                    boolean inDoneBtn = x <= (doneBtn.getWidth() + dpToPx(8)) 
                            && y >= (getHeight() - doneBtn.getHeight() - dpToPx(8));
                            
                    // Yeni tutamaclarin alanlari
                    boolean inLeftHandle = false;
                    boolean inRightHandle = false;
                    boolean inTopHandle = false;
                    boolean inBottomHandle = false;
                    
                    int handleHitSize = dpToPx(24); // Dokunma hassasiyet alani (24dp)
                    if (leftHandle != null && leftHandle.getVisibility() == VISIBLE) {
                        inLeftHandle = x <= handleHitSize && Math.abs(y - getHeight() / 2f) <= handleHitSize;
                        inRightHandle = x >= (getWidth() - handleHitSize) && Math.abs(y - getHeight() / 2f) <= handleHitSize;
                        inTopHandle = y <= handleHitSize && Math.abs(x - getWidth() / 2f) <= handleHitSize;
                        inBottomHandle = y >= (getHeight() - handleHitSize) && Math.abs(x - getWidth() / 2f) <= handleHitSize;
                    }
                    
                    if (!inTopControls && !inDoneBtn && !inLeftHandle && !inRightHandle && !inTopHandle && !inBottomHandle) {
                        ClipData data = ClipData.newPlainText("widget_id", widget.getId());
                        DragShadowBuilder shadowBuilder = new DragShadowBuilder(WorkspaceWidgetFrame.this);
                        startDragAndDrop(data, shadowBuilder, WorkspaceWidgetFrame.this, 0);
                        setAlpha(0.3f);
                        return true;
                    }
                }
                return false;
            }
        });
        addView(overlayContainer, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public BaseWidget getWidget() {
        return widget;
    }

    public void setWidgetView(View widgetView) {
        // Temizlik: overlayContainer disindaki eski widget view'larini kaldirir
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (child != overlayContainer) {
                removeViewAt(i);
            }
        }
        // Widget gorunumunu 0. indekse ekle (en alta)
        addView(widgetView, 0, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // overlayContainer'in her zaman en ustte oldugundan emin ol
        overlayContainer.bringToFront();
    }

    public void setEditMode(boolean editMode) {
        this.isEditMode = editMode;
        if (editMode) {
            overlayContainer.setVisibility(VISIBLE);
            overlayContainer.setElevation(dpToPx(30));
            overlayContainer.bringToFront();
            configBtn.setVisibility(widget.isConfigurable() ? VISIBLE : GONE);
            
            // Tutamaclari kisayollar haric goster
            boolean isShortcut = widget.getId() != null && widget.getId().startsWith("shortcut_");
            int handleVisibility = isShortcut ? GONE : VISIBLE;
            if (leftHandle != null) leftHandle.setVisibility(handleVisibility);
            if (rightHandle != null) rightHandle.setVisibility(handleVisibility);
            if (topHandle != null) topHandle.setVisibility(handleVisibility);
            if (bottomHandle != null) bottomHandle.setVisibility(handleVisibility);
            
            if (doneBtn != null) {
                doneBtn.setVisibility(VISIBLE);
            }
            startShakeAnimation();
        } else {
            overlayContainer.setVisibility(GONE);
            overlayContainer.setElevation(0);
            
            if (leftHandle != null) leftHandle.setVisibility(GONE);
            if (rightHandle != null) rightHandle.setVisibility(GONE);
            if (topHandle != null) topHandle.setVisibility(GONE);
            if (bottomHandle != null) bottomHandle.setVisibility(GONE);
            
            stopShakeAnimation();
            setAlpha(1.0f);
        }
        invalidate();
    }

    private void startShakeAnimation() {
        if (shakeAnimator == null) {
            shakeAnimator = ObjectAnimator.ofFloat(this, "rotation", -0.6f, 0.6f);
            shakeAnimator.setDuration(160);
            shakeAnimator.setRepeatCount(ValueAnimator.INFINITE);
            shakeAnimator.setRepeatMode(ValueAnimator.REVERSE);
        }
        if (!shakeAnimator.isRunning()) {
            shakeAnimator.start();
        }
    }

    private void stopShakeAnimation() {
        if (shakeAnimator != null && shakeAnimator.isRunning()) {
            shakeAnimator.cancel();
            setRotation(0);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (isEditMode) {
            // Neon cerceve ciz
            int padding = dpToPx(2);
            borderRect.set(padding, padding, getWidth() - padding, getHeight() - padding);
            canvas.drawRoundRect(borderRect, dpToPx(8), dpToPx(8), borderPaint);
        }
    }

    private boolean canWidgetFitAt(int pageIndex, int cellX, int cellY, int spanX, int spanY) {
        int paddingLeft = parentLayout.getPaddingLeft();
        int paddingTop = parentLayout.getPaddingTop();
        int usableWidth = parentLayout.getWidth() - paddingLeft - parentLayout.getPaddingRight();
        int usableHeight = parentLayout.getHeight() - paddingTop - parentLayout.getPaddingBottom();

        int cellSize = WorkspaceCellLayout.getCellSize(getContext(), usableWidth, usableHeight);
        int colCount = WorkspaceCellLayout.getColCount(getContext(), usableWidth, cellSize);
        int rowCount = WorkspaceCellLayout.getRowCount(getContext(), usableHeight, cellSize);

        if (cellX < 0 || cellX + spanX > colCount || cellY < 0 || cellY + spanY > rowCount) {
            return false;
        }

        java.util.List<BaseWidget> list = WidgetManager.getInstance(getContext()).getAllWidgets();
        boolean[][] occupied = new boolean[colCount][rowCount];
        for (BaseWidget w : list) {
            if (w != widget && w.isVisible() && w.getPageIndex() == pageIndex) {
                int cx = w.getCellX();
                int cy = w.getCellY();
                int sx = w.getSpanX();
                int sy = w.getSpanY();
                if (cx >= 0 && cx + sx <= colCount && cy >= 0 && cy + sy <= rowCount) {
                    for (int x = cx; x < cx + sx; x++) {
                        for (int y = cy; y < cy + sy; y++) {
                            occupied[x][y] = true;
                        }
                    }
                }
            }
        }

        for (int x = cellX; x < cellX + spanX; x++) {
            for (int y = cellY; y < cellY + spanY; y++) {
                if (occupied[x][y]) {
                    return false;
                }
            }
        }
        return true;
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private int dpToPx(float dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    // ==========================================
    // PREMIUM CANVAS TABANLI CUSTOM VIEW SINIFLARI
    // ==========================================

    /**
     * Kirmizi Carpi (X) Kapat/Sil Butonu.
     */
    private static class DeleteButtonView extends View {
        private final Paint bgPaint;
        private final Paint iconPaint;
        private final float density;

        public DeleteButtonView(Context context) {
            super(context);
            density = context.getResources().getDisplayMetrics().density;
            setClickable(true);
            setFocusable(true);

            bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setStyle(Paint.Style.FILL);
            bgPaint.setColor(0xEE222222); // Koyu gri yari saydam arkaplan

            iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            iconPaint.setStyle(Paint.Style.STROKE);
            iconPaint.setStrokeWidth(2.0f * density);
            iconPaint.setColor(0xFFFF3333); // Neon Kirmizi
            iconPaint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = 10f * density; // 20dp cap

            // Daire arkaplani ciz
            canvas.drawCircle(cx, cy, radius, bgPaint);

            // Carpi isaretini ciz (X)
            float size = 3.5f * density;
            canvas.drawLine(cx - size, cy - size, cx + size, cy + size, iconPaint);
            canvas.drawLine(cx + size, cy - size, cx - size, cy + size, iconPaint);
        }
    }

    /**
     * Premium Modern Ayarlar (âš™ï¸) Butonu.
     */
    private static class ConfigButtonView extends View {
        private final Paint bgPaint;
        private final Paint iconPaint;
        private final float density;

        public ConfigButtonView(Context context) {
            super(context);
            density = context.getResources().getDisplayMetrics().density;
            setClickable(true);
            setFocusable(true);

            bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setStyle(Paint.Style.FILL);
            bgPaint.setColor(0xEE222222);

            iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            iconPaint.setStyle(Paint.Style.STROKE);
            iconPaint.setStrokeWidth(1.8f * density);
            iconPaint.setColor(0xFFFFB300); // Premium Sari/Turuncu
            iconPaint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = 10f * density;

            canvas.drawCircle(cx, cy, radius, bgPaint);

            // Modern Ayar/Slider simgesi (2 yatay cizgi ve uzerinde kucuk daireler)
            float lineY1 = cy - 2.5f * density;
            float lineY2 = cy + 2.5f * density;
            float startX = cx - 4f * density;
            float endX = cx + 4f * density;

            // Cizgiler
            canvas.drawLine(startX, lineY1, endX, lineY1, iconPaint);
            canvas.drawLine(startX, lineY2, endX, lineY2, iconPaint);

            // Kaydirici daireler
            Paint circlePaint = new Paint(iconPaint);
            circlePaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx - 1.5f * density, lineY1, 1.8f * density, circlePaint);
            canvas.drawCircle(cx + 1.5f * density, lineY2, 1.8f * density, circlePaint);
        }
    }

    /**
     * Launcher3 Tarzi 6 Noktali Tasima (Drag) Butonu.
     */
    private static class DragHandleView extends View {
        private final Paint bgPaint;
        private final Paint dotPaint;
        private final float density;

        public DragHandleView(Context context) {
            super(context);
            density = context.getResources().getDisplayMetrics().density;
            setClickable(true);
            setFocusable(true);

            bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setStyle(Paint.Style.FILL);
            bgPaint.setColor(0xEE222222);

            dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            dotPaint.setStyle(Paint.Style.FILL);
            dotPaint.setColor(Color.WHITE);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = 18f * density; // 36dp cap

            canvas.drawCircle(cx, cy, radius, bgPaint);

            // 6 surukleme noktasini cizelim
            float dotRadius = 2.2f * density;
            float spacingX = 5f * density;
            float spacingY = 5f * density;

            float x1 = cx - spacingX / 2f;
            float x2 = cx + spacingX / 2f;
            float y1 = cy - spacingY;
            float y2 = cy;
            float y3 = cy + spacingY;

            canvas.drawCircle(x1, y1, dotRadius, dotPaint);
            canvas.drawCircle(x2, y1, dotRadius, dotPaint);
            canvas.drawCircle(x1, y2, dotRadius, dotPaint);
            canvas.drawCircle(x2, y2, dotRadius, dotPaint);
            canvas.drawCircle(x1, y3, dotRadius, dotPaint);
            canvas.drawCircle(x2, y3, dotRadius, dotPaint);
        }
    }

    private int getCellSize() {
        int paddingLeft = parentLayout.getPaddingLeft();
        int usableWidth = parentLayout.getWidth() - paddingLeft - parentLayout.getPaddingRight();
        int usableHeight = parentLayout.getHeight() - parentLayout.getPaddingTop() - parentLayout.getPaddingBottom();
        return WorkspaceCellLayout.getCellSize(getContext(), usableWidth, usableHeight);
    }

    private int getColCount() {
        int paddingLeft = parentLayout.getPaddingLeft();
        int usableWidth = parentLayout.getWidth() - paddingLeft - parentLayout.getPaddingRight();
        int cellSize = getCellSize();
        return WorkspaceCellLayout.getColCount(getContext(), usableWidth, cellSize);
    }

    private int getRowCount() {
        int paddingTop = parentLayout.getPaddingTop();
        int usableHeight = parentLayout.getHeight() - paddingTop - parentLayout.getPaddingBottom();
        int cellSize = getCellSize();
        return WorkspaceCellLayout.getRowCount(getContext(), usableHeight, cellSize);
    }

    private void updateWidgetSize(int spanX, int spanY, int cellX, int cellY) {
        if (canWidgetFitAt(widget.getPageIndex(), cellX, cellY, spanX, spanY)) {
            widget.setSpanX(spanX);
            widget.setSpanY(spanY);
            widget.setCellX(cellX);
            widget.setCellY(cellY);

            // WorkspaceCellLayout LayoutParams guncelle
            WorkspaceCellLayout.LayoutParams lp = (WorkspaceCellLayout.LayoutParams) getLayoutParams();
            lp.cellX = cellX;
            lp.cellY = cellY;
            lp.spanX = spanX;
            lp.spanY = spanY;
            setLayoutParams(lp);
            parentLayout.requestLayout();
        }
    }

    private void onResizeCompleted() {
        WidgetManager.getInstance(getContext()).saveWidgetConfig();
        if (onWidgetsChanged != null) {
            onWidgetsChanged.run();
        }
    }

    /**
     * Premium Modern Beyaz Resize Dairesi.
     */
    private static class WhiteDotHandleView extends View {
        private final Paint bgPaint;
        private final Paint borderPaint;
        private final float density;

        public WhiteDotHandleView(Context context) {
            super(context);
            density = context.getResources().getDisplayMetrics().density;
            setClickable(true);
            setFocusable(true);

            bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setStyle(Paint.Style.FILL);
            bgPaint.setColor(Color.WHITE);

            borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(1.5f * density);
            borderPaint.setColor(0xFF00E5FF); // Neon Turkuaz
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = 7f * density; // 14dp cap

            // Golge efekti
            Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            shadowPaint.setStyle(Paint.Style.FILL);
            shadowPaint.setColor(0x33000000);
            canvas.drawCircle(cx, cy, radius + 2f * density, shadowPaint);

            // Beyaz daire
            canvas.drawCircle(cx, cy, radius, bgPaint);

            // Neon kenarlik
            canvas.drawCircle(cx, cy, radius, borderPaint);
        }
    }

    /**
     * Yesil Onay (Done) Butonu.
     * Tiklandiginda edit modundan cikar.
     * Kod icerisinde kesinlikle Turkce karakter kullanilmamistir.
     */
    private static class DoneButtonView extends View {
        private final Paint bgPaint;
        private final Paint iconPaint;
        private final float density;
        private boolean isPressed = false;

        public DoneButtonView(Context context) {
            super(context);
            density = context.getResources().getDisplayMetrics().density;
            setClickable(true);
            setFocusable(true);

            bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setStyle(Paint.Style.FILL);
            bgPaint.setColor(0xEE222222); // Koyu gri yari saydam arkaplan

            iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            iconPaint.setStyle(Paint.Style.STROKE);
            iconPaint.setStrokeWidth(2.2f * density); // Daha net ve kalin cizgi
            iconPaint.setColor(0xFF00E676); // Neon Yesil
            iconPaint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isPressed = true;
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                    if (isPressed) {
                        isPressed = false;
                        invalidate();
                        performClick(); // Tiklama olayini tetikle
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    isPressed = false;
                    invalidate();
                    return true;
            }
            return super.onTouchEvent(event);
        }

        @Override
        public boolean performClick() {
            return super.performClick();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = 10.5f * density; // 21dp cap

            // Basildiginda renk ve boyut degisimi (Premium geri bildirim)
            if (isPressed) {
                bgPaint.setColor(0xFF00E676); // Arkaplan yesil olur
                iconPaint.setColor(0xFF222222); // Simge koyu gri olur
                radius = 9f * density; // Kuculme efekti
            } else {
                bgPaint.setColor(0xEE222222);
                iconPaint.setColor(0xFF00E676);
            }

            // Daire arkaplani ciz
            canvas.drawCircle(cx, cy, radius, bgPaint);

            // Onay (Checkmark) isaretini ciz
            float x1 = cx - 3.5f * density;
            float y1 = cy - 0.3f * density;
            float x2 = cx - 1f * density;
            float y2 = cy + 2.3f * density;
            float x3 = cx + 3.8f * density;
            float y3 = cy - 2.3f * density;

            canvas.drawLine(x1, y1, x2, y2, iconPaint);
            canvas.drawLine(x2, y2, x3, y3, iconPaint);
        }
    }
}
