package com.cosmoscout.places;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cosmoscout.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PlacesAdapter extends RecyclerView.Adapter<PlacesAdapter.PlaceViewHolder> {

    public interface PlaceActionListener {
        void onRemoveRequested(@NonNull Place place);
    }

    @NonNull private final List<Place> items = new ArrayList<>();
    @NonNull private final PlaceActionListener actionListener;

    public PlacesAdapter(@NonNull PlaceActionListener actionListener) {
        this.actionListener = actionListener;
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public PlaceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_place, parent, false);
        return new PlaceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaceViewHolder holder, int position) {
        Place place = items.get(position);
        holder.bind(place, actionListener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).getStableId();
    }

    public void submit(@NonNull List<Place> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    static final class PlaceViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleView;
        private final TextView detailView;
        private final TextView notesView;
        private final ImageButton deleteButton;

        PlaceViewHolder(@NonNull View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.placeTitle);
            detailView = itemView.findViewById(R.id.placeDetails);
            notesView = itemView.findViewById(R.id.placeNotes);
            deleteButton = itemView.findViewById(R.id.removePlaceBtn);
        }

        void bind(@NonNull Place place, @NonNull PlaceActionListener listener) {
            titleView.setText(place.getName());
            detailView.setText(buildDetails(place));

            if (place.hasNotes()) {
                notesView.setText(place.getNotes());
                notesView.setVisibility(View.VISIBLE);
            } else {
                notesView.setVisibility(View.GONE);
            }

            deleteButton.setOnClickListener(v -> listener.onRemoveRequested(place));
        }

        private String buildDetails(@NonNull Place place) {
            String coords = String.format(
                    Locale.getDefault(),
                    "%.4f, %.4f",
                    place.getLat(),
                    place.getLon()
            );
            Integer bortle = place.getBortle();
            if (bortle != null) {
                return coords + " \u2022 " + itemView.getContext().getString(R.string.bortle) + " " + bortle;
            }
            return coords;
        }
    }
}
