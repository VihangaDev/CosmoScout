package com.cosmoscout.ui.events;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.cosmoscout.R;
import com.cosmoscout.data.events.DonkiNotification;
import com.cosmoscout.databinding.ItemEventBinding;

import java.util.Date;
import java.util.Locale;

public class EventsAdapter extends ListAdapter<DonkiNotification, EventsAdapter.EventViewHolder> {

    public EventsAdapter() {
        super(DIFF_CALLBACK);
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemEventBinding binding = ItemEventBinding.inflate(inflater, parent, false);
        return new EventViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getId().hashCode();
    }

    private static final DiffUtil.ItemCallback<DonkiNotification> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<DonkiNotification>() {
                @Override
                public boolean areItemsTheSame(@NonNull DonkiNotification oldItem,
                                               @NonNull DonkiNotification newItem) {
                    return oldItem.getId().equals(newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull DonkiNotification oldItem,
                                                  @NonNull DonkiNotification newItem) {
                    return oldItem.getTimestampMillis() == newItem.getTimestampMillis()
                            && oldItem.getClassification().equals(newItem.getClassification())
                            && equalsNullable(oldItem.getDetailLine(), newItem.getDetailLine())
                            && oldItem.getType().equals(newItem.getType());
                }
            };

    private static boolean equalsNullable(@Nullable String a, @Nullable String b) {
        if (a == null) {
            return b == null;
        }
        return a.equals(b);
    }

    static final class EventViewHolder extends RecyclerView.ViewHolder {

        private final ItemEventBinding binding;

        EventViewHolder(@NonNull ItemEventBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull DonkiNotification event) {
            Context context = binding.getRoot().getContext();

            String time = formatTimestamp(context, event.getTimestampMillis());
            binding.eventTime.setText(time);

            binding.eventClassification.setText(event.getClassification());

            if (event.getDetailLine() == null || event.getDetailLine().isEmpty()) {
                binding.eventDetail.setVisibility(View.GONE);
            } else {
                binding.eventDetail.setVisibility(View.VISIBLE);
                binding.eventDetail.setText(event.getDetailLine());
            }

            binding.eventSource.setText(context.getString(
                    R.string.events_source_meta,
                    event.getSource(),
                    event.getId()));

            binding.eventTypeChip.setText(event.getType());
            binding.eventTypeChip.setChipBackgroundColor(ColorStateList.valueOf(
                    ContextCompat.getColor(context, backgroundForType(event.getType()))));
            binding.eventTypeChip.setTextColor(ContextCompat.getColor(
                    context, textColorForType(event.getType())));
            binding.eventTypeChip.setContentDescription(context.getString(
                    R.string.events_chip_accessibility, event.getType()));

            binding.executePendingBindings();
        }

        private String formatTimestamp(@NonNull Context context, long millis) {
            java.text.DateFormat timeFormat = DateFormat.getTimeFormat(context);
            Date date = new Date(millis);
            return timeFormat.format(date);
        }

        private int backgroundForType(@NonNull String type) {
            switch (type.toUpperCase(Locale.US)) {
                case "CME":
                    return R.color.status_ok_bg;
                case "SEP":
                    return R.color.status_good_bg;
                case "GEOMAGNETIC":
                    return R.color.status_poor_bg;
                default:
                    return R.color.status_pending_bg;
            }
        }

        private int textColorForType(@NonNull String type) {
            switch (type.toUpperCase(Locale.US)) {
                case "CME":
                    return R.color.colorPrimary;
                case "SEP":
                    return R.color.colorSecondary;
                case "GEOMAGNETIC":
                    return R.color.status_poor_text;
                default:
                    return R.color.colorOnSurface;
            }
        }
    }
}
