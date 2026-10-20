package com.openrun.app.util;

import android.content.Context;
import android.os.LocaleList;

import com.openrun.app.R;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

public final class Formatters {
    private Formatters() {
    }

    public static String distance(Context context, double meters) {
        double safeMeters = isFinite(meters) && meters > 0 ? meters : 0;
        if (safeMeters < 1000.0d) {
            return context.getString(R.string.format_distance_m, Math.round(safeMeters));
        }
        return context.getString(R.string.format_distance_km, safeMeters / 1000.0d);
    }

    public static String duration(long durationMs) {
        long totalSeconds = Math.max(0, durationMs) / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    public static String pace(Context context, double secondsPerKm) {
        if (!isFinite(secondsPerKm) || secondsPerKm <= 0) {
            return context.getString(R.string.value_unknown);
        }
        long totalSeconds = Math.round(secondsPerKm);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return context.getString(R.string.format_pace, minutes, seconds);
    }

    public static String accuracy(Context context, double meters) {
        if (!isFinite(meters) || meters <= 0) {
            return context.getString(R.string.value_unknown);
        }
        return context.getString(R.string.format_accuracy, Math.round(meters));
    }

    public static String elevation(Context context, double meters) {
        if (!isFinite(meters) || meters < 0) {
            return context.getString(R.string.value_unknown);
        }
        return context.getString(R.string.format_elevation_m, Math.round(meters));
    }

    public static String dateTime(Context context, long epochMs) {
        if (epochMs <= 0) {
            return context.getString(R.string.value_unknown);
        }
        return DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM,
                DateFormat.SHORT,
                appLocale(context)
        ).format(new Date(epochMs));
    }

    public static String date(Context context, long epochMs) {
        if (epochMs <= 0) {
            return context.getString(R.string.value_unknown);
        }
        return DateFormat.getDateInstance(
                DateFormat.MEDIUM,
                appLocale(context)
        ).format(new Date(epochMs));
    }

    public static String time(Context context, long epochMs) {
        if (epochMs <= 0) {
            return context.getString(R.string.value_unknown);
        }
        return DateFormat.getTimeInstance(
                DateFormat.SHORT,
                appLocale(context)
        ).format(new Date(epochMs));
    }

    private static Locale appLocale(Context context) {
        LocaleList locales = context.getResources().getConfiguration().getLocales();
        return locales.isEmpty() ? Locale.getDefault() : locales.get(0);
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
