package com.openrun.app.tracking;

public final class TrackingContract {
    public static final String ACTION_START = "com.openrun.app.action.START";
    public static final String ACTION_RECOVER = "com.openrun.app.action.RECOVER";
    public static final String ACTION_PAUSE = "com.openrun.app.action.PAUSE";
    public static final String ACTION_RESUME = "com.openrun.app.action.RESUME";
    public static final String ACTION_FINISH = "com.openrun.app.action.FINISH";
    public static final String ACTION_DISCARD = "com.openrun.app.action.DISCARD";

    public static final String EXTRA_TYPE = "com.openrun.app.extra.TYPE";

    private TrackingContract() {
    }
}
