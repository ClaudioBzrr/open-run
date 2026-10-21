package com.openrun.app.tracking;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * Barometric altitude with a GPS-based initial calibration. The pressure
 * sensor provides much smoother relative elevation than GPS altitude alone;
 * GPS is used only to anchor the absolute value.
 */
public final class BarometerTracker implements SensorEventListener {
    private static final double SEA_LEVEL_HPA = 1013.25d;

    private final SensorManager sensorManager;
    private final Sensor pressureSensor;

    private boolean running;
    private double pressureHpa = Double.NaN;
    private double rawAltitudeMeters = Double.NaN;
    private double calibrationOffset = Double.NaN;

    public BarometerTracker(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        pressureSensor = sensorManager == null
                ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
    }

    public boolean isAvailable() {
        return sensorManager != null && pressureSensor != null;
    }

    public void start() {
        if (running || !isAvailable()) {
            return;
        }
        pressureHpa = Double.NaN;
        rawAltitudeMeters = Double.NaN;
        calibrationOffset = Double.NaN;
        running = sensorManager.registerListener(
                this,
                pressureSensor,
                SensorManager.SENSOR_DELAY_NORMAL
        );
    }

    public void stop() {
        if (sensorManager != null && running) {
            sensorManager.unregisterListener(this);
        }
        running = false;
    }

    /**
     * Returns a GPS-anchored barometric altitude, or NaN while the sensor has
     * no reading or the first GPS altitude calibration is still missing.
     */
    public double altitudeWithGpsCalibration(double gpsAltitudeMeters) {
        if (Double.isNaN(rawAltitudeMeters)) {
            return Double.NaN;
        }
        if (Double.isNaN(calibrationOffset)) {
            if (RawFixMath.isFinite(gpsAltitudeMeters)) {
                calibrationOffset = gpsAltitudeMeters - rawAltitudeMeters;
            } else {
                return Double.NaN;
            }
        }
        return rawAltitudeMeters + calibrationOffset;
    }

    public boolean hasReading() {
        return !Double.isNaN(rawAltitudeMeters);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor == null || event.sensor.getType() != Sensor.TYPE_PRESSURE) {
            return;
        }
        if (event.values.length == 0) {
            return;
        }
        double pressure = event.values[0];
        if (!RawFixMath.isFinite(pressure) || pressure <= 0) {
            return;
        }
        pressureHpa = pressure;
        double altitude = 44330.0d * (
                1.0d - Math.pow(pressureHpa / SEA_LEVEL_HPA, 1.0d / 5.255d)
        );
        if (Double.isNaN(rawAltitudeMeters)) {
            rawAltitudeMeters = altitude;
        } else {
            rawAltitudeMeters = rawAltitudeMeters + 0.35d * (altitude - rawAltitudeMeters);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed: pressure accuracy is not surfaced by Android.
    }

    /**
     * Tiny helper kept here to avoid depending on Android types in the core.
     */
    private static final class RawFixMath {
        static boolean isFinite(double value) {
            return !Double.isNaN(value) && !Double.isInfinite(value);
        }
    }
}
