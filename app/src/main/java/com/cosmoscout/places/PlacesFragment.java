package com.cosmoscout.places;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.cosmoscout.R;
import com.cosmoscout.core.Perms;
import com.cosmoscout.core.Ui;
import com.cosmoscout.ui.RefreshableFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PlacesFragment extends RefreshableFragment implements PlacesAdapter.PlaceActionListener {

    private static final int REQUEST_COARSE_LOCATION = 4021;

    private PlacesRepository repository;
    private PlacesAdapter adapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView placesList;
    private TextView emptyView;
    private TextView errorView;
    private View addButton;
    private AddPlaceDialogController activeDialog;
    private boolean awaitingPermissionForLocation;

    public PlacesFragment() {
        super(R.layout.fragment_places);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = view.getContext();

        repository = new PlacesRepositoryImpl(context);
        adapter = new PlacesAdapter(this);

        swipeRefreshLayout = view.findViewById(R.id.swipeRefresh);
        placesList = view.findViewById(R.id.placesList);
        emptyView = view.findViewById(R.id.emptyState);
        errorView = view.findViewById(R.id.errorState);
        addButton = view.findViewById(R.id.addPlaceBtn);

        placesList.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
        placesList.setAdapter(adapter);
        placesList.setItemAnimator(null);

        applyInsets();

        if (addButton != null) {
            addButton.setOnClickListener(v -> showAddPlaceDialog());
        }

        fetchPlaces(false, null);
    }

    @Override
    protected void performRefresh(@NonNull Runnable onComplete) {
        fetchPlaces(true, onComplete);
    }

    private void fetchPlaces(boolean fromSwipe, @Nullable Runnable onComplete) {
        if (!fromSwipe) {
            setRefreshing(true);
        }
        repository.list((items, err) -> {
            if (!isAdded()) {
                if (onComplete != null) {
                    onComplete.run();
                }
                return;
            }
            if (err == null) {
                adapter.submit(items);
                if (items.isEmpty()) {
                    showEmpty();
                } else {
                    showContent();
                }
            } else {
                showError();
                Activity activity = getActivity();
                if (activity != null) {
                    Ui.toast(activity, getString(R.string.couldnt_fetch));
                }
            }
            if (!fromSwipe) {
                setRefreshing(false);
            }
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    private void showContent() {
        if (placesList != null) {
            placesList.setVisibility(View.VISIBLE);
        }
        if (emptyView != null) {
            emptyView.setVisibility(View.GONE);
        }
        if (errorView != null) {
            errorView.setVisibility(View.GONE);
        }
    }

    private void showEmpty() {
        if (placesList != null) {
            placesList.setVisibility(View.GONE);
        }
        if (errorView != null) {
            errorView.setVisibility(View.GONE);
        }
        if (emptyView != null) {
            emptyView.setVisibility(View.VISIBLE);
        }
    }

    private void showError() {
        if (placesList != null) {
            placesList.setVisibility(View.GONE);
        }
        if (emptyView != null) {
            emptyView.setVisibility(View.GONE);
        }
        if (errorView != null) {
            errorView.setVisibility(View.VISIBLE);
        }
    }

    private void setRefreshing(boolean refreshing) {
        if (swipeRefreshLayout == null) {
            return;
        }
        swipeRefreshLayout.post(() -> swipeRefreshLayout.setRefreshing(refreshing));
    }

    private void showAddPlaceDialog() {
        if (!isAdded()) {
            return;
        }
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_place, null, false);
        AddPlaceDialogController controller = new AddPlaceDialogController(content);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.add_place)
                .setView(content)
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.save, null);

        AlertDialog dialog = builder.create();
        controller.setDialog(dialog);
        dialog.setOnShowListener(dlg -> {
            Button positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            positive.setOnClickListener(v -> handleSavePlace(controller));
        });
        dialog.setOnDismissListener(d -> {
            if (activeDialog == controller) {
                activeDialog = null;
            }
        });
        controller.useLocationButton.setOnClickListener(v -> handleUseCurrentLocation(controller));
        dialog.show();
        activeDialog = controller;
    }

    private void handleSavePlace(@NonNull AddPlaceDialogController controller) {
        controller.clearErrors();
        String name = safeText(controller.nameField);
        String latRaw = safeText(controller.latField);
        String lonRaw = safeText(controller.lonField);

        if (name.isEmpty()) {
            controller.nameLayout.setError(getString(R.string.name_required));
            return;
        }

        double lat;
        double lon;
        try {
            lat = Double.parseDouble(latRaw);
            lon = Double.parseDouble(lonRaw);
        } catch (NumberFormatException e) {
            Ui.toast(requireActivity(), getString(R.string.invalid_coords));
            controller.latLayout.setError(getString(R.string.invalid_coords));
            controller.lonLayout.setError(getString(R.string.invalid_coords));
            return;
        }

        if (!isValidLat(lat) || !isValidLon(lon)) {
            Ui.toast(requireActivity(), getString(R.string.invalid_coords));
            controller.latLayout.setError(getString(R.string.invalid_coords));
            controller.lonLayout.setError(getString(R.string.invalid_coords));
            return;
        }

        Integer bortle = parseBortleValue(safeText(controller.bortleField));
        if (bortle != null && (bortle < 1 || bortle > 9)) {
            controller.bortleLayout.setError(getString(R.string.bortle) + " 1-9");
            return;
        }

        String notes = safeOptional(controller.notesField);

        controller.setSaving(true);
        repository.add(name, lat, lon, bortle, notes, err -> {
            controller.setSaving(false);
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            if (err == null) {
                controller.dismiss();
                Ui.toast(activity, getString(R.string.place_saved));
                fetchPlaces(false, null);
            } else {
                Ui.toast(activity, err.getMessage() != null ? err.getMessage() : getString(R.string.list_error));
            }
        });
    }

    @Nullable
    private Integer parseBortleValue(@NonNull String value) {
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void handleUseCurrentLocation(@NonNull AddPlaceDialogController controller) {
        if (!isAdded()) {
            return;
        }
        if (!Perms.hasCoarseLocation(requireContext())) {
            awaitingPermissionForLocation = true;
            Perms.requestCoarseLocation(requireActivity(), REQUEST_COARSE_LOCATION);
            return;
        }
        Location location = getLastKnownCoarseLocation();
        if (location != null) {
            controller.fillCoordinates(location.getLatitude(), location.getLongitude());
        } else {
            Ui.toast(requireActivity(), getString(R.string.location_unavailable));
        }
    }

    @Nullable
    private Location getLastKnownCoarseLocation() {
        Context context = getContext();
        if (context == null) {
            return null;
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) {
            return null;
        }
        Location best = null;
        for (String provider : getCoarseProviders(manager)) {
            try {
                Location loc = manager.getLastKnownLocation(provider);
                if (loc != null && (best == null || loc.getTime() > best.getTime())) {
                    best = loc;
                }
            } catch (SecurityException ignored) {
            }
        }
        return best;
    }

    private List<String> getCoarseProviders(@NonNull LocationManager manager) {
        List<String> providers = new ArrayList<>();
        if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            providers.add(LocationManager.NETWORK_PROVIDER);
        }
        if (manager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
            providers.add(LocationManager.PASSIVE_PROVIDER);
        }
        if (providers.isEmpty()) {
            providers.add(LocationManager.NETWORK_PROVIDER);
            providers.add(LocationManager.PASSIVE_PROVIDER);
        }
        return providers;
    }

    @Override
    public void onRemoveRequested(@NonNull Place place) {
        if (!isAdded()) {
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.remove_place_confirm)
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.remove, (dialog, which) -> executeRemove(place))
                .show();
    }

    private void executeRemove(@NonNull Place place) {
        repository.remove(place.getId(), err -> {
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            if (err == null) {
                Ui.toast(activity, getString(R.string.place_removed));
                fetchPlaces(false, null);
            } else {
                Ui.toast(activity, err.getMessage() != null ? err.getMessage() : getString(R.string.list_error));
            }
        });
    }

    private void applyInsets() {
        if (placesList != null) {
            final int baseBottom = placesList.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(placesList, (v, insets) -> {
                Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(
                        v.getPaddingLeft(),
                        v.getPaddingTop(),
                        v.getPaddingRight(),
                        baseBottom + sys.bottom
                );
                return insets;
            });
        }
        if (addButton != null) {
            final ViewGroupMarginUpdater updater = new ViewGroupMarginUpdater(addButton);
            ViewCompat.setOnApplyWindowInsetsListener(addButton, (v, insets) -> {
                Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                updater.apply(sys.bottom);
                return insets;
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_COARSE_LOCATION) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            boolean shouldHandle = awaitingPermissionForLocation;
            awaitingPermissionForLocation = false;
            if (granted && shouldHandle && activeDialog != null) {
                Location location = getLastKnownCoarseLocation();
                if (location != null) {
                    activeDialog.fillCoordinates(location.getLatitude(), location.getLongitude());
                } else {
                    Activity activity = getActivity();
                    if (activity != null) {
                        Ui.toast(activity, getString(R.string.location_unavailable));
                    }
                }
            } else if (!granted && shouldHandle) {
                Activity activity = getActivity();
                if (activity != null) {
                    Ui.toast(activity, getString(R.string.location_unavailable));
                }
            }
        }
    }

    @Override
    public void onDestroyView() {
        if (placesList != null) {
            placesList.setAdapter(null);
        }
        if (activeDialog != null) {
            activeDialog.dismiss();
            activeDialog = null;
        }
        super.onDestroyView();
    }

    private boolean isValidLat(double lat) {
        return lat >= -90d && lat <= 90d;
    }

    private boolean isValidLon(double lon) {
        return lon >= -180d && lon <= 180d;
    }

    @NonNull
    private String safeText(@Nullable TextInputEditText editText) {
        if (editText == null) {
            return "";
        }
        CharSequence text = editText.getText();
        return text == null ? "" : text.toString().trim();
    }

    @NonNull
    private String safeText(@Nullable MaterialAutoCompleteTextView editText) {
        if (editText == null) {
            return "";
        }
        CharSequence text = editText.getText();
        return text == null ? "" : text.toString().trim();
    }

    @Nullable
    private String safeOptional(@Nullable TextInputEditText editText) {
        String value = safeText(editText);
        return TextUtils.isEmpty(value) ? null : value;
    }

    @Override
    public void onResume() {
        super.onResume();
        // ensure the bottom padding accounts for current insets when returning to fragment
        if (addButton != null) {
            addButton.requestApplyInsets();
        }
        if (placesList != null) {
            placesList.requestApplyInsets();
        }
    }

    private static final class AddPlaceDialogController {
        final TextInputLayout nameLayout;
        final TextInputLayout latLayout;
        final TextInputLayout lonLayout;
        final TextInputLayout bortleLayout;
        final TextInputEditText nameField;
        final TextInputEditText latField;
        final TextInputEditText lonField;
        final MaterialAutoCompleteTextView bortleField;
        final TextInputEditText notesField;
        final MaterialButton useLocationButton;
        private AlertDialog dialog;

        AddPlaceDialogController(@NonNull View root) {
            nameLayout = root.findViewById(R.id.nameInputLayout);
            latLayout = root.findViewById(R.id.latInputLayout);
            lonLayout = root.findViewById(R.id.lonInputLayout);
            bortleLayout = root.findViewById(R.id.bortleInputLayout);
            nameField = root.findViewById(R.id.nameInput);
            latField = root.findViewById(R.id.latInput);
            lonField = root.findViewById(R.id.lonInput);
            bortleField = root.findViewById(R.id.bortleInput);
            notesField = root.findViewById(R.id.notesInput);
            useLocationButton = root.findViewById(R.id.useCurrentLocationBtn);

            String[] levels = new String[9];
            for (int i = 0; i < levels.length; i++) {
                levels[i] = String.valueOf(i + 1);
            }
            bortleField.setSimpleItems(levels);
        }

        void setDialog(@NonNull AlertDialog dialog) {
            this.dialog = dialog;
        }

        void setSaving(boolean saving) {
            if (dialog != null) {
                Button positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (positive != null) {
                    positive.setEnabled(!saving);
                }
            }
            if (useLocationButton != null) {
                useLocationButton.setEnabled(!saving);
            }
        }

        void clearErrors() {
            if (nameLayout != null) nameLayout.setError(null);
            if (latLayout != null) latLayout.setError(null);
            if (lonLayout != null) lonLayout.setError(null);
            if (bortleLayout != null) bortleLayout.setError(null);
        }

        void fillCoordinates(double lat, double lon) {
            if (latField != null) {
                latField.setText(String.format(Locale.getDefault(), "%.5f", lat));
            }
            if (lonField != null) {
                lonField.setText(String.format(Locale.getDefault(), "%.5f", lon));
            }
        }

        void dismiss() {
            if (dialog != null) {
                dialog.dismiss();
            }
        }
    }

    private static final class ViewGroupMarginUpdater {
        private final View target;
        private final int startBottom;

        ViewGroupMarginUpdater(@NonNull View target) {
            this.target = target;
            ViewGroup.LayoutParams params = target.getLayoutParams();
            if (params instanceof ViewGroup.MarginLayoutParams) {
                this.startBottom = ((ViewGroup.MarginLayoutParams) params).bottomMargin;
            } else {
                this.startBottom = 0;
            }
        }

        void apply(int bottomInset) {
            ViewGroup.LayoutParams params = target.getLayoutParams();
            if (!(params instanceof ViewGroup.MarginLayoutParams)) {
                return;
            }
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) params;
            mlp.bottomMargin = startBottom + bottomInset;
            target.setLayoutParams(mlp);
        }
    }
}
