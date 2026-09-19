package app.organicmaps.carlauncher.ui;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import androidx.annotation.Nullable;
import com.google.android.material.textview.MaterialTextView;

/**
 * CarLauncher icin ozel nav zaman/mesafe metin gorunumu.
 * Upstream tarafindan eklenen koyu tema span'lerini temizleyerek
 * gunduz/gece temasinda her zaman beyaz ve net kalmasini saglar.
 */
public class CarNavTimeTextView extends MaterialTextView
{
  public CarNavTimeTextView(Context context)
  {
    super(context);
  }

  public CarNavTimeTextView(Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
  }

  public CarNavTimeTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr)
  {
    super(context, attrs, defStyleAttr);
  }

  public CarNavTimeTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes)
  {
    super(context, attrs, defStyleAttr, defStyleRes);
  }

  @Override
  public void setText(CharSequence text, BufferType type)
  {
    if (text instanceof Spanned)
    {
      SpannableStringBuilder ssb = new SpannableStringBuilder(text);
      ForegroundColorSpan[] spans = ssb.getSpans(0, ssb.length(), ForegroundColorSpan.class);
      for (ForegroundColorSpan span : spans)
      {
        ssb.removeSpan(span);
      }
      super.setText(ssb, type);
    }
    else
    {
      super.setText(text, type);
    }
  }
}
