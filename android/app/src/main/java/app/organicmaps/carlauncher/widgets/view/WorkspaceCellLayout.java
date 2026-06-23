package app.organicmaps.carlauncher.widgets.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.DragEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import app.organicmaps.carlauncher.widgets.BaseWidget;
import app.organicmaps.carlauncher.widgets.WidgetManager;

/**
 * Cok Sayfali Premium Workspace icin Ozel 4x4 Izgara Yerlesim Sinifi (CellLayout).
 * Ekran alanini milimetrik olarak 4x4 esit hucreye boler ve widget'lari konumlandirir.
 * Sürükle-Bırak (Drag & Drop) sirasinda kılavuz cizgileri ve yeşil/kırmızı hedef alan maskeleri cizer.
 * Kod icerisinde kesinlikle Turkce karakter kullanilmamistir.
 */
public class WorkspaceCellLayout extends ViewGroup implements View.OnDragListener {

    public static final int COL_COUNT = 8;
    public static final int ROW_COUNT = 4;

    public static int getCellSize(Context context, int usableWidth, int usableHeight) {
        if (context == null) return 80;
        return Math.min(usableWidth, usableHeight) / 12;
    }

    public static int getColCount(Context context, int usableWidth, int cellSize) {
        if (cellSize <= 0) return 12;
        return usableWidth / cellSize;
    }

    public static int getRowCount(Context context, int usableHeight, int cellSize) {
        if (cellSize <= 0) return 12;
        return usableHeight / cellSize;
    }

    private int marginPx = 0;
    
    // Drag & Drop Cizim Degiskenleri
    private boolean isDragging = false;
    private int targetCellX = -1;
    private int targetCellY = -1;
    private int targetSpanX = 1;
    private int targetSpanY = 1;
    private boolean isTargetValid = false;

    private Paint gridLinePaint;
    private Paint maskPaint;
    private RectF maskRect;
    
    private Runnable onWidgetsChangedListener;
    private int pageIndex = 0;
    private long lastPageScrollTime = 0;

    public WorkspaceCellLayout(Context context) {
        super(context);
        init(context);
    }

    public WorkspaceCellLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public WorkspaceCellLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setWillNotDraw(false); // onDraw tetiklenmesi icin sart
        setClipChildren(false);
        setClipToPadding(false);
        setOnDragListener(this);
        setClickable(true); // Tiklanabilir yaparak bos sayfada edit modundan cikisi garanti eder
        setLongClickable(true);

        // Hucreler arasi default margin (6dp)
        float density = context.getResources().getDisplayMetrics().density;
        marginPx = Math.round(6 * density);

        // Premium Kenar Padding'i (12dp sol/sag, 8dp ust/alt)
        int paddingSide = Math.round(12 * density);
        int paddingTopBottom = Math.round(8 * density);
        setPadding(paddingSide, paddingTopBottom, paddingSide, paddingTopBottom);

        // Grid Kilavuz Cizgisi Boyasi (Neon Kesikli Beyaz)
        gridLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridLinePaint.setStyle(Paint.Style.STROKE);
        gridLinePaint.setStrokeWidth(dpToPx(1));
        gridLinePaint.setColor(0x22FFFFFF); // Hafif yari saydam beyaz

        // Hedef Alan Maske Boyasi
        maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        maskPaint.setStyle(Paint.Style.FILL);
        
