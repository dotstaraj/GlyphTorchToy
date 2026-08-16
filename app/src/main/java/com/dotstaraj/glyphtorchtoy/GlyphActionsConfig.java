package com.dotstaraj.glyphtorchtoy;

import android.content.Context;
import android.content.SharedPreferences;

/** Reads/writes the per-event configured action targets and the extra
 * long press timer duration. Shared between MainActivity (writes) and
 * GlyphTorchToyService (reads). */
class GlyphActionsConfig {
    private static final String PREFS_NAME = "glyph_actions";

    static final String KEY_ACTIVATED = "action_activated";
    static final String KEY_DEACTIVATED = "action_deactivated";
    static final String KEY_LONGPRESS = "action_longpress";
    static final String KEY_EXTRA_LONGPRESS = "action_extralongpress";

    private static final String KEY_EXTRA_LONGPRESS_TIMER_MS = "extra_longpress_timer_ms";
    static final int DEFAULT_EXTRA_LONGPRESS_TIMER_MS = 1000;

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    static ActionTarget get(Context context, String key) {
        return ActionTarget.decode(prefs(context).getString(key, null));
    }

    static void set(Context context, String key, ActionTarget target) {
        prefs(context).edit().putString(key, target.encode()).apply();
    }

    static int getExtraLongPressTimerMs(Context context) {
        return prefs(context).getInt(KEY_EXTRA_LONGPRESS_TIMER_MS, DEFAULT_EXTRA_LONGPRESS_TIMER_MS);
    }

    static void setExtraLongPressTimerMs(Context context, int ms) {
        prefs(context).edit().putInt(KEY_EXTRA_LONGPRESS_TIMER_MS, ms).apply();
    }
}
