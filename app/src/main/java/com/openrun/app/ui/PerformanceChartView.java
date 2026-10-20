package com.openrun.app.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.openrun.app.R;

import java.util.Locale;

/**
 * Small dependency-free line/area chart used for pace and elevation.
 */
public class PerformanceChartView extends View {
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private String title = "";
    private String unit = "";
    private float[] xs = new float[0];
    private float[] ys = new float[0];
    private boolean paceChart;
    private int lineColor;

    public PerformanceChartView(Context context) {
        super(context);
        init();
    }

    public PerformanceChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PerformanceChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        int muted = ContextCompat.getColor(getContext(), R.color.on_surface_variant);
        int grid = ContextCompat.getColor(getContext(), R.color.surface_variant);
        int ink = ContextCompat.getColor(getContext(), R.color.on_surface);
        lineColor = ContextCompat.getColor(getContext(), R.color.brand_primary);

        gridPaint.setColor(grid);
        gridPaint.setStrokeWidth(dp(1f));

        textPaint.setColor(muted);
        textPaint.setTextSize(sp(10f));
        textPaint.setTextAlign(Paint.Align.RIGHT);

        titlePaint.setColor(ink);
        titlePaint.setTextSize(sp(15f));
        titlePaint.setTypeface(android.graphics.Typeface.create(
                "sans-serif",
                android.graphics.Typeface.BOLD
        ));

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(3f));
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        fillPaint.setStyle(Paint.Style.FILL);

        dotPaint.setStyle(Paint.Style.FILL);
    }

    public void setData(String title,
                        String unit,
                        float[] xs,
                        float[] ys,
                        boolean paceChart,
                        int lineColor) {
        this.title = title == null ? "" : title;
        this.unit = unit == null ? "" : unit;
        this.xs = xs == null ? new float[0] : xs;
        this.ys = ys == null ? new float[0] : ys;
        this.paceChart = paceChart;
        this.lineColor = lineColor;
        linePaint.setColor(lineColor);
        dotPaint.setColor(lineColor);
        setContentDescription(this.title + ", " + this.unit);
        invalidate();
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = dp(48f);
        float right = getWidth() - dp(10f);
        float top = dp(30f);
        float bottom = getHeight() - dp(24f);
        if (right <= left || bottom <= top) {
            return;
        }

        canvas.drawText(title, dp(2f), dp(18f), titlePaint);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(unit, right, dp(18f), textPaint);

        if (xs.length < 2 || ys.length < 2) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(
                    getContext().getString(R.string.chart_no_data),
                    (left + right) / 2f,
                    (top + bottom) / 2f,
                    textPaint
            );
            return;
        }

        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float maxX = 0f;
        for (int i = 0; i < ys.length; i++) {
            minY = Math.min(minY, ys[i]);
            maxY = Math.max(maxY, ys[i]);
        }
        for (float x : xs) {
            maxX = Math.max(maxX, x);
        }
        if (maxX <= 0f) {
            maxX = 1f;
        }
        if (maxY - minY < 0.01f) {
            float pad = paceChart ? 10f : 2f;
            minY -= pad;
            maxY += pad;
        } else {
            float padding = (maxY - minY) * 0.08f;
            minY -= padding;
            maxY += padding;
        }

        for (int i = 0; i < 3; i++) {
            float y = top + (bottom - top) * i / 2f;
            canvas.drawLine(left, y, right, y, gridPaint);
            float value = valueAtY(y, top, bottom, minY, maxY);
            textPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(formatY(value), left - dp(6f), y + dp(3.5f), textPaint);
        }
        for (int i = 0; i < 3; i++) {
            float x = left + (right - left) * i / 2f;
            canvas.drawLine(x, top, x, bottom, gridPaint);
        }

        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(formatX(0f), left, getHeight() - dp(5f), textPaint);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(formatX(maxX / 2f), (left + right) / 2f, getHeight() - dp(5f), textPaint);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(formatX(maxX), right, getHeight() - dp(5f), textPaint);

        Path line = new Path();
        float firstX = 0f;
        float lastX = 0f;
        for (int i = 0; i < xs.length; i++) {
            float x = left + (xs[i] / maxX) * (right - left);
            float normalized = (ys[i] - minY) / (maxY - minY);
            float y = paceChart
                    ? top + normalized * (bottom - top)
                    : bottom - normalized * (bottom - top);
            if (i == 0) {
                line.moveTo(x, y);
                firstX = x;
            } else {
                line.lineTo(x, y);
            }
            lastX = x;
        }

        Path fill = new Path(line);
        fill.lineTo(lastX, bottom);
        fill.lineTo(firstX, bottom);
        fill.close();
        fillPaint.setShader(new LinearGradient(
                0,
                top,
                0,
                bottom,
                ColorUtils.setAlphaComponent(lineColor, 95),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
        ));
        canvas.drawPath(fill, fillPaint);

        canvas.drawPath(line, linePaint);
        float lastNormalized = (ys[ys.length - 1] - minY) / (maxY - minY);
        float lastY = paceChart
                ? top + lastNormalized * (bottom - top)
                : bottom - lastNormalized * (bottom - top);
        canvas.drawCircle(lastX, lastY, dp(3.5f), dotPaint);
    }

    private float valueAtY(float y, float top, float bottom, float minY, float maxY) {
        float normalized = (y - top) / (bottom - top);
        if (paceChart) {
            return minY + normalized * (maxY - minY);
        }
        return maxY - normalized * (maxY - minY);
    }

    private String formatY(float value) {
        if (paceChart) {
            int totalSeconds = Math.max(0, Math.round(value));
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;
            return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
        }
        return String.valueOf(Math.round(value));
    }

    private String formatX(float km) {
        return String.format(Locale.getDefault(), "%.1f", km) + " km";
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
