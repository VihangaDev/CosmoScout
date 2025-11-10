package com.cosmoscout.ui;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.cosmoscout.R;

import java.util.Date;

/**
 * Small helper fragment that wires up a polished SwipeRefreshLayout experience and exposes hooks
 * for subclasses to react to refresh events without re-implementing boilerplate.
 */
public abstract class RefreshableFragment extends Fragment {

    private static final long DEFAULT_REFRESH_DURATION_MS = 1200L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView statusView;

    protected RefreshableFragment(@LayoutRes int layoutId) {
        super(layoutId);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefresh);
        statusView = view.findViewById(R.id.refreshStatus);

        if (swipeRefreshLayout == null) {
            return;
        }

        Context context = view.getContext();
        swipeRefreshLayout.setProgressViewOffset(true, dp(context, 24), dp(context, 112));
        swipeRefreshLayout.setSlingshotDistance(dp(context, 140));
        swipeRefreshLayout.setDistanceToTriggerSync(dp(context, 96));
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(
                ContextCompat.getColor(context, R.color.refresh_indicator_bg));
        swipeRefreshLayout.setColorSchemeColors(
                ContextCompat.getColor(context, R.color.refresh_indicator_accent));
        swipeRefreshLayout.setOnRefreshListener(this::handleRefreshRequest);

        if (statusView != null) {
            statusView.setText(R.string.refresh_status_hint);
            statusView.setAlpha(1f);
        }
    }

    private void handleRefreshRequest() {
        onRefreshStarted();
        performRefresh(this::completeRefreshIfActive);
    }

    private void completeRefreshIfActive() {
        if (!isAdded()) {
            return;
        }
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.post(() -> {
                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            });
        }
        updateStatusTimestamp();
        onRefreshFinished();
    }

    /**
     * Subclasses can override to provide their own async work. Call onComplete when finished.
     */
    protected void performRefresh(@NonNull Runnable onComplete) {
        handler.postDelayed(onComplete, getRefreshDurationMillis());
    }

    protected long getRefreshDurationMillis() {
        return DEFAULT_REFRESH_DURATION_MS;
    }

    protected void onRefreshStarted() {
        // Optional for subclasses.
    }

    protected void onRefreshFinished() {
        // Optional for subclasses.
    }

    protected void updateStatusTimestamp() {
        if (statusView == null) {
            return;
        }
        String time = DateFormat.getTimeFormat(requireContext()).format(new Date());
        statusView.setText(getString(R.string.refresh_status_updated, time));
        statusView.setAlpha(0f);
        statusView.animate().alpha(1f).setDuration(200L).start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
        swipeRefreshLayout = null;
        statusView = null;
    }

    private int dp(@NonNull Context context, int dp) {
        return Math.round(context.getResources().getDisplayMetrics().density * dp);
    }
}
