package com.qrscanner;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public final class ScanSettings {

    private static final String PREF_NAME = "scan_settings";

    private static final String KEY_ALERT_MODE = "alert_mode";
    private static final String KEY_INTERVAL_INDEX = "scan_interval_index";
    private static final String KEY_SAVE_PHOTO = "save_photo";
    private static final String KEY_CONTINUOUS = "continuous_scan";

    private static final String KEY_FILTER_ALPHA = "filter_alpha";
    private static final String KEY_FILTER_DIGIT = "filter_digit";
    private static final String KEY_FILTER_FORMATS = "filter_formats";
    private static final String KEY_FILTER_LENGTH = "filter_length";

    public static final String ALERT_SOUND_VIBRATE = "sound_vibrate";
    public static final String ALERT_SOUND = "sound";
    public static final String ALERT_VIBRATE = "vibrate";
    public static final String ALERT_NONE = "none";

    public static final String[] ALERT_MODES =
        {ALERT_SOUND_VIBRATE, ALERT_SOUND, ALERT_VIBRATE, ALERT_NONE};

    public static final int[] INTERVALS_MS =
        {500, 1000, 1500, 2000, 2500, 3000, 5000, 8000, 10000, 15000, 20000};

    private ScanSettings() {}

    public static String getAlertMode(Context context) {
        return prefs(context).getString(KEY_ALERT_MODE, ALERT_SOUND_VIBRATE);
    }

    public static void setAlertMode(Context context, String mode) {
        prefs(context).edit().putString(KEY_ALERT_MODE, mode).apply();
    }

    public static boolean soundEnabled(Context context) {
        String mode = getAlertMode(context);
        return ALERT_SOUND.equals(mode) || ALERT_SOUND_VIBRATE.equals(mode);
    }

    public static boolean vibrateEnabled(Context context) {
        String mode = getAlertMode(context);
        return ALERT_VIBRATE.equals(mode) || ALERT_SOUND_VIBRATE.equals(mode);
    }

    public static int getIntervalIndex(Context context) {
        return prefs(context).getInt(KEY_INTERVAL_INDEX, 1);
    }

    public static void setIntervalIndex(Context context, int index) {
        prefs(context).edit().putInt(KEY_INTERVAL_INDEX, index).apply();
    }

    public static int getIntervalMs(Context context) {
        int index = getIntervalIndex(context);
        if (index < 0 || index >= INTERVALS_MS.length) index = 1;
        return INTERVALS_MS[index];
    }

    public static boolean isSavePhoto(Context context) {
        return prefs(context).getBoolean(KEY_SAVE_PHOTO, false);
    }

    public static void setSavePhoto(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_SAVE_PHOTO, enabled).apply();
    }

    public static boolean isContinuousEnabled(Context context) {
        return prefs(context).getBoolean(KEY_CONTINUOUS, true);
    }

    public static void setContinuousEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_CONTINUOUS, enabled).apply();
    }

    public static boolean isAlphaOnly(Context context) {
        return prefs(context).getBoolean(KEY_FILTER_ALPHA, false);
    }

    public static void setAlphaOnly(Context context, boolean enabled) {
        prefs(context).edit()
            .putBoolean(KEY_FILTER_ALPHA, enabled)
            .putBoolean(KEY_FILTER_DIGIT, enabled ? false : isDigitOnly(context))
            .apply();
    }

    public static boolean isDigitOnly(Context context) {
        return prefs(context).getBoolean(KEY_FILTER_DIGIT, false);
    }

    public static void setDigitOnly(Context context, boolean enabled) {
        prefs(context).edit()
            .putBoolean(KEY_FILTER_DIGIT, enabled)
            .putBoolean(KEY_FILTER_ALPHA, enabled ? false : isAlphaOnly(context))
            .apply();
    }

    public static void setFormats(Context context, Set<String> formats) {
        prefs(context).edit().putStringSet(KEY_FILTER_FORMATS, formats).apply();
    }

    public static Set<String> getFormats(Context context) {
        return new HashSet<>(prefs(context).getStringSet(KEY_FILTER_FORMATS,
            new HashSet<>()));
    }

    public static int getFixedLength(Context context) {
        return prefs(context).getInt(KEY_FILTER_LENGTH, 0);
    }

    public static void setFixedLength(Context context, int length) {
        prefs(context).edit().putInt(KEY_FILTER_LENGTH, length).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
}
