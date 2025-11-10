package com.cosmoscout.ui.home;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cosmoscout.R;
import com.cosmoscout.ui.RefreshableFragment;

/**
 * Home surface with a swipe-to-refresh scaffold and subtle copy rotation to prove the refresh.
 */
public class HomeFragment extends RefreshableFragment {

    private static final int[] REFRESH_MESSAGES = {
            R.string.home_refresh_message_1,
            R.string.home_refresh_message_2,
            R.string.home_refresh_message_3,
            R.string.home_refresh_message_4
    };

    private TextView messageView;
    private int messageIndex = -1;

    public HomeFragment() {
        super(R.layout.fragment_home);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        messageView = view.findViewById(R.id.homeMessage);
    }

    @Override
    protected void onRefreshFinished() {
        super.onRefreshFinished();
        if (messageView == null) {
            return;
        }
        messageIndex = (messageIndex + 1) % REFRESH_MESSAGES.length;
        messageView.setText(getString(REFRESH_MESSAGES[messageIndex]));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        messageView = null;
        messageIndex = -1;
    }
}
