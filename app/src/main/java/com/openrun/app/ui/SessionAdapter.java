package com.openrun.app.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.openrun.app.R;
import com.openrun.app.data.SessionEntity;
import com.openrun.app.databinding.ItemSessionBinding;
import com.openrun.app.util.Formatters;
import com.openrun.core.model.ActivityType;
import com.openrun.core.stats.GeoMath;

public class SessionAdapter extends ListAdapter<SessionEntity, SessionAdapter.SessionViewHolder> {
    public interface OnSessionClickListener {
        void onSessionClick(SessionEntity session);
    }

    private static final DiffUtil.ItemCallback<SessionEntity> DIFF =
            new DiffUtil.ItemCallback<SessionEntity>() {
                @Override
                public boolean areItemsTheSame(@NonNull SessionEntity oldItem,
                                               @NonNull SessionEntity newItem) {
                    return oldItem.id.equals(newItem.id);
                }

                @Override
                public boolean areContentsTheSame(@NonNull SessionEntity oldItem,
                                                  @NonNull SessionEntity newItem) {
                    return oldItem.updatedAt == newItem.updatedAt;
                }
            };

    private final OnSessionClickListener listener;

    public SessionAdapter(OnSessionClickListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSessionBinding binding = ItemSessionBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new SessionViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        SessionEntity item = getItem(position);
        Context context = holder.binding.getRoot().getContext();
        ActivityType type = ActivityType.fromKey(item.type);

        holder.binding.textType.setText(
                type == ActivityType.WALK ? R.string.activity_walk : R.string.activity_run
        );
        holder.binding.textDate.setText(Formatters.dateTime(context, item.startedAt));
        holder.binding.textDistance.setText(Formatters.distance(context, item.distanceM));
        holder.binding.textDuration.setText(Formatters.duration(item.movingMs));
        holder.binding.textPace.setText(
                Formatters.pace(context, GeoMath.paceSecPerKm(item.distanceM, item.movingMs))
        );

        int containerColor = ContextCompat.getColor(
                context,
                type == ActivityType.WALK
                        ? R.color.brand_accent_container
                        : R.color.brand_primary_container
        );
        int iconColor = ContextCompat.getColor(
                context,
                type == ActivityType.WALK
                        ? R.color.brand_on_accent_container
                        : R.color.brand_on_primary_container
        );
        holder.binding.iconContainer.setBackgroundTintList(
                ColorStateList.valueOf(containerColor)
        );
        holder.binding.iconShoe.setImageTintList(ColorStateList.valueOf(iconColor));
        holder.binding.getRoot().setOnClickListener(v -> {
            if (listener != null) {
                listener.onSessionClick(item);
            }
        });
    }

    static final class SessionViewHolder extends RecyclerView.ViewHolder {
        final ItemSessionBinding binding;

        SessionViewHolder(ItemSessionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
