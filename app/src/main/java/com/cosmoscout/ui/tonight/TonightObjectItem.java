package com.cosmoscout.ui.tonight;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
public class TonightObjectItem {
    @DrawableRes
    private final int iconRes;
    private final String name;
    private final String direction;
    private final String altitude;

    public TonightObjectItem(@DrawableRes int iconRes,
                             @NonNull String name,
                             @NonNull String direction,
                             @NonNull String altitude)
    {
        this.iconRes = iconRes;
        this.name = name;
        this.direction= direction;
        this.altitude = altitude;
    }
    @DrawableRes

    public int getIconRes() {
        return iconRes;
    }

    public String getName() {
        return name;
    }

    public String getDirection() {
        return direction;
    }

    public String getAltitude() {
        return altitude;
    }
}
