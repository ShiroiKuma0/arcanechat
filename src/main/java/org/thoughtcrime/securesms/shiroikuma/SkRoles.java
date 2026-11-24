package org.thoughtcrime.securesms.shiroikuma;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

/**
 * shiroikuma fork: reads and writes the colour roles of {@link SkRole}. Every value lives in the
 * default preferences (so the roles travel in the UI export); a role nobody has set shows its
 * default, which is derived from the two primaries or from another role, so changing the accent
 * moves everything that has no colour of its own.
 */
public final class SkRoles {
  private SkRoles() {}

  /** Device state, not a setting: it is in no export category. */
  private static final String MIGRATED = "sk_migrated_roles";

  private static final SkRole[] ALL = SkRole.values();
  private static final int[] cache = new int[ALL.length];
  private static final boolean[] cached = new boolean[ALL.length];
  private static SharedPreferences.OnSharedPreferenceChangeListener listener;

  private static SharedPreferences prefs(@NonNull Context context) {
    SharedPreferences p =
        PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    if (listener == null) {
      // An import, a reset or a picker writes a key: forget what was computed.
      listener = (sp, key) -> clearCache();
      p.registerOnSharedPreferenceChangeListener(listener);
    }
    return p;
  }

  private static synchronized void clearCache() {
    java.util.Arrays.fill(cached, false);
  }

  @ColorInt
  public static int get(@NonNull Context context, @NonNull SkRole role) {
    int i = role.ordinal();
    synchronized (SkRoles.class) {
      if (cached[i]) return cache[i];
    }
    int value;
    SharedPreferences p = prefs(context);
    if (p.contains(role.storageKey)) {
      try {
        value = p.getInt(role.storageKey, 0);
      } catch (ClassCastException e) {
        value = defaultOf(context, role);
      }
    } else {
      value = defaultOf(context, role);
    }
    synchronized (SkRoles.class) {
      cache[i] = value;
      cached[i] = true;
    }
    return value;
  }

  /** What the role shows while nobody has given it a colour of its own. */
  @ColorInt
  public static int defaultOf(@NonNull Context context, @NonNull SkRole role) {
    switch (role.kind) {
      case FROM:
        return get(context, SkRole.valueOf(role.from));
      case BLEND:
        return blend(
            get(context, SkRole.BACKGROUND), get(context, SkRole.ACCENT), role.arg / 100f);
      case ALPHA:
        return alpha(get(context, SkRole.valueOf(role.from)), role.arg / 100f);
      case FIXED:
      default:
        return role.arg;
    }
  }

  public static boolean isSet(@NonNull Context context, @NonNull SkRole role) {
    return prefs(context).contains(role.storageKey);
  }

  public static void set(@NonNull Context context, @NonNull SkRole role, @ColorInt int color) {
    prefs(context).edit().putInt(role.storageKey, color).apply();
    clearCache();
    SkPalette.invalidate(context);
  }

  public static void reset(@NonNull Context context, @NonNull SkRole role) {
    prefs(context).edit().remove(role.storageKey).apply();
    clearCache();
    SkPalette.invalidate(context);
  }

  public static void resetAll(@NonNull Context context) {
    SharedPreferences.Editor e = prefs(context).edit();
    for (SkRole r : ALL) e.remove(r.storageKey);
    e.apply();
    clearCache();
    SkPalette.invalidate(context);
  }

  /**
   * Once per install: carry over what older builds stored - the accent preset (a name) becomes
   * the accent role, and the single message colours of Step 2 become both directions.
   */
  public static void migrate(@NonNull Context context) {
    SharedPreferences p = prefs(context);
    if (p.getBoolean(MIGRATED, false)) return;
    SharedPreferences.Editor e = p.edit();
    String accent = p.getString("pref_accent", "yellow");
    int preset = 0;
    if ("white".equals(accent)) preset = 0xFFFFFFFF;
    else if ("cyan".equals(accent)) preset = 0xFF00E5FF;
    else if ("green".equals(accent)) preset = 0xFF00E676;
    else if ("orange".equals(accent)) preset = 0xFFFF9100;
    else if ("red".equals(accent)) preset = 0xFFFF1744;
    else if ("magenta".equals(accent)) preset = 0xFFFF44FF;
    else if ("blue".equals(accent)) preset = 0xFF448AFF;
    if (preset != 0 && !p.contains(SkRole.ACCENT.storageKey)) {
      e.putInt(SkRole.ACCENT.storageKey, preset);
    }
    carry(p, e, "pref_color_message_text", SkRole.MSG_TEXT_IN, SkRole.MSG_TEXT_OUT);
    carry(p, e, "pref_color_bubble_fill", SkRole.BUBBLE_FILL_IN, SkRole.BUBBLE_FILL_OUT);
    carry(p, e, "pref_color_bubble_border", SkRole.BUBBLE_BORDER_IN, SkRole.BUBBLE_BORDER_OUT);
    // The old keys are spent: left in place they would be exported again and migrated twice.
    e.remove("pref_accent");
    e.remove("pref_color_message_text");
    e.remove("pref_color_bubble_fill");
    e.remove("pref_color_bubble_border");
    // commit(), not apply(): an import must be on disk before it answers (see ShiroikumaExport).
    e.putBoolean(MIGRATED, true).commit();
    clearCache();
  }

  /** After an import: whatever older keys it brought are carried over as well. */
  public static void remigrate(@NonNull Context context) {
    prefs(context).edit().remove(MIGRATED).commit();
    migrate(context);
    SkPalette.invalidate(context);
  }

  private static void carry(
      SharedPreferences p, SharedPreferences.Editor e, String old, SkRole in, SkRole out) {
    if (!p.contains(old)) return;
    try {
      int v = p.getInt(old, 0);
      if (!p.contains(in.storageKey)) e.putInt(in.storageKey, v);
      if (!p.contains(out.storageKey)) e.putInt(out.storageKey, v);
    } catch (ClassCastException ignore) {
    }
  }

  // ---- colour arithmetic

  @ColorInt
  public static int blend(@ColorInt int from, @ColorInt int to, float t) {
    float s = 1f - t;
    return Color.argb(
        255,
        Math.round(Color.red(from) * s + Color.red(to) * t),
        Math.round(Color.green(from) * s + Color.green(to) * t),
        Math.round(Color.blue(from) * s + Color.blue(to) * t));
  }

  @ColorInt
  public static int alpha(@ColorInt int color, float fraction) {
    return (color & 0x00FFFFFF) | (Math.round(255 * fraction) << 24);
  }

  /** A disabled frame or label: the colour at 38 percent. */
  @ColorInt
  public static int disabled(@ColorInt int color) {
    return alpha(color, Color.alpha(color) / 255f * 0.38f);
  }

  /** Black or white, whichever reads on {@code ground}. */
  @ColorInt
  public static int inkOn(@NonNull Context context, @ColorInt int ground) {
    double lum =
        (0.299 * Color.red(ground) + 0.587 * Color.green(ground) + 0.114 * Color.blue(ground))
            / 255.0;
    return lum > 0.6 ? get(context, SkRole.BACKGROUND) : get(context, SkRole.TEXT);
  }
}
