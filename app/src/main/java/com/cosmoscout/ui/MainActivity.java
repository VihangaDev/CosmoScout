package com.cosmoscout.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.cosmoscout.R;
import com.cosmoscout.core.ThemeToggle;
import com.cosmoscout.ui.events.EventsFragment;
import com.cosmoscout.ui.home.HomeFragment;
import com.cosmoscout.ui.places.PlacesFragment;
import com.cosmoscout.ui.tonight.TonightFragment;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.lang.reflect.Method;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private View themeOverlay;
    private MaterialToolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        CoordinatorLayout root = findViewById(R.id.rootCoordinator);
        toolbar = findViewById(R.id.topAppBar);
        TextView title = findViewById(R.id.toolbarTitle);
        bottomNav = findViewById(R.id.bottomNavigation);
        themeOverlay = findViewById(R.id.themeRevealOverlay);

        title.setText(getString(R.string.app_name));

        final int toolbarStart  = ViewCompat.getPaddingStart(toolbar);
        final int toolbarTop    = toolbar.getPaddingTop();
        final int toolbarEnd    = ViewCompat.getPaddingEnd(toolbar);
        final int toolbarBottom = toolbar.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
            int types = WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout();
            Insets sys = insets.getInsets(types);
            ViewCompat.setPaddingRelative(
                    v,
                    toolbarStart + sys.left,
                    toolbarTop   + sys.top,
                    toolbarEnd   + sys.right,
                    toolbarBottom
            );
            return insets;
        });

        final int navTop = bottomNav.getPaddingTop();
        final int navBottom = bottomNav.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            bottomNav.setPadding(
                    bottomNav.getPaddingLeft(),
                    navTop,
                    bottomNav.getPaddingRight(),
                    navBottom + sys.bottom
            );
            return insets;
        });

        bottomNav.setItemIconTintList(getColorStateList(R.color.nav_item_tint));
        bottomNav.setItemTextColor(getColorStateList(R.color.nav_item_tint));
        bottomNav.setItemRippleColor(getColorStateList(R.color.nav_item_ripple_base));
        bottomNav.setItemBackground(new ColorDrawable(Color.TRANSPARENT));

        try {
            Method m = bottomNav.getClass().getMethod("setItemActiveIndicatorEnabled", boolean.class);
            m.invoke(bottomNav, false);
        } catch (Throwable ignored) {}
        try {
            Method m2 = bottomNav.getClass().getMethod(
                    "setItemActiveIndicatorColor", android.content.res.ColorStateList.class);
            m2.invoke(bottomNav, new Object[]{null});
        } catch (Throwable ignored) {}

        toolbar.post(() -> {
            MenuItem toggleItem = toolbar.getMenu().findItem(R.id.action_theme_toggle);
            if (toggleItem == null) return;

            View toggleRoot = toggleItem.getActionView();
            if (toggleRoot == null) return;

            final com.google.android.material.card.MaterialCardView thumb = toggleRoot.findViewById(R.id.thumb);
            final ImageView iconSun = toggleRoot.findViewById(R.id.iconSun);
            final ImageView iconMoon = toggleRoot.findViewById(R.id.iconMoon);

            if (thumb == null || iconSun == null || iconMoon == null) return;

            final View capsule = toggleRoot;

            boolean isDark = ThemeToggle.isDark(this);

            int sunTint = ContextCompat.getColor(this, R.color.toggle_sun_tint);
            int moonTint = ContextCompat.getColor(this, R.color.toggle_moon_tint);
            iconSun.setImageTintList(android.content.res.ColorStateList.valueOf(sunTint));
            iconMoon.setImageTintList(android.content.res.ColorStateList.valueOf(moonTint));

            applyToggleColors(capsule, thumb, isDark);
            positionThumb(capsule, thumb, isDark, false);
            crossfadeIcons(iconSun, iconMoon, isDark, false);

            capsule.setOnClickListener(v -> {
                boolean next = !ThemeToggle.isDark(this);
                capsule.setEnabled(false);

                positionThumb(capsule, thumb, next, true);
                applyToggleColors(capsule, thumb, next);
                crossfadeIcons(iconSun, iconMoon, next, true);

                startThemeReveal(next, capsule, () -> {
                    ThemeToggle.setDark(MainActivity.this, next);
                    capsule.setEnabled(true);
                });
            });
        });

        if (savedInstanceState == null) {
            showFragment(new HomeFragment());
            bottomNav.setSelectedItemId(R.id.menu_home);
        }
        bottomNav.setOnItemSelectedListener(this::onNavItemSelected);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                    getSupportFragmentManager().popBackStack();
                } else if (bottomNav.getSelectedItemId() != R.id.menu_home) {
                    bottomNav.setSelectedItemId(R.id.menu_home);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private boolean onNavItemSelected(@NonNull MenuItem item) {
        Fragment fragment = null;
        int id = item.getItemId();

        if (id == R.id.menu_home) {
            fragment = new HomeFragment();
        } else if (id == R.id.menu_tonight) {
            fragment = new TonightFragment();
        } else if (id == R.id.menu_events) {
            fragment = new EventsFragment();
        } else if (id == R.id.menu_places) {
            fragment = new PlacesFragment();
        }

        if (fragment != null) {
            showFragment(fragment);
            return true;
        }
        return false;
    }

    private void showFragment(@NonNull Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.fragmentContainer, fragment, fragment.getClass().getSimpleName())
                .commit();
    }

    private void positionThumb(View root, View thumb, boolean dark, boolean animate) {
        root.post(() -> {
            int w = root.getWidth();
            int tw = thumb.getWidth();
            int margin = dp(4);
            float endX = dark ? (w - tw - margin) : margin;

            if (animate) {
                thumb.animate()
                        .x(endX)
                        .setDuration(220)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(0.8f))
                        .start();
            } else {
                thumb.setX(endX);
            }
        });
    }

    private void startThemeReveal(boolean toDark, View sourceView, Runnable onEnd) {
        if (themeOverlay == null || sourceView == null) {
            onEnd.run();
            return;
        }

        Configuration cfg = new Configuration(getResources().getConfiguration());
        cfg.uiMode = (cfg.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (toDark ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        Context themedCtx = createConfigurationContext(cfg);
        final int background = ContextCompat.getColor(themedCtx, R.color.colorBackground);

        themeOverlay.setBackgroundColor(background);
        themeOverlay.setAlpha(1f);
        themeOverlay.setVisibility(View.VISIBLE);
        themeOverlay.bringToFront();
        ViewCompat.setElevation(themeOverlay, Math.max(ViewCompat.getElevation(themeOverlay), dp(100)));
        themeOverlay.setClickable(true);

        final int oldNavVisibility = bottomNav != null ? bottomNav.getVisibility() : View.VISIBLE;
        if (bottomNav != null) {
            bottomNav.setVisibility(View.INVISIBLE);
        }

        final MaterialToolbar toolbarRef = toolbar;
        final float originalToolbarZ = toolbarRef != null ? ViewCompat.getZ(toolbarRef) : 0f;
        final boolean raisedToolbar = toolbarRef != null;
        if (raisedToolbar) {
            ViewCompat.setZ(toolbarRef, ViewCompat.getElevation(themeOverlay) + 1f);
        }

        themeOverlay.post(() -> {
            int width = themeOverlay.getWidth();
            int height = themeOverlay.getHeight();
            if (width == 0 || height == 0) {
                if (raisedToolbar && toolbarRef != null) {
                    ViewCompat.setZ(toolbarRef, originalToolbarZ);
                }
                if (bottomNav != null) {
                    bottomNav.setVisibility(oldNavVisibility);
                }
                themeOverlay.setVisibility(View.GONE);
                onEnd.run();
                return;
            }

            int[] srcLoc = new int[2];
            int[] overlayLoc = new int[2];
            sourceView.getLocationInWindow(srcLoc);
            themeOverlay.getLocationInWindow(overlayLoc);
            int cx = srcLoc[0] - overlayLoc[0] + sourceView.getWidth() / 2;
            int cy = srcLoc[1] - overlayLoc[1] + sourceView.getHeight() / 2;

            float finalRadius = (float) Math.hypot(width, height);
            float startRadius = Math.max(sourceView.getWidth(), sourceView.getHeight()) * 0.5f;

            Animator anim = ViewAnimationUtils.createCircularReveal(themeOverlay, cx, cy, startRadius, finalRadius);
            anim.setDuration(420L);
            anim.setInterpolator(new AccelerateDecelerateInterpolator());
            anim.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    if (raisedToolbar && toolbarRef != null) {
                        ViewCompat.setZ(toolbarRef, originalToolbarZ);
                    }
                    onEnd.run();
                }

                @Override public void onAnimationCancel(Animator animation) {
                    if (bottomNav != null) {
                        bottomNav.setVisibility(oldNavVisibility);
                    }
                    if (raisedToolbar && toolbarRef != null) {
                        ViewCompat.setZ(toolbarRef, originalToolbarZ);
                    }
                    themeOverlay.setVisibility(View.GONE);
                }
            });
            anim.start();
        });
    }

    private void applyToggleColors(View capsule,
                                   com.google.android.material.card.MaterialCardView thumb,
                                   boolean dark) {
        capsule.setSelected(dark);
        int thumbColor  = ContextCompat.getColor(this, R.color.toggle_thumb);
        int strokeColor = ContextCompat.getColor(this, R.color.toggle_star_tint);
        thumb.setCardBackgroundColor(thumbColor);
        thumb.setStrokeColor(strokeColor);
    }

    private void crossfadeIcons(ImageView sun, ImageView moon, boolean dark, boolean animate) {
        float sunAlpha  = dark ? 0f : 1f;
        float moonAlpha = dark ? 1f : 0f;
        if (animate) {
            sun.animate().alpha(sunAlpha).setDuration(150).start();
            moon.animate().alpha(moonAlpha).setDuration(150).start();
        } else {
            sun.setAlpha(sunAlpha);
            moon.setAlpha(moonAlpha);
        }
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
