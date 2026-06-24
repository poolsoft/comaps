package app.organicmaps.carlauncher.voice;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Sesli asistan icin (Siri / Google Assistant tarzi) animasyonlu dalga efekti (Visualizer).
 * Dinleme (Listening) veya Isleme (Processing) modlarina gore dalga boyu ve hizi degisir.
 */
public class VoiceVisualizerView extends View {

    private Paint[] paints;
    private Path[] paths;
    private float phase = 0f;
    private float amplitude = 0f;
    private float targetAmplitude = 0f;
    private float speed = 0.05f;

    private ValueAnimator animator;

    // Renk paleti (Siri tarzÄ± neon renkler)
    private final int[] colors = {
            Color.parseColor("#32c5ff"), // AÃ§Ä±k mavi
            Color.parseColor("#ff3274"), // Pembe
            Color.parseColor("#32ff8d"), // YeÅŸil
            Color.parseColor("#9e32ff")  // Mor
    };

    public VoiceVisualizerView(Context context) {
        super(context);
        init();
    }

    public VoiceVisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        int waveCount = 4;
        paints = new Paint[waveCount];
        paths = new Path[waveCount];

        for (int i = 0; i < waveCount; i++) {
            paints[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
            paints[i].setStyle(Paint.Style.STROKE);
            paints[i].setStrokeWidth(8f - i); // Arkadaki dalgalar daha ince
            paints[i].setStrokeCap(Paint.Cap.ROUND);
            paths[i] = new Path();
        }

        // Animasyon motoru
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            phase += speed;
            // Amplitudu yumusakca hedefe dogru gotur (interpolasyon)
            amplitude += (targetAmplitude - amplitude) * 0.1f;
            invalidate();
        });
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Genislik ve yukseklik belli oldugunda gradientleri ayarla
        for (int i = 0; i < paints.length; i++) {
            LinearGradient gradient = new LinearGradient(
                    0, 0, w, 0,
                    new int[]{colors[i], colors[(i + 1) % colors.length]},
                    null,
                    Shader.TileMode.MIRROR
            );
            paints[i].setShader(gradient);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (amplitude < 0.01f) {
            return; // Eger ses yoksa hic cizme (gÃ¶rÃ¼nmez)
        }

        float w = getWidth();
        float h = getHeight();
        float midH = h / 2f;

        for (int i = 0; i < paths.length; i++) {
            paths[i].reset();
            
            // Dalga parametreleri
            float frequency = 1.5f + (i * 0.5f);
            float currentPhase = phase * (i % 2 == 0 ? 1 : -1) * (i + 1); // Farkli yonlere kaysinlar
            float waveAmplitude = amplitude * (midH * 0.6f) * (1f - (i * 0.15f)); 

            paths[i].moveTo(0, midH);

            for (float x = 0; x <= w; x += 5) {
                // Ekranin ortasinda en yuksek, kenarlarda sifir olan bir genlik katsayisi (Hanning window)
                float normalizedX = x / w;
                float edgeFactor = (float) Math.sin(normalizedX * Math.PI);
                
                float y = (float) Math.sin(normalizedX * Math.PI * frequency + currentPhase);
                
                paths[i].lineTo(x, midH + (y * waveAmplitude * edgeFactor));
            }

            canvas.drawPath(paths[i], paints[i]);
        }
    }

    /**
     * Dinleme moduna alir (Dalgalar sakince dalgalanir)
     */
    public void startListening() {
        targetAmplitude = 0.6f;
        speed = 0.08f;
        if (!animator.isRunning()) {
            animator.start();
        }
        setVisibility(VISIBLE);
    }

    /**
     * Komut isleme moduna alir (Dalgalar cok hizli ve yuksek dalgalanir)
     */
    public void startProcessing() {
        targetAmplitude = 1.0f;
        speed = 0.2f;
        if (!animator.isRunning()) {
            animator.start();
        }
        setVisibility(VISIBLE);
    }

    /**
     * Animasyonu durdurur ve gizler
     */
    public void stop() {
        targetAmplitude = 0f;
        // Animasyonu hemen durdurmuyoruz, yumusakca kuculsun (onDraw'da amplitude 0 olunca return edecek)
        postDelayed(() -> {
            if (targetAmplitude == 0f) {
                animator.cancel();
                setVisibility(GONE);
            }
        }, 1000);
    }
}
