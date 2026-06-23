package app.organicmaps.carlauncher.widgets;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import android.widget.FrameLayout;
import app.organicmaps.R;
import app.organicmaps.carlauncher.widgets.view.WorkspaceCellLayout;
import app.organicmaps.carlauncher.widgets.view.WorkspaceWidgetFrame;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewPager2 icinde her bir 4x4 WorkspaceCellLayout sayfasini dolduran Adapter sinifi.
 * Kod icerisinde kesinlikle Turkce karakter kullanilmamistir.
 */
public class WorkspacePageAdapter extends RecyclerView.Adapter<WorkspacePageAdapter.PageViewHolder> {

    public interface EditModeListener {
        void onEditModeChanged(boolean isEditMode);
    }

    public interface OnWorkspaceLongClickListener {
        void onWorkspaceLongClick(View anchorView);
    }

    public static boolean isEditMode = false;
    private EditModeListener editModeListener;
    private static OnWorkspaceLongClickListener workspaceLongClickListener;
    private static java.lang.ref.WeakReference<WorkspacePageAdapter> activeAdapterInstanceRef;

    public static void setWorkspaceLongClickListener(OnWorkspaceLongClickListener listener) {
        workspaceLongClickListener = listener;
    }

    public static void exitEditMode() {
        isEditMode = false;
        WorkspacePageAdapter activeAdapter = activeAdapterInstanceRef != null ? activeAdapterInstanceRef.get() : null;
        if (activeAdapter != null) {
            if (activeAdapter.editModeListener != null) {
                activeAdapter.editModeListener.onEditModeChanged(false);
            }
            activeAdapter.notifyDataSetChanged();
            if (activeAdapter.onWidgetsChangedListener != null) {
                activeAdapter.onWidgetsChangedListener.run();
            }
        }
    }

    private final Context context;
    private final FragmentManager fragmentManager;
    private List<BaseWidget> widgetsList;
    private final Runnable onWidgetsChangedListener;

    public void updateWidgetsList(List<BaseWidget> newWidgets) {
        this.widgetsList = new ArrayList<>(newWidgets);
        notifyDataSetChanged();
    }

    private boolean hasExtraPage = false;

    public int getBasePageCount() {
        int maxPageOfWidgets = 0;
        for (BaseWidget w : widgetsList) {
            if (w.isVisible()) {
                maxPageOfWidgets = Math.max(maxPageOfWidgets, w.getPageIndex());
            }
        }
        // Temel sayfa sayisi her zaman en az 3'tur (Kullanici istegi)
        return Math.max(3, maxPageOfWidgets + 1);
    }

    public int getPageCount() {
        int baseCount = getBasePageCount();
        if (hasExtraPage) {
            return baseCount + 1;
        }
        return baseCount;
    }

    public boolean tryAddExtraPageForDrag() {
        // Sagda zaten yeni eklenen bos sayfa varken ikinciyi eklemesin
        if (hasExtraPage) {
            return false;
        }
        hasExtraPage = true;
        int newPageIndex = getPageCount() - 1;
        notifyItemInserted(newPageIndex);
        if (onWidgetsChangedListener != null) {
            onWidgetsChangedListener.run(); // Page indicator'i da guncelle
        }
        return true;
    }

