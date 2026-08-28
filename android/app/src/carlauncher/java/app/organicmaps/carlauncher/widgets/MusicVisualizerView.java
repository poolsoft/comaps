package app.organicmaps.carlauncher.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class MusicVisualizerView extends View {

    public static final int TYPE_CLASSIC = 0;
    public static final int TYPE_GLOW_PEAK = 1;
    public static final int TYPE_NEON_MODERN = 2;
    public static final int TYPE_WAVE = 3;
    public static final int TYPE_RADIAL = 4;
    public static final int TYPE_CENTER_MIRRORED = 5;
    public static final int TYPE_PARTICLE = 6;
    public static final int TYPE_RINGS = 7;
    private int visualizerType = TYPE_NEON_MODERN;
    private int dominantColor = 0;
    private boolean isSmallPanel = true;

    public void setVisualizerContext(boolean isSmallPanel) {
        this.isSmallPanel = isSmallPanel;
        reloadSettings();
    }

    public void setDominantColor(int color) {
        if (color != 0) {
            float[] hsv = new float[3];
            Color.colorToHSV(color, hsv);
            // Eger renk cok donukse doygunlugu (Saturation) yukseltiyoruz (Turkce karakter yok)
            if (hsv[1] > 0.05f) {
                hsv[1] = Math.max(hsv[1], 0.85f);
            }
            // Parlakligi (Value) her zaman en yuksek seviyede tutuyoruz ki parlasin (Turkce karakter yok)
            hsv[2] = Math.max(hsv[2], 0.90f);
            this.dominantColor = Color.HSVToColor(hsv);
        } else {
            this.dominantColor = 0;
        }
        this.mFirst = true; // Force shader recreation
        postInvalidate();
    }

    private int getDarkerColor(int color, float factor) {
        int a = Color.alpha(color);
        int r = Math.round(Color.red(color) * factor);
        int g = Math.round(Color.green(color) * factor);
        int b = Math.round(Color.blue(color) * factor);
        return Color.argb(a, Math.min(r, 255), Math.min(g, 255), Math.min(b, 255));
    }

    private int getLighterColor(int color, float factor) {
        int a = Color.alpha(color);
        int r = Math.min(255, Math.round(Color.red(color) + (255 - Color.red(color)) * factor));
        int g = Math.min(255, Math.round(Color.green(color) + (255 - Color.green(color)) * factor));
        int b = Math.min(255, Math.round(Color.blue(color) + (255 - Color.blue(color)) * factor));
        return Color.argb(a, r, g, b);
    }

    private byte[] mBytes;
    private float[] mPoints;
    private RectF mRect = new RectF();
    private Paint mForePaint = new Paint();
    private Paint mPeakPaint = new Paint();
    private Paint mReflectionPaint = new Paint();
    private Paint mReflectionDividerPaint = new Paint();
    private int mSpectrumNum = 48; // Bar count
    private boolean mFirst = true;
    private float mirrorReflectionRatio;

    private float[] mPeaks;
    private long[] mPeakTimes;

    public MusicVisualizerView(Context context) {
        super(context);
        init();
    }

    public MusicVisualizerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MusicVisualizerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mBytes = null;
        mForePaint.setStrokeWidth(8f);
        mForePaint.setAntiAlias(true);
        mForePaint.setStyle(Paint.Style.FILL);

        mPeakPaint.setAntiAlias(true);
        mPeakPaint.setStyle(Paint.Style.FILL);
        mPeakPaint.setColor(Color.WHITE);

        mReflectionPaint.setAntiAlias(true);
        mReflectionPaint.setStyle(Paint.Style.FILL);
        mReflectionDividerPaint.setAntiAlias(true);
        mReflectionDividerPaint.setStrokeWidth(1f);
        mReflectionDividerPaint.setColor(Color.argb(72, 255, 255, 255));

        // Baslangic olarak kucuk panel varsayiliyor, disaridan context atandiginda degisecek
        try {
            android.content.SharedPreferences prefs = getContext().getSharedPreferences("car_launcher_prefs", Context.MODE_PRIVATE);
            String typeStr = prefs.getString("car_launcher_visualizer_type_small", "2");
            visualizerType = Integer.parseInt(typeStr);
        } catch (Exception e) {
            visualizerType = TYPE_NEON_MODERN;
        }

        /* Jestleri (swipe vb.) engellememesi icin secim ozelligini kaldirdik
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                visualizerType = (visualizerType + 1) % 3;
                try {
                    android.content.SharedPreferences.Editor editor = getContext().getSharedPreferences("music_visualizer_prefs", Context.MODE_PRIVATE).edit();
                    editor.putInt("visualizer_type", visualizerType);
                    editor.apply();
                } catch (Exception e) {
                    // ignore
                }
                mFirst = true; // Paint ayarlari yeniden yapilsin
                invalidate();
            }
        });
        */

        // Golge ve parilti efektlerinin cizilmesi icin yazilimsal katman destegi (Turkce karakter yok)
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void cycleVisualizerType() {
        visualizerType = (visualizerType + 1) % 8;
        try {
            android.content.SharedPreferences.Editor editor = getContext().getSharedPreferences("car_launcher_prefs", Context.MODE_PRIVATE).edit();
            String key = isSmallPanel ? "car_launcher_visualizer_type_small" : "car_launcher_visualizer_type_large";
            editor.putString(key, String.valueOf(visualizerType));
            editor.apply();
        } catch (Exception e) {
            // ignore
        }
        mFirst = true; // Paint ayarlari yeniden yapilsin
        invalidate();
    }

    public void reloadSettings() {
        try {
            android.content.SharedPreferences prefs = getContext().getSharedPreferences("car_launcher_prefs", Context.MODE_PRIVATE);
            String key = isSmallPanel ? "car_launcher_visualizer_type_small" : "car_launcher_visualizer_type_large";
            String defaultType = isSmallPanel ? "2" : "4";
            String typeStr = prefs.getString(key, defaultType);
            int newType = Integer.parseInt(typeStr);
            if (visualizerType != newType) {
                visualizerType = newType;
                mFirst = true;
                invalidate();
            }
        } catch (Exception e) {
            // ignore
        }
    }

    /** Keeps the faded reflection in this View to avoid another bitmap/Visualizer allocation. */
    public void setMirrorReflectionRatio(float ratio) {
        float clamped = Math.max(0f, Math.min(0.28f, ratio));
        if (Math.abs(mirrorReflectionRatio - clamped) > 0.001f) {
            mirrorReflectionRatio = clamped;
            invalidate();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mFirst = true;
    }

    public void updateVisualizer(byte[] fft) {
        if (fft == null) return;
        
        byte[] model = new byte[fft.length / 2 + 1];
        model[0] = (byte) Math.abs(fft[0]);
        for (int i = 2, j = 1; j < mSpectrumNum; ) {
            if (i >= fft.length) break;
            
            model[j] = (byte) Math.hypot(fft[i], fft[i + 1]);
            i += 2;
            j++;
        }
        mBytes = model;
        invalidate();
    }

    public void clear() {
        mBytes = null;
        invalidate();
    }

    private void drawFadedReflection(Canvas canvas, float left, float top, float right,
                                     float bottom, float cornerRadius) {
        float fadeSplit = top + ((bottom - top) * 0.58f);
        mReflectionPaint.setAlpha(58);
        if (cornerRadius > 0f) {
            canvas.drawRoundRect(left, top, right, fadeSplit, cornerRadius, cornerRadius, mReflectionPaint);
        } else {
            canvas.drawRect(left, top, right, fadeSplit, mReflectionPaint);
        }
        mReflectionPaint.setAlpha(20);
        if (cornerRadius > 0f) {
            canvas.drawRoundRect(left, fadeSplit, right, bottom, cornerRadius, cornerRadius, mReflectionPaint);
        } else {
            canvas.drawRect(left, fadeSplit, right, bottom, mReflectionPaint);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mBytes == null) {
            return;
        }

        mRect.set(0, 0, getWidth(), getHeight());

        mPeakPaint.setColor(dominantColor != 0 ? getLighterColor(dominantColor, 0.5f) : Color.WHITE);

        if (mFirst) {
            int heightVal = getHeight() > 0 ? getHeight() : 100;
            if (dominantColor != 0) {
                int startColor = Color.argb(38, Color.red(dominantColor), Color.green(dominantColor), Color.blue(dominantColor));
                int endColor = dominantColor;
                int[] colors = {startColor, endColor};
                LinearGradient shader = new LinearGradient(
                        0, heightVal, 0, 0, 
                        colors, null, Shader.TileMode.CLAMP);
                mForePaint.setShader(shader);
                mForePaint.setShadowLayer(15f, 0f, 0f, dominantColor);
            } else if (visualizerType == TYPE_NEON_MODERN) {
                int[] colors = {
                    Color.parseColor("#0044FF"),
                    Color.parseColor("#00FFFF")
                };
                LinearGradient shader = new LinearGradient(
                        0, heightVal, 0, 0, 
                        colors, null, Shader.TileMode.CLAMP);
                mForePaint.setShader(shader);
                mForePaint.setShadowLayer(15f, 0f, 0f, Color.parseColor("#00FFFF"));
            } else {
                int[] colors = {
                    Color.parseColor("#FF0000"), Color.parseColor("#FFFF00"),
                    Color.parseColor("#00FF00"), Color.parseColor("#00FFFF"),
                    Color.parseColor("#0000FF"), Color.parseColor("#FF00FF")
                };
                LinearGradient shader = new LinearGradient(
                        0, heightVal, 0, 0, 
                        colors, null, Shader.TileMode.CLAMP);
                mForePaint.setShader(shader);
                mForePaint.clearShadowLayer();
            }
            mReflectionPaint.setShader(mForePaint.getShader());
            mFirst = false;
        }

        int spectrumNum = Math.min(mSpectrumNum, mBytes.length);
        float barWidth = getWidth() / (float) spectrumNum;
        
        float gapRatio = (visualizerType == TYPE_NEON_MODERN || visualizerType == TYPE_PARTICLE) ? 0.25f : 0.16f;
        float gap = barWidth * gapRatio;
        float effectiveBarWidth = barWidth - gap;

        if (visualizerType == TYPE_GLOW_PEAK || visualizerType == TYPE_PARTICLE) {
            if (mPeaks == null || mPeaks.length != spectrumNum) {
                mPeaks = new float[spectrumNum];
                mPeakTimes = new long[spectrumNum];
                long now = System.currentTimeMillis();
                for (int i = 0; i < spectrumNum; i++) {
                    mPeaks[i] = 0;
                    mPeakTimes[i] = now;
                }
            }
        }

        long now = System.currentTimeMillis();

        if (visualizerType == TYPE_WAVE) {
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(0, getHeight());
            float prevX = 0;
            float prevY = getHeight();
            for (int i = 0; i < spectrumNum; i++) {
                float magnitude = (float) (Math.abs(mBytes[i]) * 4); 
                float height = (magnitude / 128f) * getHeight() * 0.8f;
                float currentX = i * barWidth + (barWidth / 2f);
                float currentY = getHeight() - height;
                path.quadTo(prevX, prevY, (prevX + currentX) / 2f, (prevY + currentY) / 2f);
                prevX = currentX;
                prevY = currentY;
            }
            path.lineTo(getWidth(), prevY);
            path.lineTo(getWidth(), getHeight());
            path.close();
            canvas.drawPath(path, mForePaint);
        } else if (visualizerType == TYPE_RADIAL) {
            float centerX = getWidth() / 2f;
            float centerY = getHeight() / 2f;
            float baseRadius = Math.min(centerX, centerY) * 0.24f;
            for (int i = 0; i < spectrumNum; i++) {
                float magnitude = (float) (Math.abs(mBytes[i]) * 4); 
                float height = (magnitude / 128f) * Math.min(centerX, centerY) * 0.86f;
                float angle = (float) (i * 2 * Math.PI / spectrumNum);
                float startX = centerX + (float) Math.cos(angle) * baseRadius;
                float startY = centerY + (float) Math.sin(angle) * baseRadius;
                float endX = centerX + (float) Math.cos(angle) * (baseRadius + height);
                float endY = centerY + (float) Math.sin(angle) * (baseRadius + height);
                mForePaint.setStrokeWidth(effectiveBarWidth * 1.2f);
                mForePaint.setStyle(Paint.Style.STROKE);
                mForePaint.setStrokeCap(Paint.Cap.ROUND);
                canvas.drawLine(startX, startY, endX, endY, mForePaint);
            }
            mForePaint.setStyle(Paint.Style.FILL);
        } else if (visualizerType == TYPE_RINGS) {
            float centerX = getWidth() / 2f;
            float centerY = getHeight() / 2f;
            mForePaint.setStyle(Paint.Style.STROKE);
            for (int r = 0; r < 4; r++) {
                int index = (r * spectrumNum) / 5;
                if (index < spectrumNum) {
                    float magnitude = (float) (Math.abs(mBytes[index]) * 4);
                    float extraRadius = (magnitude / 128f) * Math.min(centerX, centerY) * 0.5f;
                    mForePaint.setStrokeWidth(10f - (r * 2f));
                    canvas.drawCircle(centerX, centerY, 50f + (r * 40f) + extraRadius, mForePaint);
                }
            }
            mForePaint.setStyle(Paint.Style.FILL);
        } else {
            boolean drawReflection = mirrorReflectionRatio > 0f
                    && visualizerType != TYPE_CENTER_MIRRORED
                    && visualizerType != TYPE_PARTICLE;
            float reflectionGap = drawReflection ? Math.max(1f, getHeight() * 0.012f) : 0f;
            float reflectionSpace = drawReflection ? getHeight() * mirrorReflectionRatio : 0f;
            float mainBottom = getHeight() - reflectionSpace - reflectionGap;
            float mainHeight = Math.max(1f, mainBottom);
            if (drawReflection) {
                canvas.drawLine(0, mainBottom + (reflectionGap * 0.5f), getWidth(),
                        mainBottom + (reflectionGap * 0.5f), mReflectionDividerPaint);
            }
            for (int i = 0; i < spectrumNum; i++) {
                float magnitude = (float) (Math.abs(mBytes[i]) * 4); 
                float height = Math.max((magnitude / 128f) * mainHeight, mainHeight * 0.035f);
                if (height > mainHeight) height = mainHeight;
                if (height < 0) height = 0;

                float left = i * barWidth + (gap/2);
                float top = mainBottom - height;
                float right = left + effectiveBarWidth;
                float bottom = mainBottom;

                if (visualizerType == TYPE_NEON_MODERN) {
                    canvas.drawRoundRect(left, top, right, bottom, effectiveBarWidth / 2f, effectiveBarWidth / 2f, mForePaint);
                } else if (visualizerType == TYPE_CENTER_MIRRORED) {
                    float midY = getHeight() / 2f;
                    float halfHeight = height / 2f;
                    canvas.drawRoundRect(left, midY - halfHeight, right, midY + halfHeight, effectiveBarWidth / 2f, effectiveBarWidth / 2f, mForePaint);
                } else if (visualizerType == TYPE_PARTICLE) {
                    if (height >= mPeaks[i]) {
                        mPeaks[i] = height;
                        mPeakTimes[i] = now;
                    } else {
                        float elapsed = (now - mPeakTimes[i]) / 1000f;
                        float decay = elapsed * elapsed * getHeight() * 1.5f; // Yercekimi ivmesi
                        mPeaks[i] = Math.max(0, mPeaks[i] - decay);
                    }
                    float peakTop = mainBottom - mPeaks[i];
                    canvas.drawRect(left, peakTop - effectiveBarWidth, right, peakTop, mPeakPaint);
                } else if (visualizerType == TYPE_GLOW_PEAK) {
                    canvas.drawRect(left, top, right, bottom, mForePaint);
                    if (height >= mPeaks[i]) {
                        mPeaks[i] = height;
                        mPeakTimes[i] = now;
                    } else {
                        float elapsed = (now - mPeakTimes[i]) / 1000f;
                        float decay = elapsed * getHeight() * 0.6f;
                        mPeaks[i] = Math.max(0, mPeaks[i] - decay);
                        mPeakTimes[i] = now;
                    }
                    float peakTop = mainBottom - mPeaks[i];
                    canvas.drawRect(left, peakTop, right, peakTop + 6f, mPeakPaint);
                } else {
                    canvas.drawRect(left, top, right, bottom, mForePaint);
                }

                if (drawReflection) {
                    float reflectedHeight = Math.min(reflectionSpace, height * mirrorReflectionRatio);
                    float reflectionTop = mainBottom + reflectionGap;
                    float reflectionBottom = reflectionTop + reflectedHeight;
                    drawFadedReflection(canvas, left, reflectionTop, right, reflectionBottom,
                            visualizerType == TYPE_NEON_MODERN ? effectiveBarWidth / 2f : 0f);
                }
            }
        }
        
        if (visualizerType == TYPE_GLOW_PEAK || visualizerType == TYPE_PARTICLE || visualizerType == TYPE_WAVE) {
            postInvalidateDelayed(16);
        }
    }
}
