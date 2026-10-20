package com.openrun.core.model;

/**
 * The kind of activity being recorded.
 */
public enum ActivityType {
    RUN("run"),
    WALK("walk");

    private final String key;

    ActivityType(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public static ActivityType fromKey(String key) {
        if (key != null) {
            for (ActivityType type : values()) {
                if (type.key.equals(key)) {
                    return type;
                }
            }
        }
        return RUN;
    }
}
