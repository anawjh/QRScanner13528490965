package com.qrscanner;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
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

    private static final long[] SUCCESS_PATTERN = {0, 130};
    private static final long[] BLOCKED_PATTERN = {0, 250, 140, 250};

    private static final AudioAttributes SONIFICATION = new AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build();

    private static final AudioAttributes VIBRATE_ATTRIBUTES = new AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build();

    private static SoundPool pool;
    private static int okSound;
    private static int blockedSound;
    private static boolean okLoaded;
    private static boolean blockedLoaded;
    private static boolean pendingTest;

    private static TextToSpeech tts;
    private static boolean ready = false;
    private static String pendingText;
    private static Locale pendingLocale;
    private static Locale spokenLocale;

    private ScanSpeaker() {}

    public static void init(Context context) {
        initSound(context);
        initSpeech(context);
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
            play(okSound, okLoaded);
            speakScanSuccess(context);
        }
        if (ScanSettings.vibrateEnabled(context)) {
            vibrate(context, SUCCESS_PATTERN);
        }
    }

    public static void alertBlocked(Context context) {
        if (ScanSettings.soundEnabled(context)) {
            play(blockedSound, blockedLoaded);
            speakBlocked(context);
        }
        if (ScanSettings.vibrateEnabled(context)) {
            vibrate(context, BLOCKED_PATTERN);
        }
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

    public static void testSound() {
        if (okLoaded) {
            play(okSound, okLoaded);
        } else {
            pendingTest = true;
        }
    }

    public static void vibrate(Context context, long[] pattern) {
        Vibrator vibrator = vibrator(context);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        try {
            int[] amplitudes = new int[pattern.length];
            for (int i = 0; i < pattern.length; i++) {
                amplitudes[i] = pattern[i] == 0 ? 0 : 255;
            }
            if (vibrator.hasAmplitudeControl()) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1),
                    VIBRATE_ATTRIBUTES);
            } else {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1),
                    VIBRATE_ATTRIBUTES);
            }
        } catch (Exception ignored) {
        }
    }

    public static boolean canVibrate(Context context) {
        Vibrator vibrator = vibrator(context);
        return vibrator != null && vibrator.hasVibrator();
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
        spokenLocale = null;

        if (pool != null) {
            try {
                pool.release();
            } catch (Exception ignored) {
            }
        }
        pool = null;
        okSound = 0;
        blockedSound = 0;
        okLoaded = false;
        blockedLoaded = false;
        pendingTest = false;
    }

    // ======================== 提示音 ========================

    private static void initSound(Context context) {
        if (pool != null) return;
        try {
            pool = new SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(SONIFICATION)
                .build();
            pool.setOnLoadCompleteListener((sp, soundId, status) -> {
                if (status != 0) return;
                if (soundId == okSound) okLoaded = true;
                if (soundId == blockedSound) blockedLoaded = true;
                if (pendingTest && okLoaded) {
                    pendingTest = false;
                    play(okSound, okLoaded);
                }
            });
            Context app = context.getApplicationContext();
            okSound = pool.load(app, R.raw.scan_beep_ok, 1);
            blockedSound = pool.load(app, R.raw.scan_beep_blocked, 1);
        } catch (Exception e) {
            pool = null;
            okSound = 0;
            blockedSound = 0;
        }
    }

    private static void play(int soundId, boolean loaded) {
        SoundPool sp = pool;
        if (sp == null || !loaded || soundId == 0) return;
        try {
            sp.play(soundId, 1f, 1f, 1, 0, 1f);
        } catch (Exception ignored) {
        }
    }

    // ======================== 语音播报 ========================

    private static void initSpeech(Context context) {
        if (tts != null) return;
        try {
            tts = new TextToSpeech(context.getApplicationContext(), status -> {
                if (status != TextToSpeech.SUCCESS) {
                    ready = false;
                    tts = null;
                    return;
                }
                ready = true;
                tts.setAudioAttributes(SONIFICATION);
                if (pendingText != null) {
                    String text = pendingText;
                    Locale locale = pendingLocale;
                    pendingText = null;
                    pendingLocale = null;
                    speakNow(text, locale);
                }
            });
        } catch (Exception e) {
            tts = null;
            ready = false;
        }
    }

    private static void speakNow(String text, Locale locale) {
        if (tts == null || !ready) {
            pendingText = text;
            pendingLocale = locale;
            return;
        }
        if (!locale.equals(spokenLocale)) {
            int result = tts.setLanguage(locale);
            if (result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                return;
            }
            spokenLocale = locale;
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "scan_alert");
    }

    private static Vibrator vibrator(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager manager = (VibratorManager)
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return manager == null ? null : manager.getDefaultVibrator();
        }
        return (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    private static String getCurrentLang(Context context) {
        return context.getSharedPreferences(PREF_LANG, Context.MODE_PRIVATE)
            .getString(PREF_LANG, LANG_ZH);
    }
}
