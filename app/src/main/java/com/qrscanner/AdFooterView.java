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
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

public class AdFooterView extends FrameLayout {

    private static final long REFRESH_MS = 60_000L;
    private static final long GRADIENT_MS = 6_000L;
    private static final float SCROLL_SPEED = 0.07f;
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
        setOnClickListener(v -> performClick());
        setOnLongClickListener(v -> {
            if (!currentLink.isEmpty()) openLink(); else showSourceDialog();
            return true;
        });
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

        float est = tvAdText.getPaint().measureText(unit);
        if (est <= 0f) {
            tvAdText.setText(currentText);
            return;
        }
        int copies = (int) Math.ceil((2.0 * viewWidth) / est) + 2;
        StringBuilder sb = new StringBuilder(unit.length() * copies);
        for (int i = 0; i < copies; i++) sb.append(unit);
        tvAdText.setText(sb.toString());
        tvAdText.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int laidWidth = tvAdText.getMeasuredWidth();
        if (laidWidth <= 0) {
            tvAdText.setText(currentText);
            return;
        }

        float period = (float) laidWidth / copies;
        scrollAnimator = ObjectAnimator.ofFloat(tvAdText, View.TRANSLATION_X, 0f, -period);
        long duration = Math.min(45_000L, Math.max(5_000L, (long) (period / SCROLL_SPEED)));
        scrollAnimator.setDuration(duration);
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

    /**
     * 无链接时，长按广告栏打开来源诊断，便于确认联网更新是否真的生效。
     * 有链接时点击/长按都是直接跳到指定链接。
     */
    private void showSourceDialog() {
        Context ctx = getContext();
        StringBuilder sb = new StringBuilder();
        boolean cached = AdStore.hasRemoteCache(ctx);
        sb.append(cached ? ctx.getString(R.string.ad_diag_cached) : ctx.getString(R.string.ad_diag_builtin));
        sb.append('\n').append(ctx.getString(R.string.ad_diag_active, AdStore.getRemoteActive(ctx)));

        String[] sources = AdStore.sources(ctx);
        if (sources.length > 1) {
            for (String s : sources) {
                sb.append('\n').append(ctx.getString(R.string.ad_diag_source, s));
            }
        }

        long t = AdStore.getRemoteTime(ctx);
        if (cached && t > 0L) {
            long min = (System.currentTimeMillis() - t) / 60000L;
            sb.append('\n').append(min < 1L
                ? ctx.getString(R.string.ad_just_now)
                : ctx.getString(R.string.ad_minutes_ago, min));
        }

        String err = AdStore.getRemoteError(ctx);
        if (!err.isEmpty()) {
            sb.append('\n').append(ctx.getString(R.string.ad_diag_error, err));
        }
        if (currentLink.isEmpty()) {
            sb.append('\n').append(ctx.getString(R.string.ad_diag_nolink));
        }

        new AlertDialog.Builder(ctx)
            .setTitle(R.string.ad_diag_title)
            .setMessage(sb.toString())
            .setPositiveButton(R.string.ad_diag_fetch_now, (d, w) ->
                AdPoller.pollAsync(ctx, entries -> {
                    refreshAll();
                    showResult(ctx, entries);
                }))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showResult(Context ctx, java.util.List<AdEntry> entries) {
        Toast.makeText(ctx,
            entries.isEmpty() ? ctx.getString(R.string.ad_diag_failed)
                              : ctx.getString(R.string.ad_diag_ok, entries.size()),
            Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean performClick() {
        if (currentLink.isEmpty()) {
            Toast.makeText(getContext(), R.string.ad_no_link, Toast.LENGTH_SHORT).show();
            return true;
        }
        openLink();
        return true;
    }

    /** 打开当前广告的跳转链接（浏览器/外部应用）。 */
    private void openLink() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(currentLink));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            getContext().startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(getContext(),
                e.getMessage() == null ? currentLink : e.getMessage(), Toast.LENGTH_LONG).show();
        }
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
