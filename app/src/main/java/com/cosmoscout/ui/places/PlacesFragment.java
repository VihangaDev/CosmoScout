package com.cosmoscout.ui.places;

import android.Manifest;
import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.util.Log;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cosmoscout.R;
import com.cosmoscout.core.Perms;
import com.cosmoscout.core.Ui;
import com.cosmoscout.data.places.BortleEstimator;
import com.cosmoscout.data.places.Place;
import com.cosmoscout.data.places.PlacesRepository;
import com.cosmoscout.data.places.PlacesRepositoryImpl;
import com.cosmoscout.data.places.PlacesScoring;
import com.cosmoscout.ui.RefreshableFragment;
import com.cosmoscout.ui.places.PlacesController.Filter;
import com.cosmoscout.ui.places.PlacesController.HourSample;
import com.cosmoscout.ui.places.PlacesController.NightSettings;
import com.cosmoscout.ui.places.PlacesController.PlaceSkyState;
import com.cosmoscout.ui.places.PlacesController.Sort;
import com.cosmoscout.ui.places.PlacesController.UiPlace;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;

public class PlacesFragment extends RefreshableFragment implements PlacesAdapter.PlaceActionListener {

    private static final int REQUEST_COARSE_LOCATION = 4021;

    private PlacesRepository repository;
    private PlacesController controller;
    private PlacesAdapter adapter;

    private RecyclerView placesList;
    private View emptyState;
    private TextView emptySubtitle;
    private View emptyAction;
    private TextView errorView;
    private Chip chipAll;
    private Chip chipGood;
    private Chip chipOk;
    private Chip chipPoor;
    private ImageButton overflowButton;
    private View addButton;

    private final BortleEstimator bortleEstimator = new BortleEstimator();
    @Nullable
    private Call pendingBortleCall;
    private AddPlaceDialogController activeDialog;
    private boolean awaitingPermissionForLocation;
    private boolean lastLoadHadError;

    public PlacesFragment() {
        super(R.layout.fragment_places);
    }

    private boolean listErrorToastShown;

    private final PlacesController.Listener controllerListener = new PlacesController.Listener() {
        @Override
        public void onPlacesUpdated(@NonNull List<UiPlace> places) {
            lastLoadHadError = false;
            adapter.submitList(places);
            updateEmptyState(places.isEmpty());
            updateVisibleRange();
            listErrorToastShown = false;
        }

        @Override
        public void onLoadingStateChanged(boolean loading) {
            // RefreshableFragment handles indicator; update timestamp when loading stops.
            if (!loading && isAdded()) {
                updateStatusTimestamp();
            }
        }

        @Override
        public void onError(@NonNull Throwable throwable) {
            lastLoadHadError = true;
            Log.e("PlacesFragment", "Failed to load places", throwable);
            if (!isAdded()) {
                return;
            }
            boolean shouldToast = adapter.getItemCount() == 0 && !listErrorToastShown;
            if (shouldToast) {
                showToast(R.string.couldnt_fetch);
                listErrorToastShown = true;
            }
            updateEmptyState(adapter.getItemCount() == 0);
        }
    };

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = view.getContext();

        repository = new PlacesRepositoryImpl(context);
        controller = new PlacesController(context, repository, controllerListener);
        adapter = new PlacesAdapter(this);

        placesList = view.findViewById(R.id.placesList);
        emptyState = view.findViewById(R.id.emptyStateGroup);
        emptySubtitle = view.findViewById(R.id.emptySubtitle);
        emptyAction = view.findViewById(R.id.emptyCta);
        errorView = view.findViewById(R.id.errorState);
        chipAll = view.findViewById(R.id.filterAll);
        chipGood = view.findViewById(R.id.filterGood);
        chipOk = view.findViewById(R.id.filterOk);
        chipPoor = view.findViewById(R.id.filterPoor);
        overflowButton = view.findViewById(R.id.placesOverflow);
        addButton = view.findViewById(R.id.addPlaceBtn);

