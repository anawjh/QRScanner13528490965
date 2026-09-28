package com.qrscanner;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

public final class ScanSpeaker {

    private static final String PREF_LANG = "app_lang";
    private static final String LANG_EN = "en";
    private static final String LANG_ZH = "zh";

    private static final long[] SUCCESS_PATTERN = {0, 60};
    private static final long[] BLOCKED_PATTERN = {0, 200, 120, 200};

    private static TextToSpeech tts;
    private static boolean ready = false;
    private static String pendingText;
    private static Locale pendingLocale;

    private ScanSpeaker() {}

    public static void init(Context context) {
        if (tts != null) return;
        tts = new TextToSpeech(context.getApplicationContext(), status -> {
            if (status != TextToSpeech.SUCCESS) {
                ready = false;
                return;
            }
            ready = true;
            tts.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
            if (pendingText != null) {
                String text = pendingText;
                Locale locale = pendingLocale;
                pendingText = null;
                pendingLocale = null;
                speakNow(text, locale);
            }
        });
    }

    public static boolean isEnabled(Context context) {
        return ScanSettings.soundEnabled(context);
    }

    public static void setEnabled(Context context, boolean enabled) {
        ScanSettings.setAlertMode(context,
            enabled ? ScanSettings.ALERT_SOUND_VIBRATE : ScanSettings.ALERT_NONE);
    }

    public static void alertSuccess(Context context) {
        if (ScanSettings.soundEnabled(context)) {
            speakScanSuccess(context);
        }
        if (ScanSettings.vibrateEnabled(context)) {
            vibrate(context, SUCCESS_PATTERN);
        }
    }

    public static void alertBlocked(Context context) {
        speakBlocked(context);
        vibrate(context, BLOCKED_PATTERN);
    }

    public static void speakScanSuccess(Context context) {
        boolean english = LANG_EN.equals(getCurrentLang(context));
        String text = context.getString(english
            ? R.string.voice_scan_success_en
            : R.string.voice_scan_success_zh);
        speakNow(text, english ? Locale.US : Locale.SIMPLIFIED_CHINESE);
    }

    public static void speakBlocked(Context context) {
        boolean english = LANG_EN.equals(getCurrentLang(context));
        String text = context.getString(english
            ? R.string.voice_blocked_en
            : R.string.voice_blocked_zh);
        speakNow(text, english ? Locale.US : Locale.SIMPLIFIED_CHINESE);
    }

    public static void vibrate(Context context, long[] pattern) {
        try {
            Vibrator vibrator = vibrator(context);
            if (vibrator == null || !vibrator.hasVibrator()) return;
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
        } catch (Exception ignored) {
        }
    }

    public static void shutdown() {
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception ignored) {
            }
        }
        tts = null;
        ready = false;
        pendingText = null;
        pendingLocale = null;
    }

    private static Vibrator vibrator(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager manager = (VibratorManager)
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return manager == null ? null : manager.getDefaultVibrator();
        }
        return (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    private static void speakNow(String text, Locale locale) {
        if (tts == null || !ready) {
            pendingText = text;
            pendingLocale = locale;
            return;
        }
        int result = tts.setLanguage(locale);
        if (result == TextToSpeech.LANG_MISSING_DATA
                || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            return;
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "scan_alert");
    }

    private static String getCurrentLang(Context context) {
        return context.getSharedPreferences(PREF_LANG, Context.MODE_PRIVATE)
            .getString(PREF_LANG, LANG_ZH);
    }
}
