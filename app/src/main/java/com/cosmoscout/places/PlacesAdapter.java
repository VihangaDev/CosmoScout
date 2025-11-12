package com.cosmoscout.places;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.cosmoscout.R;
import com.cosmoscout.places.PlacesController.PlaceSkyState;
import com.cosmoscout.places.ui.PlaceTimelineView;
import com.google.android.material.chip.Chip;
import com.google.android.material.button.MaterialButton;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public final class PlacesAdapter extends ListAdapter<PlacesController.UiPlace, PlacesAdapter.PlaceViewHolder> {

    public interface PlaceActionListener {
        void onShowDetails(@NonNull PlacesController.UiPlace uiPlace);
        void onMap(@NonNull Place place);
        void onRoute(@NonNull Place place);
        void onDelete(@NonNull Place place);
        void onSetPrimary(@NonNull Place place);
    }

    @NonNull
    private final PlaceActionListener listener;

    public PlacesAdapter(@NonNull PlaceActionListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public PlaceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_place_rich, parent, false);
        return new PlaceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaceViewHolder holder, int position) {
        PlacesController.UiPlace item = getItem(position);
        holder.bind(item, listener);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).place.getStableId();
    }

    private static final DiffUtil.ItemCallback<PlacesController.UiPlace> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<PlacesController.UiPlace>() {
                @Override
                public boolean areItemsTheSame(@NonNull PlacesController.UiPlace oldItem,
                                               @NonNull PlacesController.UiPlace newItem) {
                    return oldItem.place.getId().equals(newItem.place.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull PlacesController.UiPlace oldItem,
                                                  @NonNull PlacesController.UiPlace newItem) {
                    boolean skyEqual;
                    if (oldItem.sky == null && newItem.sky == null) {
                        skyEqual = true;
                    } else if (oldItem.sky != null && newItem.sky != null) {
                        skyEqual = oldItem.sky.score == newItem.sky.score
                                && oldItem.sky.status == newItem.sky.status
                                && oldItem.sky.updatedAt == newItem.sky.updatedAt
                                && oldItem.sky.clearPct == newItem.sky.clearPct
                                && oldItem.sky.moonPct == newItem.sky.moonPct
                                && oldItem.sky.windowStart == newItem.sky.windowStart
                                && oldItem.sky.windowEnd == newItem.sky.windowEnd;
                    } else {
                        skyEqual = false;
                    }

                    return skyEqual
                            && oldItem.isPrimary == newItem.isPrimary
                            && Objects.equals(oldItem.distanceKm, newItem.distanceKm);
                }
            };

    static final class PlaceViewHolder extends RecyclerView.ViewHolder {

        private final View cardRoot;
        private final TextView titleView;
        private final TextView primaryBadge;
        private final TextView statusView;
        private final Chip windowChip;
        private final TextView metricsView;
        private final PlaceTimelineView timelineView;
        private final TextView updatedView;
        private final MaterialButton mapButton;
        private final MaterialButton routeButton;
        private final MaterialButton deleteButton;
        private final ImageButton overflowButton;
        private long lastUpdated;

        PlaceViewHolder(@NonNull View itemView) {
            super(itemView);
            cardRoot = itemView;
            titleView = itemView.findViewById(R.id.placeTitle);
            primaryBadge = itemView.findViewById(R.id.primaryBadge);
            statusView = itemView.findViewById(R.id.statusPill);
            windowChip = itemView.findViewById(R.id.bestWindowChip);
            metricsView = itemView.findViewById(R.id.placeMetrics);
            timelineView = itemView.findViewById(R.id.placeTimeline);
            updatedView = itemView.findViewById(R.id.updatedLabel);
            mapButton = itemView.findViewById(R.id.mapButton);
            routeButton = itemView.findViewById(R.id.routeButton);
            deleteButton = itemView.findViewById(R.id.deleteButton);
            overflowButton = itemView.findViewById(R.id.quickMenuButton);
        }

        void bind(@NonNull PlacesController.UiPlace uiPlace,
                  @NonNull PlaceActionListener listener) {
            Place place = uiPlace.place;
            titleView.setText(place.getName());

            primaryBadge.setVisibility(uiPlace.isPrimary ? View.VISIBLE : View.GONE);

            PlaceSkyState state = uiPlace.sky;
            if (state != null) {
                bindStatus(state);
                bindWindow(state, place);
                bindMetrics(state, uiPlace.distanceKm, place);
                bindUpdated(state);
                timelineView.setValues(state.timeline);
            } else {
                statusView.setText(R.string.details);
                statusView.setBackgroundResource(R.drawable.bg_status_pending);
                statusView.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.colorOnSurfaceVariant));
                windowChip.setText(R.string.best_window_format);
                metricsView.setText(formatCoords(place));
                updatedView.setText(R.string.couldnt_fetch);
                timelineView.setValues(null);
            }

            cardRoot.setOnClickListener(v -> listener.onShowDetails(uiPlace));
            cardRoot.setOnLongClickListener(v -> {
                showQuickMenu(v, place, listener);
                return true;
            });
            mapButton.setOnClickListener(v -> listener.onMap(place));
            routeButton.setOnClickListener(v -> listener.onRoute(place));
            deleteButton.setOnClickListener(v -> listener.onDelete(place));
            overflowButton.setOnClickListener(v -> showQuickMenu(v, place, listener));
        }

        private void bindStatus(@NonNull PlaceSkyState state) {
            int textRes;
            int backgroundRes;
            int textColorRes;
            switch (state.status) {
                case GOOD:
                    textRes = R.string.sky_good;
                    backgroundRes = R.drawable.bg_status_good;
                    textColorRes = R.color.colorSecondary;
                    break;
                case OK:
                    textRes = R.string.sky_ok;
                    backgroundRes = R.drawable.bg_status_ok;
                    textColorRes = R.color.colorPrimary;
                    break;
                default:
                    textRes = R.string.sky_poor;
                    backgroundRes = R.drawable.bg_status_poor;
                    textColorRes = R.color.status_poor_text;
                    break;
            }
            statusView.setText(textRes);
            statusView.setBackgroundResource(backgroundRes);
            statusView.setTextColor(ContextCompat.getColor(itemView.getContext(), textColorRes));
        }

        private void bindWindow(@NonNull PlaceSkyState state, @NonNull Place place) {
            Context context = itemView.getContext();
            java.text.DateFormat formatter = android.text.format.DateFormat.getTimeFormat(context);
            formatter.setTimeZone(state.timezone);
            String start = formatter.format(new Date(state.windowStart));
            String end = formatter.format(new Date(state.windowEnd));
            windowChip.setText(context.getString(R.string.best_window_format, start, end));
        }

        private void bindMetrics(@NonNull PlaceSkyState state,
                                 @Nullable Double distanceKm,
                                 @NonNull Place place) {
            StringBuilder builder = new StringBuilder();
            builder.append(itemView.getContext().getString(R.string.clear_pct, state.clearPct));
            builder.append(" • ");
            builder.append(itemView.getContext().getString(R.string.moon_pct, state.moonPct));
            builder.append(" • ");
            if (distanceKm != null) {
                builder.append(itemView.getContext().getString(
                        R.string.distance_away,
                        String.format(Locale.getDefault(), "%.1f", distanceKm)
                ));
            } else {
                builder.append(formatCoords(place));
            }
            metricsView.setText(builder.toString());
        }

        private void bindUpdated(@NonNull PlaceSkyState state) {
            if (lastUpdated != state.updatedAt) {
                updatedView.setAlpha(0f);
                updatedView.animate().alpha(1f).setDuration(200L).start();
            }
            lastUpdated = state.updatedAt;
            long minutes = Math.max(0, TimeUnit.MILLISECONDS.toMinutes(
                    System.currentTimeMillis() - state.updatedAt
            ));
            if (minutes == 0) {
                updatedView.setText(R.string.updated_just_now);
            } else {
                updatedView.setText(itemView.getContext().getString(R.string.updated_ago, minutes));
            }
        }

        private void showQuickMenu(@NonNull View anchor,
                                   @NonNull Place place,
                                   @NonNull PlaceActionListener listener) {
            PopupMenu menu = new PopupMenu(anchor.getContext(), anchor);
            menu.getMenu().add(Menu.NONE, 1, Menu.NONE, R.string.set_primary);
            menu.getMenu().add(Menu.NONE, 2, Menu.NONE, R.string.delete);
            menu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) {
                    listener.onSetPrimary(place);
                    return true;
                } else if (item.getItemId() == 2) {
                    listener.onDelete(place);
                    return true;
                }
                return false;
            });
            menu.show();
        }

        private String formatCoords(@NonNull Place place) {
            return String.format(
                    Locale.getDefault(),
                    "%.4f, %.4f",
                    place.getLat(),
                    place.getLon()
            );
        }
    }
}
