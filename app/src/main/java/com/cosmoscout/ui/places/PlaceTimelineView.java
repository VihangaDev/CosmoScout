package com.cosmoscout.ui.places;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.cosmoscout.R;

import java.util.List;
public final class PlaceTimelineView extends View {

    private static final int MAX_SEGMENTS = 8;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int[] values = new int[MAX_SEGMENTS];

    private int count;
    private float barSpacing;
    private float cornerRadius;

    public PlaceTimelineView(Context context) {
        super(context);
        init();
    }

    public PlaceTimelineView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PlaceTimelineView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        int trackColor = ContextCompat.getColor(getContext(), R.color.colorOnSurfaceVariant);
        int barColor = ContextCompat.getColor(getContext(), R.color.colorPrimary);
        trackPaint.setColor(trackColor);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(dp(2));
        trackPaint.setAlpha(90);

        barPaint.setColor(barColor);
        barPaint.setStyle(Paint.Style.FILL);

        barSpacing = dp(6);
        cornerRadius = dp(4);
    }

    public void setValues(@Nullable List<Integer> data) {
        count = 0;
        if (data != null && !data.isEmpty()) {
            int max = Math.min(MAX_SEGMENTS, data.size());
            for (int i = 0; i < max; i++) {
                values[i] = clamp(data.get(i));
            }
            count = max;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int drawCount = count > 0 ? count : MAX_SEGMENTS;
        if (count == 0) {
            for (int i = 0; i < drawCount; i++) {
                values[i] = 0;
            }
        }

        float width = getWidth() - getPaddingLeft() - getPaddingRight();
        float height = getHeight() - getPaddingTop() - getPaddingBottom();
        if (width <= 0 || height <= 0) {
            return;
        }
        float totalSpacing = barSpacing * (drawCount - 1);
        float barWidth = (width - totalSpacing) / drawCount;
        if (barWidth <= 0) {
            barWidth = width / drawCount;
        }
        float startX = getPaddingLeft();
        float baseline = getPaddingTop() + height;
        canvas.drawLine(getPaddingLeft(), baseline, getPaddingLeft() + width, baseline, trackPaint);
        for (int i = 0; i < drawCount; i++) {
            float fraction = values[i] / 100f;
            float barHeight = height * fraction;
            float top = baseline - barHeight;
            rect.set(startX, top, startX + barWidth, baseline);
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, barPaint);
            startX += barWidth + barSpacing;
        }
    }

    private int clamp(int value) {
        if (value < 0) return 0;
        if (value > 100) return 100;
        return value;
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
