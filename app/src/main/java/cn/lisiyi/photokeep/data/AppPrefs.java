package cn.lisiyi.photokeep.data;

import android.content.Context;
import android.content.res.Configuration;
import java.util.Locale;

/** Stores and applies user-selected language and theme preferences without AppCompat. */
public final class AppPrefs {
    public static final String PREFS_NAME = "photokeep_prefs";
    public static final String KEY_LANGUAGE = "language";
    public static final String LANG_SYSTEM = "system";
    public static final String LANG_ZH = "zh";
    public static final String LANG_EN = "en";
    public static final String KEY_THEME = "theme";
    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

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

    public static String theme(Context context) {
        String value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_THEME, THEME_SYSTEM);
        return THEME_LIGHT.equals(value) || THEME_DARK.equals(value) ? value : THEME_SYSTEM;
    }

    public static void setTheme(Context context, String value) {
        String normalized = THEME_LIGHT.equals(value) || THEME_DARK.equals(value) ? value : THEME_SYSTEM;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_THEME, normalized)
                .commit();
    }

    /** Returns a configuration-wrapped context when a non-system language or theme override is active. */
    public static Context wrap(Context base) {
        String lang = language(base);
        String theme = theme(base);
        if (LANG_SYSTEM.equals(lang) && THEME_SYSTEM.equals(theme)) return base;
        Configuration config = new Configuration(base.getResources().getConfiguration());
        if (LANG_ZH.equals(lang)) config.setLocale(Locale.SIMPLIFIED_CHINESE);
        else if (LANG_EN.equals(lang)) config.setLocale(Locale.ENGLISH);
        if (THEME_LIGHT.equals(theme)) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
        } else if (THEME_DARK.equals(theme)) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
        }
        return base.createConfigurationContext(config);
    }
}
