package app.organicmaps.maplayer;

import android.content.Context;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatImageButton;
import app.organicmaps.R;

public class LayersButton extends AppCompatImageButton
{
  private boolean mAreLayersActive = false;

  public LayersButton(Context context)
  {
    super(context);
  }

  public LayersButton(Context context, AttributeSet attrs)
  {
    super(context, attrs);
  }

  public LayersButton(Context context, AttributeSet attrs, int defStyleAttr)
  {
    super(context, attrs, defStyleAttr);
  }

  @Override
  public int[] onCreateDrawableState(int extraSpace)
  {
    final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
    if (mAreLayersActive)
      mergeDrawableStates(drawableState, new int[] {R.attr.layers_enabled});
    return drawableState;
  }

  public void setHasActiveLayers(boolean areLayersActive)
  {
    mAreLayersActive = areLayersActive;
    refreshDrawableState();
  }
}
