package cn.lisiyi.photokeep.data;

import android.content.Context;
import android.content.res.Configuration;
import java.util.Locale;

/** Stores and applies user-selected language preferences without AppCompat. */
public final class AppPrefs {
    public static final String PREFS_NAME = "photokeep_prefs";
    public static final String KEY_LANGUAGE = "language";
    public static final String LANG_SYSTEM = "system";
    public static final String LANG_ZH = "zh";
    public static final String LANG_EN = "en";

    private AppPrefs() {}

    public static String language(Context context) {
        String value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, LANG_SYSTEM);
        return LANG_ZH.equals(value) || LANG_EN.equals(value) ? value : LANG_SYSTEM;
    }

    public static void setLanguage(Context context, String value) {
        String normalized = LANG_ZH.equals(value) || LANG_EN.equals(value) ? value : LANG_SYSTEM;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LANGUAGE, normalized)
                .commit();
    }

    /** Returns a configuration-wrapped context when a non-system language override is active. */
    public static Context wrap(Context base) {
        String lang = language(base);
        if (LANG_SYSTEM.equals(lang)) return base;
        Configuration config = new Configuration(base.getResources().getConfiguration());
        if (LANG_ZH.equals(lang)) config.setLocale(Locale.SIMPLIFIED_CHINESE);
        else if (LANG_EN.equals(lang)) config.setLocale(Locale.ENGLISH);
        return base.createConfigurationContext(config);
    }
}
