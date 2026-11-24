package org.thoughtcrime.securesms.shiroikuma;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * shiroikuma fork: the arrangement of the UI page - the order of its sections and which of them
 * are open. Two small lists in the default preferences, so they travel in the UI export with the
 * colours and fonts. Every section is folded on a fresh install.
 */
public final class SkPageState {
  private SkPageState() {}

  public static final String KEY_ORDER = "sk_ui_section_order";
  public static final String KEY_OPEN = "sk_ui_sections_open";

  private static SharedPreferences sp(@NonNull Context c) {
    return PreferenceManager.getDefaultSharedPreferences(c.getApplicationContext());
  }

  /** The stored order, with sections it does not know yet appended in their default order. */
  @NonNull
  public static List<String> order(@NonNull Context c, @NonNull List<String> defaults) {
    List<String> out = new ArrayList<>();
    String stored = sp(c).getString(KEY_ORDER, "");
    if (!TextUtils.isEmpty(stored)) {
      for (String id : stored.split(",")) {
        if (defaults.contains(id) && !out.contains(id)) out.add(id);
      }
    }
    for (String id : defaults) if (!out.contains(id)) out.add(id);
    return out;
  }

  public static void setOrder(@NonNull Context c, @NonNull List<String> order) {
    sp(c).edit().putString(KEY_ORDER, TextUtils.join(",", order)).apply();
  }

  public static void resetOrder(@NonNull Context c) {
    sp(c).edit().remove(KEY_ORDER).apply();
  }

  @NonNull
  private static Set<String> open(@NonNull Context c) {
    String stored = sp(c).getString(KEY_OPEN, "");
    return TextUtils.isEmpty(stored)
        ? new HashSet<String>()
        : new HashSet<>(Arrays.asList(stored.split(",")));
  }

  public static boolean isOpen(@NonNull Context c, @NonNull String id) {
    return open(c).contains(id);
  }

  public static void setOpen(@NonNull Context c, @NonNull String id, boolean value) {
    Set<String> set = open(c);
    if (value) set.add(id);
    else set.remove(id);
    sp(c).edit().putString(KEY_OPEN, TextUtils.join(",", set)).apply();
  }
}
