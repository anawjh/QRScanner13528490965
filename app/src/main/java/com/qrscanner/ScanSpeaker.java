package com.qrscanner;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

public final class ScanSpeaker {

    private static final String PREF_LANG = "app_lang";
    private static final String PREF_SCAN = "scan_settings";
    public static final String KEY_VOICE_PROMPT = "voice_prompt";
    private static final String LANG_EN = "en";
    private static final String LANG_ZH = "zh";

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
        return prefs(context).getBoolean(KEY_VOICE_PROMPT, true);
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_VOICE_PROMPT, enabled).apply();
    }

    public static void speakScanSuccess(Context context) {
        if (!isEnabled(context)) return;
        init(context);

        boolean english = LANG_EN.equals(getCurrentLang(context));
        String text = context.getString(english
            ? R.string.voice_scan_success_en
            : R.string.voice_scan_success_zh);
        speakNow(text, english ? Locale.US : Locale.SIMPLIFIED_CHINESE);
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
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "scan_success");
    }

    private static String getCurrentLang(Context context) {
        return context.getSharedPreferences(PREF_LANG, Context.MODE_PRIVATE)
            .getString(PREF_LANG, LANG_ZH);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF_SCAN, Context.MODE_PRIVATE);
    }
}
