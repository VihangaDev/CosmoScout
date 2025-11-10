package com.cosmoscout.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public abstract class BaseFragment extends Fragment {

    protected BaseFragment(@LayoutRes int layoutId) {
        super(layoutId);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Common hooks can land here later.
    }

    protected boolean isSafe() {
        Activity activity = getActivity();
        return getView() != null
                && isAdded()
                && activity != null
                && !activity.isFinishing()
                && !activity.isDestroyed();
    }
}
