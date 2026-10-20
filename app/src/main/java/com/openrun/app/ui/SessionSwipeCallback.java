package com.openrun.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.openrun.app.R;

/**
 * Swipe-left helper for the history list. The red background, trash icon and
 * label are drawn behind the card while the user drags it sideways.
 */
public final class SessionSwipeCallback extends ItemTouchHelper.SimpleCallback {
    public interface OnSwipeListener {
        void onSwiped(int position);
    }

    private final OnSwipeListener listener;
    private final Drawable deleteIcon;
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final String deleteLabel;
    private final float cornerRadius;
    private final float iconSize;
    private final float horizontalPadding;
    private final float iconGap;

    public SessionSwipeCallback(Context context, OnSwipeListener listener) {
        super(0, ItemTouchHelper.LEFT);
        this.listener = listener;

        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        float density = metrics.density;
        cornerRadius = 20f * density;
        iconSize = 22f * density;
        horizontalPadding = 20f * density;
        iconGap = 8f * density;

        backgroundPaint.setColor(ContextCompat.getColor(context, R.color.delete_action));
        labelPaint.setColor(ContextCompat.getColor(context, R.color.on_delete_action));
        labelPaint.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                14f,
                metrics
        ));
        labelPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        labelPaint.setTextAlign(Paint.Align.RIGHT);
        deleteLabel = context.getString(R.string.delete_activity);

        Drawable icon = ContextCompat.getDrawable(context, R.drawable.ic_delete);
        if (icon != null) {
            icon = DrawableCompat.wrap(icon).mutate();
            DrawableCompat.setTint(
                    icon,
                    ContextCompat.getColor(context, R.color.on_delete_action)
            );
        }
        deleteIcon = icon;
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView,
                          @NonNull RecyclerView.ViewHolder viewHolder,
                          @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        // The callback is posted after the success animation, which leaves the
        // row translated off-screen. Bring it back before showing the
        // confirmation dialog so canceling does not leave a blank row.
        viewHolder.itemView.setTranslationX(0f);
        viewHolder.itemView.setTranslationY(0f);
        int position = viewHolder.getBindingAdapterPosition();
        if (position != RecyclerView.NO_POSITION) {
            listener.onSwiped(position);
        }
    }

    @Override
    public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
        return 0.32f;
    }

    @Override
    public void onChildDraw(@NonNull Canvas canvas,
                            @NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder,
                            float dX,
                            float dY,
                            int actionState,
                            boolean isCurrentlyActive) {
        View itemView = viewHolder.itemView;
        if (dX < 0 && itemView.getWidth() > 0) {
            RectF background = new RectF(
                    itemView.getLeft(),
                    itemView.getTop(),
                    itemView.getRight(),
                    itemView.getBottom()
            );
            canvas.drawRoundRect(background, cornerRadius, cornerRadius, backgroundPaint);

            float progress = Math.min(1f, -dX / (itemView.getWidth() * 0.35f));
            int alpha = (int) (255f * progress);
            float centerY = (itemView.getTop() + itemView.getBottom()) / 2f;
            float iconRight = itemView.getRight() - horizontalPadding;
            float iconLeft = iconRight - iconSize;

            if (deleteIcon != null) {
                deleteIcon.setBounds(
                        Math.round(iconLeft),
                        Math.round(centerY - iconSize / 2f),
                        Math.round(iconRight),
                        Math.round(centerY + iconSize / 2f)
                );
                deleteIcon.setAlpha(alpha);
                deleteIcon.draw(canvas);
            }

            labelPaint.setAlpha(alpha);
            float textBaseline = centerY
                    - (labelPaint.ascent() + labelPaint.descent()) / 2f;
            canvas.drawText(
                    deleteLabel,
                    iconLeft - iconGap,
                    textBaseline,
                    labelPaint
            );
        }
        super.onChildDraw(
                canvas,
                recyclerView,
                viewHolder,
                dX,
                dY,
                actionState,
                isCurrentlyActive
        );
    }
}
