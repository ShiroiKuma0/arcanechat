package org.thoughtcrime.securesms.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AppCompatDelegate;
import org.thoughtcrime.securesms.R;

public class DynamicTheme {

  public static final String DARK = "dark";
  public static final String LIGHT = "light";
  public static final String SYSTEM = "system";
  public static final String PURPLE = "purple";
  public static final String GREEN = "green";
  public static final String BLUE = "blue";
  public static final String RED = "red";
  public static final String PINK = "pink";
  public static final String GRAY = "gray";

  private int currentTheme;
  private int currentAccent;

  public void onCreate(Activity activity) {
    // shiroikuma fork: the black-yellow theme applies always, whatever the theme picker says -
    // the dark theme as the base, the colour roles laid over it. The palette goes onto the
    // activity's resources before anything is resolved from them (below Android 10 this is the
    // only hook that runs early enough).
    org.thoughtcrime.securesms.shiroikuma.SkPalette.apply(activity);
    currentTheme = getSelectedTheme(activity);
    activity.setTheme(currentTheme);
    currentAccent = getAccentOverlay(activity);
    activity.getTheme().applyStyle(currentAccent, true);
  }

  public void onResume(Activity activity) {
    if (currentTheme != getSelectedTheme(activity)) {
      Intent intent = activity.getIntent();
      activity.finish();
      OverridePendingTransition.invoke(activity);
      activity.startActivity(intent);
      OverridePendingTransition.invoke(activity);
    }
  }

  // shiroikuma fork: the overlay that points every colour attribute at a colour role. (It was a
  // set of eight accent presets until the roles replaced them; the name is kept for its callers.)
  public static @StyleRes int getAccentOverlay(Context context) {
    return R.style.ThemeOverlay_Sk;
  }

  public static void setDefaultDayNightMode(@NonNull Context context) {
    // shiroikuma fork: always dark - the black-yellow theme has no light form.
    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
  }

  private @StyleRes int getSelectedTheme(Activity activity) {
    return getDarkThemeStyle(DARK);
  }

  protected @StyleRes int getLightThemeStyle(@NonNull String theme) {
    if (theme.equals(PURPLE)) return R.style.TextSecure_PurpleTheme;
    if (theme.equals(GREEN)) return R.style.TextSecure_GreenTheme;
    if (theme.equals(BLUE)) return R.style.TextSecure_BlueTheme;
    if (theme.equals(RED)) return R.style.TextSecure_RedTheme;
    if (theme.equals(PINK)) return R.style.TextSecure_PinkTheme;
    if (theme.equals(GRAY)) return R.style.TextSecure_GrayTheme;
    return R.style.TextSecure_LightTheme;
  }

  protected @StyleRes int getDarkThemeStyle(@NonNull String theme) {
    if (theme.equals(PURPLE)) return R.style.TextSecure_PurpleDarkTheme;
    if (theme.equals(GREEN)) return R.style.TextSecure_GreenDarkTheme;
    if (theme.equals(BLUE)) return R.style.TextSecure_BlueDarkTheme;
    if (theme.equals(RED)) return R.style.TextSecure_RedDarkTheme;
    if (theme.equals(PINK)) return R.style.TextSecure_PinkDarkTheme;
    if (theme.equals(GRAY)) return R.style.TextSecure_GrayDarkTheme;
    return R.style.TextSecure_DarkTheme;
  }

  static boolean systemThemeAvailable() {
    return Build.VERSION.SDK_INT >= 29;
  }

  /** shiroikuma fork: always dark. */
  public static boolean isDarkTheme(@NonNull Context context) {
    return true;
  }

  // return a checkmark emoji that fits to the background of the selected theme.
  public static String getCheckmarkEmoji(@NonNull Context context) {
    return isDarkTheme(context) ? "✅" /*blue, white or white in a box*/ : "✔️" /*blue or black*/;
  }

  private static boolean isSystemInDarkTheme(@NonNull Context context) {
    return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
        == Configuration.UI_MODE_NIGHT_YES;
  }

  private static final class OverridePendingTransition {
    static void invoke(Activity activity) {
      activity.overridePendingTransition(0, 0);
    }
  }
}