        placesList.setLayoutManager(new LinearLayoutManager(context));
        placesList.setAdapter(adapter);
        placesList.setItemAnimator(null);
        placesList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                updateVisibleRange();
            }
        });

        applyInsets();

        if (addButton != null) {
            addButton.setOnClickListener(v -> showAddPlaceDialog());
        }
        if (emptyAction != null) {
            emptyAction.setOnClickListener(v -> showAddPlaceDialog());
        }
        if (overflowButton != null) {
            overflowButton.setOnClickListener(this::showOverflowMenu);
        }

        setupFilterChips();

        controller.setDeviceLocation(getLastKnownCoarseLocation());
        controller.reload();
        updateVisibleRange();
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
        cancelPendingBortleCall();
        if (controller != null) {
            controller.destroy();
        }
        super.onDestroyView();
    }

    @Override
    protected void performRefresh(@NonNull Runnable onComplete) {
        controller.reload(() -> {
            controller.refreshVisible(true);
            onComplete.run();
        });
    }

    private void setupFilterChips() {
        Filter filter = controller.getFilter();
        updateChipState(chipAll, filter == Filter.ALL);
        updateChipState(chipGood, filter == Filter.GOOD);
        updateChipState(chipOk, filter == Filter.OK);
        updateChipState(chipPoor, filter == Filter.POOR);

        if (chipAll != null) chipAll.setOnClickListener(v -> controller.setFilter(Filter.ALL));
        if (chipGood != null) chipGood.setOnClickListener(v -> controller.setFilter(Filter.GOOD));
        if (chipOk != null) chipOk.setOnClickListener(v -> controller.setFilter(Filter.OK));
        if (chipPoor != null) chipPoor.setOnClickListener(v -> controller.setFilter(Filter.POOR));
    }

    private void updateChipState(@Nullable Chip chip, boolean checked) {
        if (chip == null) return;
        chip.setChecked(checked);
    }

    private void showOverflowMenu(@NonNull View anchor) {
        PopupMenu popup = new PopupMenu(anchor.getContext(), anchor);
        popup.getMenu().add(Menu.NONE, 1, Menu.NONE, R.string.tonight_settings);
        popup.getMenu().add(Menu.NONE, 2, Menu.NONE, R.string.sort_by);
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                showTonightSettingsDialog();
                return true;
            } else if (item.getItemId() == 2) {
                showSortDialog();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void showSortDialog() {
        Sort current = controller.getSort();
        String[] labels = new String[]{getString(R.string.sort_score), getString(R.string.sort_distance), getString(R.string.sort_name)};
        int checked = current == Sort.SCORE ? 0 : current == Sort.DISTANCE ? 1 : 2;
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.sort_by).setSingleChoiceItems(labels, checked, (dialog, which) -> {
            Sort target = which == 0 ? Sort.SCORE : which == 1 ? Sort.DISTANCE : Sort.NAME;
            controller.setSort(target);
            dialog.dismiss();
        }).setNegativeButton(R.string.cancel, null).show();
    }

    private void showTonightSettingsDialog() {
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_tonight_settings, null, false);
        TextInputEditText startField = content.findViewById(R.id.windowStartInput);
        TextInputEditText endField = content.findViewById(R.id.windowEndInput);
        TextInputEditText windField = content.findViewById(R.id.windCapInput);
        TextInputEditText cloudField = content.findViewById(R.id.weightCloudInput);
        TextInputEditText precipField = content.findViewById(R.id.weightPrecipInput);
        TextInputEditText windWeightField = content.findViewById(R.id.weightWindInput);
        TextInputEditText moonField = content.findViewById(R.id.weightMoonInput);

        NightSettings settings = controller.getNightSettings();
        startField.setText(formatMinutes(settings.windowStartMinutes));
        endField.setText(formatMinutes(settings.windowEndMinutes));
        windField.setText(String.format(Locale.getDefault(), "%.1f", settings.windCap));
        cloudField.setText(String.format(Locale.getDefault(), "%.2f", settings.weightCloud));
        precipField.setText(String.format(Locale.getDefault(), "%.2f", settings.weightPrecip));
        windWeightField.setText(String.format(Locale.getDefault(), "%.2f", settings.weightWind));
        moonField.setText(String.format(Locale.getDefault(), "%.2f", settings.weightMoon));

        View.OnClickListener timeListener = v -> {
            TextInputEditText editText = (TextInputEditText) v;
            int[] parts = parseMinutes(editText.getText() != null ? editText.getText().toString() : "00:00");
            TimePickerDialog dialog = new TimePickerDialog(requireContext(), (view, hourOfDay, minute) -> editText.setText(formatMinutes(hourOfDay * 60 + minute)), parts[0], parts[1], DateFormat.is24HourFormat(requireContext()));
            dialog.show();
        };
        startField.setOnClickListener(timeListener);
        endField.setOnClickListener(timeListener);

        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.tonight_settings).setView(content).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, (dialog, which) -> {
            Integer start = parseMinutesValue(startField.getText());
            Integer end = parseMinutesValue(endField.getText());
            Double windCap = parseDouble(windField.getText());
            Double weightCloud = parseDouble(cloudField.getText());
            Double weightPrecip = parseDouble(precipField.getText());
            Double weightWind = parseDouble(windWeightField.getText());
            Double weightMoon = parseDouble(moonField.getText());
            if (start == null || end == null || windCap == null || weightCloud == null || weightPrecip == null || weightWind == null || weightMoon == null) {
                showToast(R.string.invalid_coords);
                return;
            }
            controller.updateNightSettings(new NightSettings(start, end, windCap, weightCloud, weightPrecip, weightWind, weightMoon));
        }).show();
    }

    private String formatMinutes(int minutes) {
        int hour = (minutes / 60) % 24;
        int minute = minutes % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
    }

    private int[] parseMinutes(String value) {
        Integer minutes = parseMinutesValue(value);
        if (minutes == null) {
            return new int[]{19, 0};
        }
        return new int[]{minutes / 60, minutes % 60};
    }

    @Nullable
    private Integer parseMinutesValue(@Nullable CharSequence text) {
        if (text == null) return null;
        String value = text.toString();
        String[] parts = value.split(":");
        if (parts.length != 2) return null;
        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            return hour * 60 + minute;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    private Double parseDouble(@Nullable CharSequence text) {
        if (text == null) return null;
        String value = text.toString().trim();
        if (value.isEmpty()) return null;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void updateVisibleRange() {
        if (placesList == null) return;
        RecyclerView.LayoutManager lm = placesList.getLayoutManager();
        if (!(lm instanceof LinearLayoutManager)) {
            return;
        }
        LinearLayoutManager llm = (LinearLayoutManager) lm;
        int first = llm.findFirstVisibleItemPosition();
        int last = llm.findLastVisibleItemPosition();
        if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) {
            return;
        }
        controller.onVisibleRangeChanged(first, last);
    }

    private void updateEmptyState(boolean isEmpty) {
        if (emptyState != null) {
            emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
        if (addButton != null) {
            addButton.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
        if (errorView != null) {
            errorView.setVisibility(isEmpty && lastLoadHadError ? View.VISIBLE : View.GONE);
        }
        if (!isEmpty && errorView != null) {
            errorView.setVisibility(View.GONE);
        }
    }

    private void showDetailSheet(@NonNull UiPlace uiPlace) {
        if (getContext() == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.bottomsheet_place_detail, null, false);
        dialog.setContentView(content);

        TextView title = content.findViewById(R.id.detailTitle);
        TextView subtitle = content.findViewById(R.id.detailSubtitle);
        TextView notes = content.findViewById(R.id.detailNotes);
        TextView bestWindow = content.findViewById(R.id.detailBestWindow);
        TextView status = content.findViewById(R.id.detailStatus);
        TextView score = content.findViewById(R.id.detailScore);
        TextView cloudLine = content.findViewById(R.id.detailCloudLine);
        TextView windLine = content.findViewById(R.id.detailWindLine);
        TextView moonLine = content.findViewById(R.id.detailMoonLine);
        TextView updated = content.findViewById(R.id.detailUpdated);
        ViewGroup timeline = content.findViewById(R.id.detailTimeline);
        MaterialButton primaryButton = content.findViewById(R.id.detailPrimaryButton);
        MaterialButton mapButton = content.findViewById(R.id.detailMapButton);
        MaterialButton routeButton = content.findViewById(R.id.detailRouteButton);
        MaterialButton deleteButton = content.findViewById(R.id.detailDeleteButton);

        Place place = uiPlace.place;
        title.setText(place.getName());
        StringBuilder subtitleText = new StringBuilder();
        subtitleText.append(String.format(Locale.getDefault(), "%.4f, %.4f", place.getLat(), place.getLon()));
        if (place.getBortle() != null) {
            subtitleText.append(" • ").append(getString(R.string.bortle)).append(" ").append(place.getBortle());
        }
        if (uiPlace.distanceKm != null) {
            subtitleText.append(" • ").append(getString(R.string.distance_away, String.format(Locale.getDefault(), "%.1f", uiPlace.distanceKm)));
        }
        subtitle.setText(subtitleText.toString());

        if (place.hasNotes()) {
            notes.setVisibility(View.VISIBLE);
            notes.setText(place.getNotes());
        } else {
            notes.setVisibility(View.GONE);
        }

        if (uiPlace.sky != null) {
            PlaceSkyState state = uiPlace.sky;
            java.text.DateFormat df = DateFormat.getTimeFormat(requireContext());
            df.setTimeZone(controller.getTimezone(place.getId()));
            bestWindow.setText(getString(R.string.best_window_format, df.format(new Date(state.windowStart)), df.format(new Date(state.windowEnd))));
            status.setText(state.status == PlacesScoring.SkyStatus.GOOD ? R.string.sky_good : state.status == PlacesScoring.SkyStatus.OK ? R.string.sky_ok : R.string.sky_poor);
            score.setText(String.valueOf(state.score));
            cloudLine.setText(getString(R.string.clear_pct, state.clearPct));
            windLine.setText(getString(R.string.detail_wind, state.avgWind));
            moonLine.setText(getString(R.string.moon_pct, state.moonPct));
            long minutes = Math.max(0, TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - state.updatedAt));
            updated.setText(minutes == 0 ? getString(R.string.updated_just_now) : getString(R.string.updated_ago, minutes));
            populateTimeline(timeline, controller.getHourSamples(place.getId()), controller.getTimezone(place.getId()));
        } else {
            bestWindow.setText(R.string.best_window_format);
            status.setText(R.string.details);
            score.setText("-");
            updated.setText(R.string.list_empty);
            timeline.removeAllViews();
        }

        primaryButton.setText(uiPlace.isPrimary ? R.string.primary : R.string.set_primary);
        primaryButton.setEnabled(!uiPlace.isPrimary);
        primaryButton.setOnClickListener(v -> {
            controller.setPrimaryPlace(place.getId());
            dialog.dismiss();
        });
        mapButton.setOnClickListener(v -> {
            openMap(place);
            dialog.dismiss();
        });
        routeButton.setOnClickListener(v -> {
            openRoute(place);
            dialog.dismiss();
        });
        deleteButton.setOnClickListener(v -> {
            dialog.dismiss();
            onDelete(place);
        });

        dialog.show();
    }

    private void populateTimeline(@NonNull ViewGroup container, @NonNull List<HourSample> samples, @NonNull TimeZone timezone) {
        container.removeAllViews();
        for (HourSample sample : samples) {
            Chip chip = new Chip(container.getContext());
            chip.setCheckable(false);
            chip.setClickable(false);
            chip.setEnsureMinTouchTargetSize(false);
            chip.setChipStrokeWidth(0f);
            chip.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            java.text.DateFormat df = DateFormat.getTimeFormat(container.getContext());
            df.setTimeZone(timezone);
            chip.setText(String.format(Locale.getDefault(), "%s\n%d%%", df.format(new Date(sample.timeMillis)), sample.cloudPct));
            chip.setChipBackgroundColorResource(R.color.colorSurface);
            chip.setTextColor(ContextCompat.getColor(container.getContext(), R.color.colorOnSurface));
            if (sample.precipitation > 0d) {
                chip.setChipIconResource(R.drawable.ic_rain_24);
                chip.setChipIconTint(ColorStateList.valueOf(ContextCompat.getColor(container.getContext(), R.color.colorPrimary)));
            }
            // Add margins to each chip for spacing
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(4, 0, 4, 0); // 4dp left and right margin
            chip.setLayoutParams(params);
            container.addView(chip);
        }
    }

    @Override
    public void onShowDetails(@NonNull UiPlace uiPlace) {
        showDetailSheet(uiPlace);
    }

    @Override
    public void onMap(@NonNull Place place) {
        openMap(place);
    }

    @Override
    public void onRoute(@NonNull Place place) {
        openRoute(place);
    }

    @Override
    public void onDelete(@NonNull Place place) {
        new MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.remove_place_confirm).setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss()).setPositiveButton(R.string.remove, (dialog, which) -> executeRemove(place)).show();
    }

    @Override
    public void onSetPrimary(@NonNull Place place) {
        controller.setPrimaryPlace(place.getId());
    }

    private void openMap(@NonNull Place place) {
        if (!isValidCoordinate(place.getLat(), place.getLon())) {
            showToast(R.string.invalid_location);
            return;
        }
        String uri = String.format(Locale.US, "geo:%f,%f?q=%f,%f(%s)", place.getLat(), place.getLon(), place.getLat(), place.getLon(), Uri.encode(place.getName()));
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        if (intent.resolveActivity(requireActivity().getPackageManager()) != null) {
            startActivity(intent);
        } else {
            // Fall back to opening in browser
            String browserUri = String.format(Locale.US, "https://maps.google.com/?q=%f,%f", place.getLat(), place.getLon());
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(browserUri));
            if (browserIntent.resolveActivity(requireActivity().getPackageManager()) != null) {
                startActivity(browserIntent);
            } else {
                showToast(R.string.no_browser_app);
            }
        }
    }

    private void openRoute(@NonNull Place place) {
        if (!isValidCoordinate(place.getLat(), place.getLon())) {
            showToast(R.string.invalid_location);
            return;
        }
        String uri = String.format(Locale.US, "google.navigation:q=%f,%f", place.getLat(), place.getLon());
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        if (intent.resolveActivity(requireActivity().getPackageManager()) != null) {
            startActivity(intent);
        } else {
            // Fall back to opening Google Maps in browser
            String browserUri = String.format(Locale.US, "https://maps.google.com/?q=%f,%f", place.getLat(), place.getLon());
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(browserUri));
            if (browserIntent.resolveActivity(requireActivity().getPackageManager()) != null) {
                startActivity(browserIntent);
            } else {
                showToast(R.string.no_browser_app);
            }
        }
    }

    private boolean isValidCoordinate(double lat, double lon) {
        return !Double.isNaN(lat) && !Double.isNaN(lon) && !Double.isInfinite(lat) && !Double.isInfinite(lon)
                && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
    }

    private void applyInsets() {
        if (placesList != null) {
            final int baseBottom = placesList.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(placesList, (v, insets) -> {
                Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), baseBottom + sys.bottom);
                return insets;
            });
        }
        offsetAddButtonForBottomBar();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_COARSE_LOCATION) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            boolean shouldHandle = awaitingPermissionForLocation;
            awaitingPermissionForLocation = false;
            if (granted) {
                controller.setDeviceLocation(getLastKnownCoarseLocation());
            }
            if (granted && shouldHandle && activeDialog != null) {
                Location location = getLastKnownCoarseLocation();
                if (location != null) {
                    activeDialog.fillCoordinates(location.getLatitude(), location.getLongitude());
                } else if (isAdded()) {
                    showToast(R.string.location_unavailable);
                }
            } else if (!granted && shouldHandle && isAdded()) {
                showToast(R.string.location_unavailable);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (addButton != null) addButton.requestApplyInsets();
        if (placesList != null) placesList.requestApplyInsets();
    }

    private void executeRemove(@NonNull Place place) {
        repository.remove(place.getId(), err -> {
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            if (err == null) {
                Ui.toast(activity, getString(R.string.place_removed));
                controller.onPlaceRemoved(place.getId());
                controller.reload();
            } else {
                Ui.toast(activity, err.getMessage() != null ? err.getMessage() : getString(R.string.list_error));
            }
        });
    }

    // --- Existing add place implementation ---

    private void showAddPlaceDialog() {
        if (!isAdded()) {
            return;
        }
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.bottomsheet_add_place, null, false);
        AddPlaceDialogController dialogController = new AddPlaceDialogController(content);

        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        dialog.setContentView(content);
        dialogController.setDialog(dialog);
        dialog.setOnDismissListener(d -> {
            if (activeDialog == dialogController) {
                activeDialog = null;
            }
        });
        dialogController.saveButton.setOnClickListener(v -> handleSavePlace(dialogController));
        dialogController.cancelButton.setOnClickListener(v -> dialog.dismiss());
        dialogController.useLocationButton.setOnClickListener(v -> handleUseCurrentLocation(dialogController));
        dialogController.pickOnMapButton.setOnClickListener(v -> showLocationPicker(dialogController));
        dialogController.estimateBortleButton.setOnClickListener(v -> requestBortleEstimate(dialogController, false));
        dialog.show();
        activeDialog = dialogController;
    }

    private void handleSavePlace(@NonNull AddPlaceDialogController dialogController) {
        dialogController.clearErrors();
        String name = safeText(dialogController.nameField);
        String latRaw = safeText(dialogController.latField);
        String lonRaw = safeText(dialogController.lonField);

        if (name.isEmpty()) {
            dialogController.nameLayout.setError(getString(R.string.name_required));
            return;
        }

        double lat;
        double lon;
        try {
            lat = Double.parseDouble(latRaw);
            lon = Double.parseDouble(lonRaw);
        } catch (NumberFormatException e) {
            Ui.toast(requireActivity(), getString(R.string.invalid_coords));
            dialogController.latLayout.setError(getString(R.string.invalid_coords));
            dialogController.lonLayout.setError(getString(R.string.invalid_coords));
            return;
        }
        if (!isValidLat(lat) || !isValidLon(lon)) {
            Ui.toast(requireActivity(), getString(R.string.invalid_coords));
            dialogController.latLayout.setError(getString(R.string.invalid_coords));
            dialogController.lonLayout.setError(getString(R.string.invalid_coords));
            return;
        }

        Integer bortle = parseBortleValue(safeText(dialogController.bortleField));
        if (bortle != null && (bortle < 1 || bortle > 9)) {
            dialogController.bortleLayout.setError(getString(R.string.bortle));
            return;
        }

        String notes = safeOptional(dialogController.notesField);
        dialogController.setSaving(true);
        repository.add(name, lat, lon, bortle, notes, err -> {
            dialogController.setSaving(false);
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            if (err == null) {
                dialogController.dismiss();
                Ui.toast(activity, getString(R.string.place_saved));
                controller.reload();
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
            controller.setBortleHelper(null);
        } else {
            Ui.toast(requireActivity(), getString(R.string.location_unavailable));
        }
    }

    private void requestBortleEstimate(@NonNull AddPlaceDialogController controller, boolean autoTriggered) {
        String latRaw = safeText(controller.latField);
        String lonRaw = safeText(controller.lonField);
        if (latRaw.isEmpty() || lonRaw.isEmpty()) {
            if (!autoTriggered) {
                Ui.toast(requireActivity(), getString(R.string.invalid_coords));
            }
            return;
        }
        double lat;
        double lon;
        try {
            lat = Double.parseDouble(latRaw);
            lon = Double.parseDouble(lonRaw);
        } catch (NumberFormatException e) {
            if (!autoTriggered) {
                Ui.toast(requireActivity(), getString(R.string.invalid_coords));
            }
            return;
        }
        if (!isValidLat(lat) || !isValidLon(lon)) {
            if (!autoTriggered) {
                Ui.toast(requireActivity(), getString(R.string.invalid_coords));
            }
            return;
        }
        startBortleEstimate(controller, lat, lon, autoTriggered);
    }

    private void startBortleEstimate(@NonNull AddPlaceDialogController controller, double lat, double lon, boolean autoTriggered) {
        cancelPendingBortleCall();
        controller.setEstimatingBortle(true);
        controller.setBortleHelper(getString(R.string.estimating_bortle));
        final Call[] holder = new Call[1];
        Call call = bortleEstimator.estimate(lat, lon, (value, error) -> Ui.runOnUi(getActivity(), () -> {
            if (pendingBortleCall == holder[0]) {
                pendingBortleCall = null;
            }
            if (controller != activeDialog) {
                return;
            }
            controller.setEstimatingBortle(false);
            if (value != null) {
                controller.setBortleValue(String.valueOf(value));
                controller.setBortleHelper(getString(R.string.bortle_estimate_applied, value));
            } else {
                controller.setBortleHelper(getString(R.string.bortle_estimate_failed));
                if (!autoTriggered) {
                    Ui.toast(getActivity(), getString(R.string.bortle_estimate_failed));
                }
            }
        }));
        holder[0] = call;
        pendingBortleCall = call;
    }

    private void cancelPendingBortleCall() {
        if (pendingBortleCall != null) {
            pendingBortleCall.cancel();
            pendingBortleCall = null;
        }
    }

    private void showLocationPicker(@NonNull AddPlaceDialogController controller) {
        if (!isAdded()) {
            return;
        }
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_location_picker, null, false);
        TextView coordLabel = content.findViewById(R.id.selectedCoords);
        ProgressBar progress = content.findViewById(R.id.mapProgress);
        WebView webView = content.findViewById(R.id.locationWebView);

        double lat = parseDoubleOr(controller.latField, Double.NaN);
        double lon = parseDoubleOr(controller.lonField, Double.NaN);
        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            Location fallback = getLastKnownCoarseLocation();
            if (fallback != null) {
                lat = fallback.getLatitude();
                lon = fallback.getLongitude();
            }
        }
        boolean hasStart = !Double.isNaN(lat) && !Double.isNaN(lon);
        int zoom = hasStart ? 14 : 2;
        StringBuilder urlBuilder = new StringBuilder("file:///android_asset/location_picker.html?zoom=").append(zoom);
        if (hasStart) {
            urlBuilder.append("&lat=").append(lat).append("&lon=").append(lon);
        }
        String url = urlBuilder.toString();
        final double[] selected = new double[]{Double.NaN, Double.NaN};

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.pick_on_map).setView(content).setNegativeButton(R.string.cancel, (d, which) -> d.dismiss()).setPositiveButton(R.string.confirm_location, null).create();
        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            positive.setEnabled(false);
            positive.setOnClickListener(v -> {
                if (Double.isNaN(selected[0]) || Double.isNaN(selected[1])) {
                    return;
                }
                controller.setCoordinates(selected[0], selected[1]);
                dialog.dismiss();
                requestBortleEstimate(controller, true);
            });
        });
        dialog.setOnDismissListener(d -> webView.destroy());

        initPickerWebView(webView, progress, coordLabel, selected, dialog);
        webView.loadUrl(url);
        dialog.show();
    }

    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private void initPickerWebView(@NonNull WebView webView, @NonNull ProgressBar progress, @NonNull TextView coordLabel, @NonNull double[] selected, @NonNull AlertDialog dialog) {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
            }
        });
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void onLocationChanged(double lat, double lon) {
                selected[0] = lat;
                selected[1] = lon;
                Ui.runOnUi(getActivity(), () -> {
                    coordLabel.setText(String.format(Locale.getDefault(), "%.5f, %.5f", lat, lon));
                    Button positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                    if (positive != null) {
                        positive.setEnabled(true);
                    }
                });
            }
        }, "AndroidBridge");
    }

    private double parseDoubleOr(@Nullable TextInputEditText field, double fallback) {
        String value = safeText(field);
        if (value.isEmpty()) {
            return fallback;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Nullable
    private Location getLastKnownCoarseLocation() {
        Context context = getContext();
        if (context == null) {
            return null;
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
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

    @NonNull
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

    private void offsetAddButtonForBottomBar() {
        if (addButton == null || !isAdded()) {
            return;
        }
        final View bottomNav = requireActivity().findViewById(R.id.bottomNavigation);
        final int extraPadding = (int) (16 * getResources().getDisplayMetrics().density);

        ViewCompat.setOnApplyWindowInsetsListener(addButton, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int navHeight = bottomNav != null ? bottomNav.getHeight() : 0;
            updateBottomMargin(v, sys.bottom + navHeight + extraPadding);
            return insets;
        });

        if (bottomNav != null) {
            bottomNav.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> addButton.requestApplyInsets());
        } else {
            addButton.requestApplyInsets();
        }
    }

    private void updateBottomMargin(@NonNull View target, int bottomMargin) {
        ViewGroup.LayoutParams params = target.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) params;
        mlp.bottomMargin = bottomMargin;
        target.setLayoutParams(mlp);
    }

    private void showToast(@StringRes int resId) {
        Activity activity = getActivity();
        if (activity != null) {
            Ui.toast(activity, getString(resId));
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
        final MaterialButton pickOnMapButton;
        final MaterialButton estimateBortleButton;
        final MaterialButton saveButton;
        final MaterialButton cancelButton;
        private final String estimateLabel;
        private final String estimatingLabel;
        private BottomSheetDialog dialog;

        AddPlaceDialogController(@NonNull View root) {
            Context ctx = root.getContext();
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
            pickOnMapButton = root.findViewById(R.id.pickOnMapBtn);
            estimateBortleButton = root.findViewById(R.id.estimateBortleBtn);
            saveButton = root.findViewById(R.id.saveAddPlaceBtn);
            cancelButton = root.findViewById(R.id.cancelAddPlaceBtn);
            estimateLabel = ctx.getString(R.string.estimate_bortle);
            estimatingLabel = ctx.getString(R.string.estimating_bortle);

            String[] levels = new String[9];
            for (int i = 0; i < levels.length; i++) {
                levels[i] = String.valueOf(i + 1);
            }
            bortleField.setSimpleItems(levels);
        }

        void setDialog(@NonNull BottomSheetDialog dialog) {
            this.dialog = dialog;
        }

        void setSaving(boolean saving) {
            if (saveButton != null) {
                saveButton.setEnabled(!saving);
            }
            if (useLocationButton != null) {
                useLocationButton.setEnabled(!saving);
            }
            if (pickOnMapButton != null) {
                pickOnMapButton.setEnabled(!saving);
            }
            if (estimateBortleButton != null) {
                estimateBortleButton.setEnabled(!saving);
            }
        }

        void clearErrors() {
            if (nameLayout != null) nameLayout.setError(null);
            if (latLayout != null) latLayout.setError(null);
            if (lonLayout != null) lonLayout.setError(null);
            if (bortleLayout != null) bortleLayout.setError(null);
            setBortleHelper(null);
        }

        void fillCoordinates(double lat, double lon) {
            if (latField != null) {
                latField.setText(String.format(Locale.getDefault(), "%.5f", lat));
            }
            if (lonField != null) {
                lonField.setText(String.format(Locale.getDefault(), "%.5f", lon));
            }
        }

        void setCoordinates(double lat, double lon) {
            fillCoordinates(lat, lon);
        }

        void setBortleValue(@Nullable String value) {
            if (bortleField != null) {
                bortleField.setText(value);
            }
        }

        void setEstimatingBortle(boolean estimating) {
            if (estimateBortleButton != null) {
                estimateBortleButton.setEnabled(!estimating);
                estimateBortleButton.setText(estimating ? estimatingLabel : estimateLabel);
            }
        }

        void setBortleHelper(@Nullable CharSequence text) {
            if (bortleLayout != null) {
                bortleLayout.setHelperText(text);
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
