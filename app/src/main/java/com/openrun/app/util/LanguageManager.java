package com.openrun.app.util;

import android.app.Activity;
import android.content.Context;
import android.os.Build;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;

/**
 * In-app language selection. On Android 13+ the platform persists the
 * per-app locale; on older versions the choice is stored in preferences and
 * applied when the process starts.
 */
public final class LanguageManager {
    public static final String SYSTEM = "";
    public static final String ENGLISH = "en";
    public static final String PORTUGUESE = "pt";

    private static final String PREFS = "app_settings";
    private static final String KEY_LANGUAGE = "app_language";

    private LanguageManager() {
    }

    public static void applySavedLanguage(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        try {
            AppCompatDelegate.setApplicationLocales(toLocales(getSavedTag(context)));
        } catch (RuntimeException ignored) {
            // Language is a preference; a failure here must not block startup.
        }
    }

    public static String getSavedTag(Context context) {
        String tag = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, SYSTEM);
        return tag == null ? SYSTEM : tag;
    }

    public static String getCurrentTag(Context context) {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        if (!locales.isEmpty()) {
            Locale locale = locales.get(0);
            if (locale != null && locale.getLanguage() != null) {
                return locale.getLanguage();
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            String saved = getSavedTag(context);
            if (!saved.isEmpty()) {
                return saved;
            }
        }
        return Locale.getDefault().getLanguage();
    }

    public static void setLanguage(Activity activity, String tag) {
        String safeTag;
        if (PORTUGUESE.equals(tag)) {
            safeTag = PORTUGUESE;
        } else if (ENGLISH.equals(tag)) {
            safeTag = ENGLISH;
        } else {
            safeTag = SYSTEM;
        }

        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LANGUAGE, safeTag)
                .apply();

        AppCompatDelegate.setApplicationLocales(toLocales(safeTag));

        // The AppCompat backport usually recreates activities itself, but this
        // guarantees an immediate refresh on API < 33.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            activity.recreate();
        }
    }

    private static LocaleListCompat toLocales(String tag) {
        if (tag == null || tag.isEmpty()) {
            return LocaleListCompat.getEmptyLocaleList();
        }
        return LocaleListCompat.forLanguageTags(tag);
    }
}
