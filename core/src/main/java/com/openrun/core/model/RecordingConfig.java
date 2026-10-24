package com.openrun.core.model;

/**
 * Tunables for the tracking engine.
 */
public final class RecordingConfig {
    private final ActivityType type;
    private final int maxAccuracyMeters;
    private final int warmupAccuracyMeters;
    private final double maxSpeedMps;
    private final boolean autoPauseEnabled;

    public RecordingConfig(ActivityType type,
                           int maxAccuracyMeters,
                           int warmupAccuracyMeters,
                           double maxSpeedMps,
                           boolean autoPauseEnabled) {
        if (type == null) {
            throw new IllegalArgumentException("type == null");
        }
        if (maxAccuracyMeters <= 0 || warmupAccuracyMeters <= 0) {
            throw new IllegalArgumentException("accuracy thresholds must be positive");
        }
        if (maxSpeedMps <= 0) {
            throw new IllegalArgumentException("maxSpeedMps must be positive");
        }
        this.type = type;
        this.maxAccuracyMeters = maxAccuracyMeters;
        this.warmupAccuracyMeters = warmupAccuracyMeters;
        this.maxSpeedMps = maxSpeedMps;
        this.autoPauseEnabled = autoPauseEnabled;
    }

    public static RecordingConfig defaults(ActivityType type) {
        if (type == ActivityType.WALK) {
            // Start on any fix that is accurate enough to be recorded. Waiting
            // for a stricter "warmup" fix used to discard the first part of the
            // activity while the session stayed in ACQUIRING.
            return new RecordingConfig(type, 30, 30, 8.0d, false);
        }
        return new RecordingConfig(type, 25, 25, 15.0d, false);
    }

    public ActivityType getType() {
        return type;
    }

    public int getMaxAccuracyMeters() {
        return maxAccuracyMeters;
    }

    public int getWarmupAccuracyMeters() {
        return warmupAccuracyMeters;
    }

    public double getMaxSpeedMps() {
        return maxSpeedMps;
    }

    public boolean isAutoPauseEnabled() {
        return autoPauseEnabled;
    }
}
