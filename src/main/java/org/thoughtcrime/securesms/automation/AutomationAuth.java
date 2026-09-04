package org.thoughtcrime.securesms.automation;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * shiroikuma fork — the gate for the 保存復元 automation contract: a master switch (default
 * <b>ON</b>), an opt-in 「Use authorization token?」 switch (default <b>OFF</b>), and the token
 * itself.
 *
 * <h3>Why the token became optional (contract v2, 2026-09-04)</h3>
 *
 * <p>v1 shipped every sister app closed: the switch defaulted to false and a caller also had to
 * present a 48-character secret 白い熊 had pasted from this app's settings into the caller's. That
 * is wrong for where the family is going — <b>a pasted secret cannot survive a wipe</b>, and the
 * case the contract now exists to serve is 白い熊 応用管理 restoring apps <i>and their data</i> onto a
 * clean phone, where nothing has been configured and nobody has pasted anything. A gate that only
 * works once the phone is already set up is no gate for setting the phone up.
 *
 * <p>The switch stays (it is the only way to close this app off, and a feature that can be turned
 * on but never off is one 白い熊 cannot retreat from); the token becomes an extra a caller <i>may</i>
 * be asked for.
 *
 * <h3>A token sent to an app that does not want one is IGNORED, never refused</h3>
 *
 * <p>Required, not a nicety. Tokens live in task arguments and workspace variables that outlive the
 * setting they were pasted for, so a caller still sending one — because it was configured last
 * year, or because another app on the batch does want one — must be served. Refusing it would turn
 * "白い熊 turned a switch off" into "half the batch mysteriously fails", which is precisely the
 * friction the switch exists to remove. That is why {@link #refuse} only looks at the candidate
 * when {@link #isTokenRequired} says so.
 *
 * <h3>One function, not two checks per entry point</h3>
 *
 * <p>{@link #refuse} is the single gate every surface calls. Two checks written out at each entry
 * point is how "disabled" and "bad token" drift apart across forty-two apps.
 *
 * <p>All three values live in their own device-local prefs file, <b>not</b> in the default
 * SharedPreferences the export engine dumps — so the token can never travel inside a backup zip
 * (see {@code ShiroikumaExport.APP_EXCLUDE}'s note). The token is generated lazily on first read,
 * so the settings row always shows a value, and compared in constant time.
 */
public final class AutomationAuth {

  /** Device-local prefs; deliberately outside the export map. */
  public static final String PREFS = "shiroikuma_automation";

  private static final String KEY_ENABLED = "automation_enabled";
  private static final String KEY_REQUIRE_TOKEN = "automation_require_token";
  private static final String KEY_TOKEN = "automation_token";

  /** Contract v2: the door answers out of the box, so a wiped phone needs nothing configured. */
  private static final boolean DEFAULT_ENABLED = true;

  /** Contract v2: the token is an extra a caller may be asked for, not the gate. */
  private static final boolean DEFAULT_REQUIRE_TOKEN = false;

  private static final int TOKEN_BYTES = 24;

  private AutomationAuth() {}

  private static SharedPreferences prefs(@NonNull Context context) {
    return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  // --- the one gate ---------------------------------------------------------------------------

  /**
   * The single authorization check for every automation surface.
   *
   * @return {@code null} when the caller may proceed, otherwise the exact {@code ERROR:} line to
   *     answer with. "Automation disabled" and "bad token" stay distinct because they debug
   *     differently.
   */
  @Nullable
  public static String refuse(@NonNull Context context, @Nullable String candidate) {
    if (!isEnabled(context)) return "ERROR:automation disabled";
    // A token we are not asking for is ignored - never an error. See the class comment.
    if (isTokenRequired(context) && !matches(context, candidate)) return "ERROR:bad token";
    return null;
  }

  // --- the two switches -----------------------------------------------------------------------

  public static boolean isEnabled(@NonNull Context context) {
    return prefs(context).getBoolean(KEY_ENABLED, DEFAULT_ENABLED);
  }

  public static void setEnabled(@NonNull Context context, boolean enabled) {
    prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
  }

  /** 「Use authorization token?」 — off by default, so any sister app may drive the automation. */
  public static boolean isTokenRequired(@NonNull Context context) {
    return prefs(context).getBoolean(KEY_REQUIRE_TOKEN, DEFAULT_REQUIRE_TOKEN);
  }

  public static void setTokenRequired(@NonNull Context context, boolean required) {
    prefs(context).edit().putBoolean(KEY_REQUIRE_TOKEN, required).apply();
  }

  // --- the token ------------------------------------------------------------------------------

  /** The stored token, generated on first read so the settings row is never empty. */
  @NonNull
  public static synchronized String getToken(@NonNull Context context) {
    SharedPreferences prefs = prefs(context);
    String token = prefs.getString(KEY_TOKEN, null);
    if (token == null || token.isEmpty()) {
      token = generate();
      prefs.edit().putString(KEY_TOKEN, token).apply();
    }
    return token;
  }

  /** Replaces the token; every pasted copy stops working immediately. */
  @NonNull
  public static synchronized String regenerateToken(@NonNull Context context) {
    String token = generate();
    prefs(context).edit().putString(KEY_TOKEN, token).apply();
    return token;
  }

  /**
   * Constant-time comparison against the stored token. Kept for the case where the token <i>is</i>
   * required; {@link #refuse} is what decides whether that case applies.
   */
  public static boolean matches(@NonNull Context context, @Nullable String candidate) {
    if (candidate == null || candidate.isEmpty()) return false;
    String stored = getToken(context);
    return MessageDigest.isEqual(candidate.getBytes(), stored.getBytes());
  }

  /** {@code 80922d8c…4c49a87c} — what the settings row shows; the tap copies the whole thing. */
  @NonNull
  public static String abbreviate(@NonNull String token) {
    if (token.length() <= 20) return token;
    return token.substring(0, 8) + "…" + token.substring(token.length() - 8);
  }

  private static String generate() {
    byte[] raw = new byte[TOKEN_BYTES];
    new SecureRandom().nextBytes(raw);
    StringBuilder sb = new StringBuilder(raw.length * 2);
    for (byte b : raw) {
      sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
    }
    return sb.toString();
  }
}
