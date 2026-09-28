package com.qrscanner;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

public class AdFooterView extends FrameLayout {

    private static final long REFRESH_MS = 60_000L;
    private static final long SCROLL_MS = 25_000L;
    private static final long GRADIENT_MS = 6_000L;
    private static final String SEP = "        ";

    private static final int[] GRADIENT_COLORS = {
        0xffdc2626, 0xffd97706, 0xff16a34a, 0xff1d4ed8, 0xff9333ea, 0xffdc2626
    };

    private TextView tvAdText;
    private GradientDrawable gradient;
    private ObjectAnimator scrollAnimator;
    private ValueAnimator gradientAnimator;
    private float gradientShift = 0f;

    private String currentText = "";
    private String currentLink = "";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            AdPoller.pollAsync(getContext(), entries -> refreshAll());
            applyCurrent();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    private static final java.util.List<AdFooterView> INSTANCES = new java.util.ArrayList<>();

    /** 所有已挂载的广告栏立即重新匹配当前小时。 */
    public static void refreshAll() {
        for (AdFooterView v : new java.util.ArrayList<>(INSTANCES)) {
            if (v.isAttachedToWindow()) v.applyCurrent();
        }
    }

    public AdFooterView(Context context) {
        this(context, null);
    }

    public AdFooterView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AdFooterView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        inflate(context, R.layout.view_ad_footer, this);
        tvAdText = findViewById(R.id.tvAdText);
        gradient = new GradientDrawable();
        setBackground(gradient);
        applyCurrent();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        INSTANCES.add(this);
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(ticker);
        INSTANCES.remove(this);
        cancelScroll();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0) {
            gradient.setWidth(w);
            startGradient(w);
            buildMarquee(w);
        }
    }

    // ======================== 文案 ========================

    private void applyCurrent() {
        AdEntry entry = AdStore.current(getContext());
        setAd(entry.text, entry.link);
    }

    public void setAd(String text, String link) {
        String t = text == null ? "" : text.trim();
        String l = link == null ? "" : link.trim();
        if (t.equals(currentText) && l.equals(currentLink)) return;
        currentText = t;
        currentLink = l;
        setClickable(!l.isEmpty());
        if (getWidth() > 0) buildMarquee(getWidth());
    }

    private void buildMarquee(int viewWidth) {
        cancelScroll();
        if (currentText.isEmpty()) {
            tvAdText.setText(currentText);
            return;
        }
        tvAdText.setTranslationX(0f);
        String unit = currentText + SEP;

        tvAdText.setText(unit);
        tvAdText.measure(
            MeasureSpec.makeMeasureSpec(viewWidth, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int unitWidth = tvAdText.getMeasuredWidth();
        if (unitWidth <= 0) {
            tvAdText.setText(currentText);
            return;
        }

        int copies = Math.max(2, (int) Math.ceil((2.0 * viewWidth) / unitWidth) + 1);
        StringBuilder sb = new StringBuilder(unit.length() * copies);
        for (int i = 0; i < copies; i++) sb.append(unit);
        tvAdText.setText(sb.toString());

        scrollAnimator = ObjectAnimator.ofFloat(tvAdText, View.TRANSLATION_X, 0f, -unitWidth);
        scrollAnimator.setDuration(SCROLL_MS);
        scrollAnimator.setInterpolator(new LinearInterpolator());
        scrollAnimator.setRepeatCount(ValueAnimator.INFINITE);
        scrollAnimator.start();
    }

    private void cancelScroll() {
        if (scrollAnimator != null) {
            scrollAnimator.cancel();
            scrollAnimator = null;
        }
    }

    // ======================== 渐变背景 ========================

    private void startGradient(int width) {
        if (gradientAnimator != null) gradientAnimator.cancel();
        gradientAnimator = ValueAnimator.ofFloat(0f, 1f);
        gradientAnimator.setDuration(GRADIENT_MS);
        gradientAnimator.setRepeatCount(ValueAnimator.INFINITE);
        gradientAnimator.setRepeatMode(ValueAnimator.REVERSE);
        gradientAnimator.addUpdateListener(a -> {
            gradientShift = -width * a.getAnimatedFraction();
            gradient.setShift(gradientShift);
        });
        gradientAnimator.start();
    }

    // ======================== 点击跳转 ========================

    @Override
    public boolean performClick() {
        if (currentLink.isEmpty()) return super.performClick();
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(currentLink));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        } catch (Exception ignored) {
        }
        return true;
    }

    private static class GradientDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix matrix = new Matrix();
        private LinearGradient shader;
        private int width = 1;
        private float shift = 0f;

        void setWidth(int w) {
            if (w > 0) {
                width = w;
                rebuild();
            }
        }

        void setShift(float value) {
            this.shift = value;
            if (shader == null) return;
            matrix.reset();
            matrix.setTranslate(this.shift, 0f);
            shader.setLocalMatrix(matrix);
            invalidateSelf();
        }

        private void rebuild() {
            shader = new LinearGradient(0, 0, width, 0, GRADIENT_COLORS, null, Shader.TileMode.CLAMP);
            paint.setShader(shader);
            matrix.reset();
            matrix.setTranslate(shift, 0f);
            shader.setLocalMatrix(matrix);
        }

        @Override
        public void draw(Canvas canvas) {
            if (shader == null) rebuild();
            canvas.drawRect(getBounds(), paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter cf) {
            paint.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.OPAQUE;
        }
    }
}