        maskRect = new RectF();
    }

    public void setPageIndex(int pageIndex) {
        this.pageIndex = pageIndex;
    }

    public void setOnWidgetsChangedListener(Runnable listener) {
        this.onWidgetsChangedListener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int paddingLeft = getPaddingLeft();
        int paddingRight = getPaddingRight();
        int paddingTop = getPaddingTop();
        int paddingBottom = getPaddingBottom();

        int usableWidth = widthSize - paddingLeft - paddingRight;
        int usableHeight = heightSize - paddingTop - paddingBottom;

        int cellSize = getCellSize(getContext(), usableWidth, usableHeight);

        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                LayoutParams lp = (LayoutParams) child.getLayoutParams();
                
                int childWidth = cellSize * lp.spanX - (2 * marginPx);
                int childHeight = cellSize * lp.spanY - (2 * marginPx);

                if (childWidth < 0) childWidth = 0;
                if (childHeight < 0) childHeight = 0;

                int childWidthSpec = MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY);
                int childHeightSpec = MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY);
                
                child.measure(childWidthSpec, childHeightSpec);
            }
        }

        setMeasuredDimension(widthSize, heightSize);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int paddingLeft = getPaddingLeft();
        int paddingTop = getPaddingTop();
        
        int usableWidth = (r - l) - paddingLeft - getPaddingRight();
        int usableHeight = (b - t) - paddingTop - getPaddingBottom();

        int cellSize = getCellSize(getContext(), usableWidth, usableHeight);
        int colCount = getColCount(getContext(), usableWidth, cellSize);
        int rowCount = getRowCount(getContext(), usableHeight, cellSize);

        int extraWidth = (usableWidth - (colCount * cellSize)) / 2;
        int extraHeight = (usableHeight - (rowCount * cellSize)) / 2;

        int startLeft = paddingLeft + extraWidth;
        int startTop = paddingTop + extraHeight;

        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                LayoutParams lp = (LayoutParams) child.getLayoutParams();

                int left = startLeft + (lp.cellX * cellSize) + marginPx;
                int top = startTop + (lp.cellY * cellSize) + marginPx;
                int right = left + (lp.spanX * cellSize) - (2 * marginPx);
                int bottom = top + (lp.spanY * cellSize) - (2 * marginPx);

                child.layout(left, top, right, bottom);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int paddingLeft = getPaddingLeft();
        int paddingTop = getPaddingTop();
        int usableWidth = getWidth() - paddingLeft - getPaddingRight();
        int usableHeight = getHeight() - paddingTop - getPaddingBottom();

        int cellSize = getCellSize(getContext(), usableWidth, usableHeight);
        int colCount = getColCount(getContext(), usableWidth, cellSize);
        int rowCount = getRowCount(getContext(), usableHeight, cellSize);

        int extraWidth = (usableWidth - (colCount * cellSize)) / 2;
        int extraHeight = (usableHeight - (rowCount * cellSize)) / 2;

        int startLeft = paddingLeft + extraWidth;
        int startTop = paddingTop + extraHeight;

        // 1. Duzenleme / Surukleme / Edit Modu sirasinda Izgara Kilavuz cizgilerini ciz
        if (isDragging || app.organicmaps.carlauncher.widgets.WorkspacePageAdapter.isEditMode) {
            // Dikey Cizgiler
            for (int i = 1; i < colCount; i++) {
                float x = startLeft + (i * cellSize);
                canvas.drawLine(x, startTop, x, startTop + (rowCount * cellSize), gridLinePaint);
            }
            // Yatay Cizgiler
            for (int i = 1; i < rowCount; i++) {
                float y = startTop + (i * cellSize);
                canvas.drawLine(startLeft, y, startLeft + (colCount * cellSize), y, gridLinePaint);
            }

            // 2. Suruklenen alanin altindaki Hedef Alan Maskesini Ciz
            if (targetCellX >= 0 && targetCellY >= 0) {
                float left = startLeft + (targetCellX * cellSize) + marginPx;
                float top = startTop + (targetCellY * cellSize) + marginPx;
                float right = left + (targetSpanX * cellSize) - (2 * marginPx);
                float bottom = top + (targetSpanY * cellSize) - (2 * marginPx);

                maskRect.set(left, top, right, bottom);

                if (isTargetValid) {
                    maskPaint.setColor(0x3300FF00); // Neon Yesil (Gecerli)
                } else {
                    maskPaint.setColor(0x33FF0000); // Neon Kirmizi (Cakisma/Gecersiz)
                }
                canvas.drawRoundRect(maskRect, dpToPx(8), dpToPx(8), maskPaint);
            }
        }
    }

    @Override
    public boolean onDrag(View v, DragEvent event) {
        WorkspaceWidgetFrame dragFrame = null;
        if (event.getLocalState() instanceof WorkspaceWidgetFrame) {
            dragFrame = (WorkspaceWidgetFrame) event.getLocalState();
        }

        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                isDragging = true;
                if (dragFrame != null) {
                    BaseWidget w = findWidgetForFrame(dragFrame);
                    if (w != null) {
                        targetSpanX = w.getSpanX();
                        targetSpanY = w.getSpanY();
                    }
                }
                invalidate();
                return true;

            case DragEvent.ACTION_DRAG_ENTERED:
                return true;

            case DragEvent.ACTION_DRAG_LOCATION:
                int pLeft = getPaddingLeft();
                int pTop = getPaddingTop();
                int uWidth = getWidth() - pLeft - getPaddingRight();
                int uHeight = getHeight() - pTop - getPaddingBottom();

                int cSize = getCellSize(getContext(), uWidth, uHeight);
                int cCount = getColCount(getContext(), uWidth, cSize);
                int rCount = getRowCount(getContext(), uHeight, cSize);

                int exWidth = (uWidth - (cCount * cSize)) / 2;
                int exHeight = (uHeight - (rCount * cSize)) / 2;

                int sLeft = pLeft + exWidth;
                int sTop = pTop + exHeight;

                if (cSize > 0) {
                    // Sayfalar arasi otomatik gecis (Auto-Scroll)
                    androidx.viewpager2.widget.ViewPager2 vp = findViewPager();
                    if (vp != null) {
                        long now = System.currentTimeMillis();
                        if (now - lastPageScrollTime > 1500) {
                            float dragX = event.getX();
                            int boundary = dpToPx(40);
                            final androidx.viewpager2.widget.ViewPager2 finalVp = vp;
                            if (dragX < boundary && vp.getCurrentItem() > 0) {
                                vp.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        finalVp.setCurrentItem(finalVp.getCurrentItem() - 1, true);
                                    }
                                });
                                lastPageScrollTime = now;
                                targetCellX = -1;
                                targetCellY = -1;
                                invalidate();
                                return true;
                            } else if (dragX > getWidth() - boundary) {
                                if (vp.getCurrentItem() < vp.getAdapter().getItemCount() - 1) {
                                    vp.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            finalVp.setCurrentItem(finalVp.getCurrentItem() + 1, true);
                                        }
                                    });
                                    lastPageScrollTime = now;
                                    targetCellX = -1;
                                    targetCellY = -1;
                                    invalidate();
                                    return true;
                                } else if (vp.getAdapter() instanceof app.organicmaps.carlauncher.widgets.WorkspacePageAdapter) {
                                    app.organicmaps.carlauncher.widgets.WorkspacePageAdapter adapter = 
                                        (app.organicmaps.carlauncher.widgets.WorkspacePageAdapter) vp.getAdapter();
                                    if (adapter.tryAddExtraPageForDrag()) {
                                        vp.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                finalVp.setCurrentItem(finalVp.getCurrentItem() + 1, true);
                                            }
                                        });
                                        lastPageScrollTime = now;
                                        targetCellX = -1;
                                        targetCellY = -1;
                                        invalidate();
                                        return true;
                                    }
                                }
                            }
                        }
                    }

                    // Surukleme shadow'unu ortalayarak hedef hucreyi hesapla
                    int cx = (int) ((event.getX() - sLeft - (cSize * targetSpanX / 2f)) / cSize + 0.5f);
                    int cy = (int) ((event.getY() - sTop - (cSize * targetSpanY / 2f)) / cSize + 0.5f);

                    // Sinirlandirma (Clamping)
                    cx = Math.max(0, Math.min(cCount - targetSpanX, cx));
                    cy = Math.max(0, Math.min(rCount - targetSpanY, cy));

                    if (cx != targetCellX || cy != targetCellY) {
                        targetCellX = cx;
                        targetCellY = cy;

                        if (dragFrame != null) {
                            BaseWidget w = findWidgetForFrame(dragFrame);
                            if (w != null) {
                                isTargetValid = canWidgetFitAt(w, pageIndex, targetCellX, targetCellY, targetSpanX, targetSpanY);
                            } else {
                                isTargetValid = false;
                            }
                        } else {
                            isTargetValid = false;
                        }
                        invalidate();
                    }
                }
                return true;

            case DragEvent.ACTION_DRAG_EXITED:
                targetCellX = -1;
                targetCellY = -1;
                invalidate();
                return true;

            case DragEvent.ACTION_DROP:
                isDragging = false;
                if (isTargetValid && dragFrame != null && targetCellX >= 0 && targetCellY >= 0) {
                    BaseWidget w = findWidgetForFrame(dragFrame);
                    if (w != null) {
                        w.setPageIndex(pageIndex);
                        w.setCellX(targetCellX);
                        w.setCellY(targetCellY);
                        
                        WidgetManager.getInstance(getContext()).saveWidgetConfig();
                        
                        // WorkspaceCellLayout LayoutParams guncelle
                        LayoutParams lp = (LayoutParams) dragFrame.getLayoutParams();
                        lp.cellX = targetCellX;
                        lp.cellY = targetCellY;
                        dragFrame.setLayoutParams(lp);

                        if (onWidgetsChangedListener != null) {
                            onWidgetsChangedListener.run();
                        }
                    }
                } else {
                    if (dragFrame != null) {
                        // Gecersiz hedefe birakildiginda uyar
                        Toast.makeText(getContext(), "Gecersiz konum! Ust uste yerlesim yapilamaz.", Toast.LENGTH_SHORT).show();
                    }
                }
                targetCellX = -1;
                targetCellY = -1;
                invalidate();
                return true;

            case DragEvent.ACTION_DRAG_ENDED:
                isDragging = false;
                targetCellX = -1;
                targetCellY = -1;
                if (dragFrame != null) {
                    dragFrame.setAlpha(1.0f); // Opakligi sifirla
                }
                
                // Ekstra bos sayfayi temizleme kontrolu
                androidx.viewpager2.widget.ViewPager2 vp2 = findViewPager();
                if (vp2 != null && vp2.getAdapter() instanceof app.organicmaps.carlauncher.widgets.WorkspacePageAdapter) {
                    app.organicmaps.carlauncher.widgets.WorkspacePageAdapter adapter = 
                        (app.organicmaps.carlauncher.widgets.WorkspacePageAdapter) vp2.getAdapter();
                    adapter.checkAndRemoveUnusedExtraPage(vp2);
                }
                
                invalidate();
                return true;
        }
        return false;
    }

    private BaseWidget findWidgetForFrame(WorkspaceWidgetFrame frame) {
        return frame != null ? frame.getWidget() : null;
    }

    private boolean canWidgetFitAt(BaseWidget targetWidget, int pageIndex, int cellX, int cellY, int spanX, int spanY) {
        int paddingLeft = getPaddingLeft();
        int paddingTop = getPaddingTop();
        int usableWidth = getWidth() - paddingLeft - getPaddingRight();
        int usableHeight = getHeight() - paddingTop - getPaddingBottom();

        int cellSize = getCellSize(getContext(), usableWidth, usableHeight);
        int colCount = getColCount(getContext(), usableWidth, cellSize);
        int rowCount = getRowCount(getContext(), usableHeight, cellSize);

        if (cellX < 0 || cellX + spanX > colCount || cellY < 0 || cellY + spanY > rowCount) {
            return false;
        }

        java.util.List<BaseWidget> list = WidgetManager.getInstance(getContext()).getAllWidgets();
        boolean[][] occupied = new boolean[colCount][rowCount];
        for (BaseWidget w : list) {
            if (w != targetWidget && w.isVisible() && w.getPageIndex() == pageIndex) {
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

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    public static class LayoutParams extends ViewGroup.LayoutParams {
        public int cellX = 0;
        public int cellY = 0;
        public int spanX = 1;
        public int spanY = 1;

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
            if (source instanceof LayoutParams) {
                LayoutParams lp = (LayoutParams) source;
                this.cellX = lp.cellX;
                this.cellY = lp.cellY;
                this.spanX = lp.spanX;
                this.spanY = lp.spanY;
            }
        }
    }

    private androidx.viewpager2.widget.ViewPager2 findViewPager() {
        android.view.ViewParent p = getParent();
        while (p != null) {
            if (p instanceof androidx.viewpager2.widget.ViewPager2) {
                return (androidx.viewpager2.widget.ViewPager2) p;
            }
            p = p.getParent();
        }
        return null;
    }
}
