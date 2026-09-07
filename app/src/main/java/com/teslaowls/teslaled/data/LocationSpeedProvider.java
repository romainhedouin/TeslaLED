package com.teslaowls.teslaled.data;

import android.location.Location;
import android.location.LocationListener;

/**
 * Tracks the device's current ground speed via LocationManager updates
 * (registered/unregistered by whoever owns this, typically tied to the
 * Activity's onResume/onPause). getSpeedKmh() just reads the last known
 * value - it's cheap to call from a once-a-second render tick.
 */
public class LocationSpeedProvider implements SpeedProvider, LocationListener {

    private volatile double lastSpeedKmh = 0;
    private volatile boolean hasFix = false;

    @Override
    public double getSpeedKmh() {
        return lastSpeedKmh;
    }

    public boolean hasFix() {
        return hasFix;
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location.hasSpeed()) {
            lastSpeedKmh = location.getSpeed() * 3.6;
            hasFix = true;
        }
    }
}
