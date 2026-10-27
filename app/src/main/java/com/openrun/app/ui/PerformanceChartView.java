package com.openrun.app.ui;

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
 *
 * <p>The chart geometry and axis labels are rebuilt only when the data or the
 * view size changes. Drawing itself only replays the cached paths, which keeps
 * scrolling the detail screen smooth even while the chart is on screen.</p>
 */
public class PerformanceChartView extends View {
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path linePath = new Path();
    private final Path fillPath = new Path();
    private final String[] yLabels = new String[3];
    private final float[] gridX = new float[3];
    private final float[] gridY = new float[3];
    private float density;
    private float scaledDensity;

    private String title = "";
    private String unit = "";
    private float[] xs = new float[0];
    private float[] ys = new float[0];
    private boolean paceChart;
    private int lineColor;

    private boolean chartReady;
    private boolean hasData;
    private float chartLeft;
    private float chartRight;
    private float chartTop;
    private float chartBottom;
    private float lastX;
    private float lastY;
    private String xStartLabel = "";
    private String xMiddleLabel = "";
    private String xEndLabel = "";

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
        density = getResources().getDisplayMetrics().density;
        scaledDensity = getResources().getDisplayMetrics().scaledDensity;
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
        rebuildChart();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        rebuildChart();
    }

    private void rebuildChart() {
        linePath.rewind();
        fillPath.rewind();
        fillPaint.setShader(null);
        chartReady = false;
        hasData = false;

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        chartLeft = dp(48f);
        chartRight = width - dp(10f);
        chartTop = dp(30f);
        chartBottom = height - dp(24f);
        if (chartRight <= chartLeft || chartBottom <= chartTop) {
            return;
        }
        chartReady = true;

        int count = Math.min(xs.length, ys.length);
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float maxX = 0f;
        int validSamples = 0;
        for (int i = 0; i < count; i++) {
            if (!isFinite(xs[i]) || !isFinite(ys[i])) {
                continue;
            }
            minY = Math.min(minY, ys[i]);
            maxY = Math.max(maxY, ys[i]);
            maxX = Math.max(maxX, xs[i]);
            validSamples++;
        }
        if (validSamples < 2) {
            return;
        }
        if (maxX <= 0f) {
            maxX = 1f;
        }
        if (maxY - minY < 0.01f) {
            float padding = paceChart ? 10f : 2f;
            minY -= padding;
            maxY += padding;
        } else {
            float padding = (maxY - minY) * 0.08f;
            minY -= padding;
            maxY += padding;
        }

        for (int i = 0; i < 3; i++) {
            float fraction = i / 2f;
            gridY[i] = chartTop + (chartBottom - chartTop) * fraction;
            gridX[i] = chartLeft + (chartRight - chartLeft) * fraction;
            yLabels[i] = formatY(valueAtY(gridY[i], minY, maxY));
        }
        xStartLabel = formatX(0f);
        xMiddleLabel = formatX(maxX / 2f);
        xEndLabel = formatX(maxX);

        boolean firstPoint = true;
        float firstX = 0f;
        for (int i = 0; i < count; i++) {
            float xValue = xs[i];
            float yValue = ys[i];
            if (!isFinite(xValue) || !isFinite(yValue)) {
                continue;
            }
            float x = chartLeft + (xValue / maxX) * (chartRight - chartLeft);
            float normalized = (yValue - minY) / (maxY - minY);
            float y = paceChart
                    ? chartTop + normalized * (chartBottom - chartTop)
                    : chartBottom - normalized * (chartBottom - chartTop);
            if (firstPoint) {
                linePath.moveTo(x, y);
                firstX = x;
                firstPoint = false;
            } else {
                linePath.lineTo(x, y);
            }
            lastX = x;
            lastY = y;
        }
        if (firstPoint) {
            return;
        }

        fillPath.set(linePath);
        fillPath.lineTo(lastX, chartBottom);
        fillPath.lineTo(firstX, chartBottom);
        fillPath.close();
        fillPaint.setShader(new LinearGradient(
                0,
                chartTop,
                0,
                chartBottom,
                ColorUtils.setAlphaComponent(lineColor, 95),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
        ));
        hasData = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!chartReady) {
            return;
        }

        canvas.drawText(title, dp(2f), dp(18f), titlePaint);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(unit, chartRight, dp(18f), textPaint);

        if (!hasData) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(
                    getContext().getString(R.string.chart_no_data),
                    (chartLeft + chartRight) / 2f,
                    (chartTop + chartBottom) / 2f,
                    textPaint
            );
            return;
        }

        for (int i = 0; i < 3; i++) {
            canvas.drawLine(chartLeft, gridY[i], chartRight, gridY[i], gridPaint);
            textPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(
                    yLabels[i],
                    chartLeft - dp(6f),
                    gridY[i] + dp(3.5f),
                    textPaint
            );
        }
        for (int i = 0; i < 3; i++) {
            canvas.drawLine(gridX[i], chartTop, gridX[i], chartBottom, gridPaint);
        }

        float labelBaseline = getHeight() - dp(5f);
        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(xStartLabel, chartLeft, labelBaseline, textPaint);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(xMiddleLabel, (chartLeft + chartRight) / 2f, labelBaseline, textPaint);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(xEndLabel, chartRight, labelBaseline, textPaint);

        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(linePath, linePaint);
        canvas.drawCircle(lastX, lastY, dp(3.5f), dotPaint);
    }

    private float valueAtY(float y, float minY, float maxY) {
        float normalized = (y - chartTop) / (chartBottom - chartTop);
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

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private float dp(float value) {
        return value * density;
    }

    private float sp(float value) {
        return value * scaledDensity;
    }
}
