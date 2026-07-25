package org.thoughtcrime.securesms.automation;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * shiroikuma fork — the gate for the 保存復元 automation contract: a master switch (default OFF)
 * plus a shared secret the companion app (白い熊 自由作業盤) puts in its token-gated intents.
 *
 * <p>Both live in their own device-local prefs file, <b>not</b> in the default SharedPreferences the
 * export engine dumps — so the token can never travel inside a backup zip (see {@code
 * ShiroikumaExport.APP_EXCLUDE}'s note). The token is generated lazily on first read, so the
 * settings row always shows a value, and compared in constant time.
 */
public final class AutomationAuth {

  /** Device-local prefs; deliberately outside the export map. */
  public static final String PREFS = "shiroikuma_automation";

  private static final String KEY_ENABLED = "automation_enabled";
  private static final String KEY_TOKEN = "automation_token";

  private static final int TOKEN_BYTES = 24;

  private AutomationAuth() {}

  private static SharedPreferences prefs(@NonNull Context context) {
    return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  public static boolean isEnabled(@NonNull Context context) {
    return prefs(context).getBoolean(KEY_ENABLED, false);
  }

  public static void setEnabled(@NonNull Context context, boolean enabled) {
    prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
  }

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

  /** Constant-time comparison against the stored token. */
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