    public void checkAndRemoveUnusedExtraPage(final androidx.viewpager2.widget.ViewPager2 vp) {
        if (!hasExtraPage) return;
        
        final int extraPageIndex = getPageCount() - 1;
        boolean hasWidgetsOnExtraPage = false;
        for (BaseWidget w : widgetsList) {
            if (w.isVisible() && w.getPageIndex() == extraPageIndex) {
                hasWidgetsOnExtraPage = true;
                break;
            }
        }
        
        // Eger ekstra sayfaya hicbir widget birakilmadiysa, bu sayfayi kaldir
        if (!hasWidgetsOnExtraPage) {
            hasExtraPage = false;
            if (vp != null && vp.getCurrentItem() == extraPageIndex) {
                // Eger su an silinecek olan ekstra sayfadaysak, once bir onceki sayfaya kaydiralim
                vp.setCurrentItem(extraPageIndex - 1, true);
                vp.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        notifyItemRemoved(extraPageIndex);
                        if (onWidgetsChangedListener != null) {
                            onWidgetsChangedListener.run(); // Page indicator'i da guncelle
                        }
                    }
                }, 300); // 300ms animasyon suresi sonrasi silme
            } else {
                notifyItemRemoved(extraPageIndex);
                if (onWidgetsChangedListener != null) {
                    onWidgetsChangedListener.run(); // Page indicator'i da guncelle
                }
            }
        } else {
            // Eger widget birakilmissa, bu sayfa artik kalici hale gelir ve hasExtraPage sifirlanir
            hasExtraPage = false;
        }
    }

    public WorkspacePageAdapter(Context context, FragmentManager fragmentManager, List<BaseWidget> widgets, Runnable onWidgetsChangedListener) {
        this.context = context;
        this.fragmentManager = fragmentManager;
        this.widgetsList = widgets;
        this.onWidgetsChangedListener = onWidgetsChangedListener;
        activeAdapterInstanceRef = new java.lang.ref.WeakReference<>(this);
    }

    public void setEditModeListener(EditModeListener listener) {
        this.editModeListener = listener;
    }

    public EditModeListener getEditModeListener() {
        return editModeListener;
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.workspace_page_grid, parent, false);
        return new PageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        holder.bind(position);
    }

    @Override
    public int getItemCount() {
        return getPageCount();
    }

    class PageViewHolder extends RecyclerView.ViewHolder {
        WorkspaceCellLayout cellLayout;

        public PageViewHolder(@NonNull View itemView) {
            super(itemView);
            cellLayout = itemView.findViewById(R.id.workspace_grid);
        }

        public void bind(int pageIndex) {
            cellLayout.removeAllViews();
            cellLayout.setPageIndex(pageIndex);
            cellLayout.setOnWidgetsChangedListener(onWidgetsChangedListener);

            // Edit modundan cikmak icin cellLayout'a tiklama
            cellLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (isEditMode) {
                        isEditMode = false;
                        if (editModeListener != null) {
                            editModeListener.onEditModeChanged(false);
                        }
                        notifyDataSetChanged();
                    }
                }
            });

            cellLayout.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (workspaceLongClickListener != null) {
                        workspaceLongClickListener.onWorkspaceLongClick(v);
                        return true;
                    }
                    return false;
                }
            });
            
            // Bu sayfadaki gorunur widget'lari bul
            List<BaseWidget> pageWidgets = new ArrayList<>();
            for (BaseWidget widget : widgetsList) {
                if (widget.isVisible() && widget.getPageIndex() == pageIndex) {
                    pageWidgets.add(widget);
                }
            }

            android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
            float density = dm.density;
            int screenWidthPx = dm.widthPixels;
            int screenHeightPx = dm.heightPixels;
            
            int paddingSidePx = Math.round(12 * density);
            int paddingTopBottomPx = Math.round(8 * density);
            int taskbarPx = Math.round(56 * density);
            
            int usableWidthPx = screenWidthPx - (2 * paddingSidePx);
            int usableHeightPx = screenHeightPx - (2 * paddingTopBottomPx) - taskbarPx;
            
            int cellSize = WorkspaceCellLayout.getCellSize(context, usableWidthPx, usableHeightPx);
            int colCount = WorkspaceCellLayout.getColCount(context, usableWidthPx, cellSize);
            int rowCount = WorkspaceCellLayout.getRowCount(context, usableHeightPx, cellSize);

            // Izgara doluluk matrisi
            boolean[][] occupied = new boolean[colCount][rowCount];

            // Once koordinati belli olan widget'lari yerlestirelim ve matrisi isaretleyelim
            for (BaseWidget widget : pageWidgets) {
                int cx = widget.getCellX();
                int cy = widget.getCellY();
                int sx = widget.getSpanX();
                int sy = widget.getSpanY();

                if (cx >= 0 && cx + sx <= colCount && cy >= 0 && cy + sy <= rowCount) {
                    for (int x = cx; x < cx + sx; x++) {
                        for (int y = cy; y < cy + sy; y++) {
                            occupied[x][y] = true;
                        }
                    }
                }
            }

            // Simdi koordinati olmayan widget'lara bos yer bulup atayalim
            boolean needsSave = false;
            for (BaseWidget widget : pageWidgets) {
                if (widget.getCellX() == -1 || widget.getCellY() == -1) {
                    int sx = widget.getSpanX();
                    int sy = widget.getSpanY();
                    Point emptyPoint = findEmptySpace(occupied, sx, sy);
                    if (emptyPoint != null) {
                        widget.setCellX(emptyPoint.x);
                        widget.setCellY(emptyPoint.y);
                        for (int x = emptyPoint.x; x < emptyPoint.x + sx; x++) {
                            for (int y = emptyPoint.y; y < emptyPoint.y + sy; y++) {
                                occupied[x][y] = true;
                            }
                        }
                        needsSave = true;
                    } else {
                        // Sayfa doluysa, bir sonraki sayfaya tasi (maksimum 3 sayfa)
                        if (pageIndex < 2) {
                            widget.setPageIndex(pageIndex + 1);
                            widget.setCellX(-1);
                            widget.setCellY(-1);
                            needsSave = true;
                        }
                    }
                }
            }

            if (needsSave) {
                WidgetManager.getInstance(context).saveWidgetConfig();
                cellLayout.post(new Runnable() {
                    @Override
                    public void run() {
                        notifyDataSetChanged();
                        if (onWidgetsChangedListener != null) {
                            onWidgetsChangedListener.run();
                        }
                    }
                });
            }

            // Sayfadaki tum widget'lari WorkspaceCellLayout'a ekleyelim
            for (final BaseWidget widget : pageWidgets) {
                if (widget.getCellX() == -1 || widget.getCellY() == -1) {
                    continue; // Yer bulunamadiysa ekleme
                }

                View widgetView = widget.getRootView();
                if (widgetView == null) {
                    widgetView = widget.createView();
                }

                if (widgetView != null) {
                    // View parent baglantisini temizle
                    ViewGroup parent = (ViewGroup) widgetView.getParent();
                    if (parent != null) {
                        parent.removeView(widgetView);
                    }

                    // WorkspaceWidgetFrame olustur
                    WorkspaceWidgetFrame widgetFrame = new WorkspaceWidgetFrame(
                            context,
                            widget,
                            cellLayout,
                            fragmentManager,
                            new Runnable() {
                                @Override
                                public void run() {
                                    notifyDataSetChanged();
                                    if (onWidgetsChangedListener != null) {
                                        onWidgetsChangedListener.run();
                                    }
                                }
                            }
                    );
                    
                    // Widget view'i frame'e ekle
                    widgetFrame.setWidgetView(widgetView);
                    widgetFrame.setEditMode(isEditMode);

                    // WorkspaceCellLayout LayoutParams olustur
                    WorkspaceCellLayout.LayoutParams params = new WorkspaceCellLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, 
                            ViewGroup.LayoutParams.MATCH_PARENT
                    );
                    params.cellX = widget.getCellX();
                    params.cellY = widget.getCellY();
                    params.spanX = widget.getSpanX();
                    params.spanY = widget.getSpanY();
                    
                    widgetFrame.setLayoutParams(params);

                    // Normal moddayken uzun basinca Duzenleme Modunu aktif et
                    widgetView.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            if (!isEditMode) {
                                isEditMode = true;
                                if (editModeListener != null) {
                                    editModeListener.onEditModeChanged(true);
                                }
                                notifyDataSetChanged();
                                return true;
                            }
                            return false;
                        }
                    });

                    cellLayout.addView(widgetFrame);
                }
            }
        }

        private Point findEmptySpace(boolean[][] occupied, int spanX, int spanY) {
            if (occupied == null || occupied.length == 0 || occupied[0].length == 0) return null;
            int maxCol = occupied.length;
            int maxRow = occupied[0].length;
            for (int y = 0; y <= maxRow - spanY; y++) {
                for (int x = 0; x <= maxCol - spanX; x++) {
                    boolean fits = true;
                    for (int sy = 0; sy < spanY; sy++) {
                        for (int sx = 0; sx < spanX; sx++) {
                            if (occupied[x + sx][y + sy]) {
                                fits = false;
                                break;
                            }
                        }
                        if (!fits) break;
                    }
                    if (fits) {
                        return new Point(x, y);
                    }
                }
            }
            return null;
        }

        private void showWidgetOptionsPopupMenu(View anchorView, final BaseWidget widget) {
            PopupMenu popup = new PopupMenu(context, anchorView);
            
            // Menu maddelerini dinamik ekleyelim
            if (widget.isConfigurable()) {
                popup.getMenu().add(0, 1, 0, "Widget Ayarlari");
            }
            
            popup.getMenu().add(0, 2, 1, "Boyut: Small (3x3)");
            popup.getMenu().add(0, 3, 2, "Boyut: Medium (6x3)");
            popup.getMenu().add(0, 4, 3, "Boyut: Large (6x6)");
            
            // Sayfa tasima secenekleri
            if (widget.getPageIndex() > 0) {
                popup.getMenu().add(0, 5, 4, "Sola Tasi (Sayfa " + widget.getPageIndex() + ")");
            }
            if (widget.getPageIndex() < 2) {
                popup.getMenu().add(0, 6, 5, "Saga Tasi (Sayfa " + (widget.getPageIndex() + 2) + ")");
            }
            
            popup.getMenu().add(0, 7, 6, "Kapat / Kaldir");

            popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(android.view.MenuItem item) {
                    int itemId = item.getItemId();
                    if (itemId == 1) {
                        widget.openConfig(fragmentManager);
                        return true;
                    } else if (itemId == 2) {
                        changeWidgetSize(widget, BaseWidget.WidgetSize.SMALL);
                        return true;
                    } else if (itemId == 3) {
                        changeWidgetSize(widget, BaseWidget.WidgetSize.MEDIUM);
                        return true;
                    } else if (itemId == 4) {
                        changeWidgetSize(widget, BaseWidget.WidgetSize.LARGE);
                        return true;
                    } else if (itemId == 5) {
                        moveWidgetToPage(widget, widget.getPageIndex() - 1);
                        return true;
                    } else if (itemId == 6) {
                        moveWidgetToPage(widget, widget.getPageIndex() + 1);
                        return true;
                    } else if (itemId == 7) {
                        removeWidgetFromWorkspace(widget);
                        return true;
                    }
                    return false;
                }
            });
            popup.show();
        }

        private boolean canWidgetFitAt(BaseWidget targetWidget, int pageIndex, int cellX, int cellY, int spanX, int spanY) {
            android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
            float density = dm.density;
            int screenWidthPx = dm.widthPixels;
            int screenHeightPx = dm.heightPixels;
            int paddingSidePx = Math.round(12 * density);
            int paddingTopBottomPx = Math.round(8 * density);
            int taskbarPx = Math.round(56 * density);
            int usableWidthPx = screenWidthPx - (2 * paddingSidePx);
            int usableHeightPx = screenHeightPx - (2 * paddingTopBottomPx) - taskbarPx;
            
            int cellSize = WorkspaceCellLayout.getCellSize(context, usableWidthPx, usableHeightPx);
            int colCount = WorkspaceCellLayout.getColCount(context, usableWidthPx, cellSize);
            int rowCount = WorkspaceCellLayout.getRowCount(context, usableHeightPx, cellSize);

            if (cellX < 0 || cellX + spanX > colCount || cellY < 0 || cellY + spanY > rowCount) {
                return false;
            }

            // Hedef sayfa doluluk matrisini olustur (TargetWidget haric)
            boolean[][] occupied = new boolean[colCount][rowCount];
            for (BaseWidget w : widgetsList) {
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

            // Cakisma olup olmadigini test et
            for (int x = cellX; x < cellX + spanX; x++) {
                for (int y = cellY; y < cellY + spanY; y++) {
                    if (occupied[x][y]) {
                        return false;
                    }
                }
            }
            return true;
        }

        private Point findNewPositionForWidget(BaseWidget targetWidget, int pageIndex, int spanX, int spanY) {
            android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
            float density = dm.density;
            int screenWidthPx = dm.widthPixels;
            int screenHeightPx = dm.heightPixels;
            int paddingSidePx = Math.round(12 * density);
            int paddingTopBottomPx = Math.round(8 * density);
            int taskbarPx = Math.round(56 * density);
            int usableWidthPx = screenWidthPx - (2 * paddingSidePx);
            int usableHeightPx = screenHeightPx - (2 * paddingTopBottomPx) - taskbarPx;
            
            int cellSize = WorkspaceCellLayout.getCellSize(context, usableWidthPx, usableHeightPx);
            int colCount = WorkspaceCellLayout.getColCount(context, usableWidthPx, cellSize);
            int rowCount = WorkspaceCellLayout.getRowCount(context, usableHeightPx, cellSize);

            boolean[][] occupied = new boolean[colCount][rowCount];
            for (BaseWidget w : widgetsList) {
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
            return findEmptySpace(occupied, spanX, spanY);
        }

        private void changeWidgetSize(BaseWidget widget, BaseWidget.WidgetSize newSize) {
            int targetSpanX = 3;
            int targetSpanY = 3;
            if (newSize == BaseWidget.WidgetSize.MEDIUM) {
                targetSpanX = 6;
                targetSpanY = 3;
            } else if (newSize == BaseWidget.WidgetSize.LARGE) {
                targetSpanX = 6;
                targetSpanY = 6;
            }

            // Cakisma kontrolü: Bu sayfadaki diger widget'lar ile cakismadan bu boyuta gecip gecemeyecegini kontrol et
            boolean fits = canWidgetFitAt(widget, widget.getPageIndex(), widget.getCellX(), widget.getCellY(), targetSpanX, targetSpanY);
            
            if (fits) {
                widget.setSize(newSize);
            } else {
                // Eger koordinatinda sigmiyorsa, sayfada baska bir bos yer ara
                Point p = findNewPositionForWidget(widget, widget.getPageIndex(), targetSpanX, targetSpanY);
                if (p != null) {
                    widget.setSize(newSize);
                    widget.setCellX(p.x);
                    widget.setCellY(p.y);
                } else {
                    // Sayfada hic yer yoksa, diger sayfalarda bosluk ara
                    boolean foundInOtherPage = false;
                    for (int page = 0; page < 3; page++) {
                        if (page != widget.getPageIndex()) {
                            Point op = findNewPositionForWidget(widget, page, targetSpanX, targetSpanY);
                            if (op != null) {
                                widget.setSize(newSize);
                                widget.setPageIndex(page);
                                widget.setCellX(op.x);
                                widget.setCellY(op.y);
                                foundInOtherPage = true;
                                Toast.makeText(context, widget.getTitle() + " " + (page + 1) + ". sayfaya tasinarak buyutuldu.", Toast.LENGTH_LONG).show();
                                break;
                            }
                        }
                    }
                    if (!foundInOtherPage) {
                        Toast.makeText(context, "Hicbir sayfada bu boyuta uygun bos alan bulunamadi!", Toast.LENGTH_LONG).show();
                        return;
                    }
                }
            }

            WidgetManager.getInstance(context).saveWidgetConfig();
            notifyDataSetChanged();
            if (onWidgetsChangedListener != null) {
                onWidgetsChangedListener.run();
            }
            Toast.makeText(context, widget.getTitle() + " boyutu guncellendi.", Toast.LENGTH_SHORT).show();
        }

        private void moveWidgetToPage(BaseWidget widget, int targetPage) {
            int targetSpanX = widget.getSpanX();
            int targetSpanY = widget.getSpanY();
            
            Point p = findNewPositionForWidget(widget, targetPage, targetSpanX, targetSpanY);
            if (p != null) {
                widget.setPageIndex(targetPage);
                widget.setCellX(p.x);
                widget.setCellY(p.y);
                
                WidgetManager.getInstance(context).saveWidgetConfig();
                notifyDataSetChanged();
                if (onWidgetsChangedListener != null) {
                    onWidgetsChangedListener.run();
                }
                Toast.makeText(context, widget.getTitle() + " " + (targetPage + 1) + ". sayfaya tasindi.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "Hedef sayfada bu widget icin yeterli bos alan bulunamadi!", Toast.LENGTH_SHORT).show();
            }
        }

        private void removeWidgetFromWorkspace(BaseWidget widget) {
            widget.setVisible(false);
            WidgetManager.getInstance(context).saveWidgetConfig();
            notifyDataSetChanged();
            if (onWidgetsChangedListener != null) {
                onWidgetsChangedListener.run();
            }
            Toast.makeText(context, widget.getTitle() + " kaldirildi.", Toast.LENGTH_SHORT).show();
        }
    }

    private static class Point {
        int x, y;
        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}

