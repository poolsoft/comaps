package app.organicmaps.carlauncher.widgets;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Tum widget'larin base class'i.
 * Her widget bu class'i extend eder.
 */
public abstract class BaseWidget {

    protected String id;
    protected String title;
    protected boolean isVisible;
    protected int order;
    protected Context context;
    protected View rootView;
    
    // Cok sayfali grid koordinatlari ve boyutlari
    protected int pageIndex = 0;
    protected int cellX = -1;
    protected int cellY = -1;
    protected int spanX = 1;
    protected int spanY = 1;

    public void setContext(Context context) {
        this.context = context;
    }

    /**
     * Guvenli bir sekilde Activity Context'ini dondurur.
     */
    public Context getContext() {
        return context;
    }

    public enum WidgetSize {
        SMALL, MEDIUM, LARGE
    }
    protected WidgetSize size = WidgetSize.SMALL;

    private boolean isStarted = false;

    public BaseWidget(@NonNull Context context, @NonNull String id, @NonNull String title) {
        this.context = context;
        this.id = id;
        this.title = title;
        this.isVisible = true;
        this.order = 0;
        this.size = WidgetSize.SMALL;
    }

    /**
     * Widget view'ini olustur.
     */
    @NonNull
    public abstract View createView();

    /**
     * Widget verilerini guncelle.
     */
    public abstract void update();

    /**
     * Widget baslat (listeners, observers ekle).
     */
    public void onStart() {
        isStarted = true;
    }

    /**
     * Widget durdur (listeners, observers kaldir).
     */
    public void onStop() {
        isStarted = false;
    }

    /**
     * Widget destroy.
     */
    public void onDestroy() {
        if (rootView != null) {
            rootView = null;
        }
        this.context = null; // Activity sizintisini onlemek icin context referansini sifirliyoruz (Turkce karakter yok)
    }

    // Getters & Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public boolean isVisible() {
        return isVisible;
    }

    public void setVisible(boolean visible) {
        this.isVisible = visible;
        if (rootView != null) {
            rootView.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public boolean isStarted() {
        return isStarted;
    }

    public WidgetSize getSize() {
        return size;
    }

    public void setSize(WidgetSize size) {
        this.size = size;
        switch (size) {
            case MEDIUM:
                this.spanX = 6;
                this.spanY = 3;
                break;
            case LARGE:
                this.spanX = 6;
                this.spanY = 6;
                break;
            case SMALL:
            default:
                this.spanX = 3;
                this.spanY = 3;
                break;
        }
        onSizeChanged(size);
    }
    
    /**
     * Widget boyutu degistiginde cagrilir.
     * Override edilerek widget icerigi guncellenebilir.
     */
    protected void onSizeChanged(WidgetSize newSize) {
        // Default impl
    }

    @Nullable
    public View getRootView() {
        return rootView;
    }

    /**
     * Widget'in ayarlanabilir olup olmadığını belirtir.
     * @return Varsayılan olarak false.
     */
    public boolean isConfigurable() {
        return false;
    }

    /**
     * Widget ayar ekranını açar.
     * @param fragmentManager Dialog göstermek için gerekli.
     */
    public void openConfig(androidx.fragment.app.FragmentManager fragmentManager) {
        // Varsayılan boş implementation
    }

    public int getPageIndex() { return pageIndex; }
    public void setPageIndex(int pageIndex) { this.pageIndex = pageIndex; }

    public int getCellX() { return cellX; }
    public void setCellX(int cellX) { this.cellX = cellX; }

    public int getCellY() { return cellY; }
    public void setCellY(int cellY) { this.cellY = cellY; }

    public int getSpanX() { return spanX; }
    public void setSpanX(int spanX) { this.spanX = spanX; }

    public int getSpanY() { return spanY; }
    public void setSpanY(int spanY) { this.spanY = spanY; }
}
