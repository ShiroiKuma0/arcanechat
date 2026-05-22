package org.thoughtcrime.securesms.util;

import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.b44t.messenger.DcContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.notifications.UnifiedPushUtils;
import org.thoughtcrime.securesms.preferences.widgets.NotificationPrivacyPreference;

public class Prefs {

  private static final String TAG = "Prefs";

  public static final String RELIABLE_SERVICE_PREF = "pref_reliable_service";
  public static final String DISABLE_PASSPHRASE_PREF = "pref_disable_passphrase";
  public static final String THEME_PREF = "pref_theme";
  public static final String LANGUAGE_PREF = "pref_language";
  public static final String BACKGROUND_PREF = "pref_chat_background";

  // shiroikuma fork (Step 2): per-surface configurable colours. Each role is an ARGB int stored
  // in default SharedPreferences; defaults reproduce the Step 1 yellow-on-black palette. Applied
  // per-surface in code (a runtime Resources colour override does NOT reach theme/attr resolution).
  public static final String COLOR_MESSAGE_TEXT_PREF = "pref_color_message_text";
  public static final String COLOR_BUBBLE_FILL_PREF = "pref_color_bubble_fill";
  public static final String COLOR_BUBBLE_BORDER_PREF = "pref_color_bubble_border";
  // shiroikuma fork (Step 4): incoming/outgoing splits of the three message-bubble roles. Each
  // defaults to the matching legacy single-value pref above, so colours already chosen carry over
  // to both directions until the user differentiates them.
  public static final String COLOR_MSG_TEXT_IN_PREF = "pref_color_message_text_in";
  public static final String COLOR_MSG_TEXT_OUT_PREF = "pref_color_message_text_out";
  public static final String COLOR_BUBBLE_FILL_IN_PREF = "pref_color_bubble_fill_in";
  public static final String COLOR_BUBBLE_FILL_OUT_PREF = "pref_color_bubble_fill_out";
  public static final String COLOR_BUBBLE_BORDER_IN_PREF = "pref_color_bubble_border_in";
  public static final String COLOR_BUBBLE_BORDER_OUT_PREF = "pref_color_bubble_border_out";
  public static final String COLOR_CONVERSATION_BG_PREF = "pref_color_conversation_bg";
  public static final String COLOR_LIST_TITLE_PREF = "pref_color_list_title";
  public static final String COLOR_LIST_PREVIEW_PREF = "pref_color_list_preview";
  public static final String COLOR_FAB_PREF = "pref_color_fab";
  public static final String ACCENT_PREF = "pref_accent";
  public static final int COLOR_YELLOW = 0xFFFFFF00;
  public static final int COLOR_BLACK = 0xFF000000;

  private static final String DATABASE_ENCRYPTED_SECRET =
      "pref_database_encrypted_secret_"; // followed by account-id
  private static final String DATABASE_UNENCRYPTED_SECRET =
      "pref_database_unencrypted_secret_"; // followed by account-id

  public static final String RINGTONE_PREF = "pref_key_ringtone";
  private static final String VIBRATE_PREF = "pref_key_vibrate";
  private static final String CHAT_VIBRATE = "pref_chat_vibrate_"; // followed by chat-id
  public static final String LED_COLOR_PREF = "pref_led_color";
  private static final String CHAT_RINGTONE = "pref_chat_ringtone_"; // followed by chat-id
  public static final String SCREEN_SECURITY_PREF = "pref_screen_security";
  private static final String ENTER_SENDS_PREF = "pref_enter_sends";
  private static final String PROMPTED_DOZE_MSG_ID_PREF = "pref_prompted_doze_msg_id";
  private static final String STATS_DEVICE_MSG_ID_PREF = "pref_stats_device_msg_id";
  public static final String DOZE_ASKED_DIRECTLY = "pref_doze_asked_directly";
  public static final String ASKED_FOR_NOTIFICATION_PERMISSION =
      "pref_asked_for_notification_permission";
  private static final String IN_THREAD_NOTIFICATION_PREF = "pref_key_inthread_notifications";

  public static final String NOTIFICATION_PRIVACY_PREF = "pref_notification_privacy";
  public static final String NOTIFICATION_PRIORITY_PREF = "pref_notification_priority";

  public static final String DISABLE_UNIFIEDPUSH = "pref_disable_unifiedpush";

  private static final String PROFILE_AVATAR_ID_PREF = "pref_profile_avatar_id";
  public static final String INCOGNITO_KEYBORAD_PREF = "pref_incognito_keyboard";

  private static final String PREF_CONTACT_PHOTO_IDENTIFIERS = "pref_contact_photo_identifiers";

  public static final String ALWAYS_LOAD_REMOTE_CONTENT = "pref_always_load_remote_content";
  public static final boolean ALWAYS_LOAD_REMOTE_CONTENT_DEFAULT = false;

  public static final String LAST_DEVICE_MSG_LABEL = "pref_last_device_msg_id";
  public static final String WEBXDC_STORE_URL_PREF = "pref_webxdc_store_url";
  public static final String DEFAULT_WEBXDC_STORE_URL = "https://webxdc.org/apps/";

  public enum VibrateState {
    DEFAULT(0),
    ENABLED(1),
    DISABLED(2);
    private final int id;

    VibrateState(int id) {
      this.id = id;
    }

    public int getId() {
      return id;
    }

    public static VibrateState fromId(int id) {
      return values()[id];
    }
  }

  public static void setDatabaseEncryptedSecret(
      @NonNull Context context, @NonNull String secret, int accountId) {
    setStringPreference(context, DATABASE_ENCRYPTED_SECRET + accountId, secret);
  }

  public static void setDatabaseUnencryptedSecret(
      @NonNull Context context, @Nullable String secret, int accountId) {
    setStringPreference(context, DATABASE_UNENCRYPTED_SECRET + accountId, secret);
  }

  public static @Nullable String getDatabaseUnencryptedSecret(
      @NonNull Context context, int accountId) {
    return getStringPreference(context, DATABASE_UNENCRYPTED_SECRET + accountId, null);
  }

  public static @Nullable String getDatabaseEncryptedSecret(
      @NonNull Context context, int accountId) {
    return getStringPreference(context, DATABASE_ENCRYPTED_SECRET + accountId, null);
  }

  public static boolean isIncognitoKeyboardEnabled(Context context) {
    return getBooleanPreference(context, INCOGNITO_KEYBORAD_PREF, false);
  }

  public static void setProfileAvatarId(Context context, int id) {
    setIntegerPreference(context, PROFILE_AVATAR_ID_PREF, id);
  }

  public static int getProfileAvatarId(Context context) {
    return getIntegerPreference(context, PROFILE_AVATAR_ID_PREF, 0);
  }

  // shiroikuma fork (Step 2): configurable colour roles.
  public static int getMessageTextColor(Context context) {
    return getIntegerPreference(context, COLOR_MESSAGE_TEXT_PREF, COLOR_YELLOW);
  }

  public static void setMessageTextColor(Context context, int color) {
    setIntegerPreference(context, COLOR_MESSAGE_TEXT_PREF, color);
  }

  // shiroikuma fork (Step 4b): accent preset key (drives colorAccent/control attrs + popup colours
  // via a theme overlay applied in DynamicTheme). Default "yellow" = the Step 1 look.
  public static String getAccent(Context context) {
    return getStringPreference(context, ACCENT_PREF, "yellow");
  }

  public static void setAccent(Context context, String value) {
    setStringPreference(context, ACCENT_PREF, value);
  }

  // Direction-aware overloads (Step 4). Default to the legacy shared value above.
  public static int getMessageTextColor(Context context, boolean outgoing) {
    return getIntegerPreference(
        context,
        outgoing ? COLOR_MSG_TEXT_OUT_PREF : COLOR_MSG_TEXT_IN_PREF,
        getMessageTextColor(context));
  }

  public static void setMessageTextColor(Context context, boolean outgoing, int color) {
    setIntegerPreference(context, outgoing ? COLOR_MSG_TEXT_OUT_PREF : COLOR_MSG_TEXT_IN_PREF, color);
  }

  public static int getBubbleFillColor(Context context, boolean outgoing) {
    return getIntegerPreference(
        context,
        outgoing ? COLOR_BUBBLE_FILL_OUT_PREF : COLOR_BUBBLE_FILL_IN_PREF,
        getBubbleFillColor(context));
  }

  public static void setBubbleFillColor(Context context, boolean outgoing, int color) {
    setIntegerPreference(
        context, outgoing ? COLOR_BUBBLE_FILL_OUT_PREF : COLOR_BUBBLE_FILL_IN_PREF, color);
  }

  public static int getBubbleBorderColor(Context context, boolean outgoing) {
    return getIntegerPreference(
        context,
        outgoing ? COLOR_BUBBLE_BORDER_OUT_PREF : COLOR_BUBBLE_BORDER_IN_PREF,
        getBubbleBorderColor(context));
  }

  public static void setBubbleBorderColor(Context context, boolean outgoing, int color) {
    setIntegerPreference(
        context, outgoing ? COLOR_BUBBLE_BORDER_OUT_PREF : COLOR_BUBBLE_BORDER_IN_PREF, color);
  }

  public static int getBubbleFillColor(Context context) {
    return getIntegerPreference(context, COLOR_BUBBLE_FILL_PREF, COLOR_BLACK);
  }

  public static void setBubbleFillColor(Context context, int color) {
    setIntegerPreference(context, COLOR_BUBBLE_FILL_PREF, color);
  }

  public static int getBubbleBorderColor(Context context) {
    return getIntegerPreference(context, COLOR_BUBBLE_BORDER_PREF, COLOR_YELLOW);
  }

  public static void setBubbleBorderColor(Context context, int color) {
    setIntegerPreference(context, COLOR_BUBBLE_BORDER_PREF, color);
  }

  public static int getConversationBackgroundColor(Context context) {
    return getIntegerPreference(context, COLOR_CONVERSATION_BG_PREF, COLOR_BLACK);
  }

  public static void setConversationBackgroundColor(Context context, int color) {
    setIntegerPreference(context, COLOR_CONVERSATION_BG_PREF, color);
  }

  public static int getListTitleColor(Context context) {
    return getIntegerPreference(context, COLOR_LIST_TITLE_PREF, COLOR_YELLOW);
  }

  public static void setListTitleColor(Context context, int color) {
    setIntegerPreference(context, COLOR_LIST_TITLE_PREF, color);
  }

  public static int getListPreviewColor(Context context) {
    return getIntegerPreference(context, COLOR_LIST_PREVIEW_PREF, COLOR_YELLOW);
  }

  public static void setListPreviewColor(Context context, int color) {
    setIntegerPreference(context, COLOR_LIST_PREVIEW_PREF, color);
  }

  public static int getFabColor(Context context) {
    return getIntegerPreference(context, COLOR_FAB_PREF, COLOR_YELLOW);
  }

  public static void setFabColor(Context context, int color) {
    setIntegerPreference(context, COLOR_FAB_PREF, color);
  }

  // shiroikuma fork (Step 3): per-category configurable fonts. Each category stores three values
  // under "pref_font_<category>_{family,weight,size}". Defaults ("", 0, 0) mean "leave as-is".
  public static final String FONT_CHAT_TEXT = "chat_text";
  public static final String FONT_CONV_TITLE = "conv_title";
  public static final String FONT_LIST_TITLE = "list_title";
  public static final String FONT_LIST_PREVIEW = "list_preview";
  public static final String FONT_SETTINGS = "settings";

  /** Newline-separated absolute paths of user-picked custom font files. */
  public static String[] getFontFiles(Context context) {
    String raw = getStringPreference(context, "pref_font_files", "");
    if (raw.isEmpty()) return new String[0];
    java.util.List<String> out = new java.util.ArrayList<>();
    for (String s : raw.split("\n")) if (!s.trim().isEmpty()) out.add(s.trim());
    return out.toArray(new String[0]);
  }

  public static void addFontFile(Context context, String path) {
    if (path == null || path.isEmpty()) return;
    for (String existing : getFontFiles(context)) if (existing.equals(path)) return; // dedupe
    String raw = getStringPreference(context, "pref_font_files", "");
    setStringPreference(context, "pref_font_files", raw.isEmpty() ? path : raw + "\n" + path);
  }

  public static String getFontFamily(Context context, String category) {
    return getStringPreference(context, "pref_font_" + category + "_family", "");
  }

  public static int getFontWeight(Context context, String category) {
    return getIntegerPreference(context, "pref_font_" + category + "_weight", 0);
  }

  public static int getFontSize(Context context, String category) {
    return getIntegerPreference(context, "pref_font_" + category + "_size", 0);
  }

  public static void setFont(Context context, String category, String family, int weight, int size) {
    setStringPreference(context, "pref_font_" + category + "_family", family);
    setIntegerPreference(context, "pref_font_" + category + "_weight", weight);
    setIntegerPreference(context, "pref_font_" + category + "_size", size);
  }

  public static int getNotificationPriority(Context context) {
    return Integer.valueOf(
        getStringPreference(
            context, NOTIFICATION_PRIORITY_PREF, String.valueOf(NotificationCompat.PRIORITY_HIGH)));
  }

  public static NotificationPrivacyPreference getNotificationPrivacy(Context context) {
    return new NotificationPrivacyPreference(
        getStringPreference(context, NOTIFICATION_PRIVACY_PREF, "all"));
  }

  public static boolean isInChatNotifications(Context context) {
    return getBooleanPreference(context, IN_THREAD_NOTIFICATION_PREF, true);
  }

  public static void setEnterSendsEnabled(Context context, boolean value) {
    setBooleanPreference(context, ENTER_SENDS_PREF, value);
  }

  public static boolean isEnterSendsEnabled(Context context) {
    return getBooleanPreference(context, ENTER_SENDS_PREF, false);
  }

  public static boolean isPasswordDisabled(Context context) {
    return getBooleanPreference(context, DISABLE_PASSPHRASE_PREF, false);
  }

  public static void setScreenSecurityEnabled(Context context, boolean value) {
    setBooleanPreference(context, SCREEN_SECURITY_PREF, value);
  }

  public static boolean isScreenSecurityEnabled(Context context) {
    return getBooleanPreference(context, SCREEN_SECURITY_PREF, false);
  }

  public static String getTheme(Context context) {
    return getStringPreference(
        context,
        THEME_PREF,
        DynamicTheme.systemThemeAvailable() ? DynamicTheme.SYSTEM : DynamicTheme.LIGHT);
  }

  public static String getWebxdcStoreUrl(Context context) {
    return getStringPreference(context, WEBXDC_STORE_URL_PREF, DEFAULT_WEBXDC_STORE_URL);
  }

  public static void setWebxdcStoreUrl(Context context, String url) {
    if (url == null || url.trim().isEmpty() || DEFAULT_WEBXDC_STORE_URL.equals(url)) url = null;
    setStringPreference(context, WEBXDC_STORE_URL_PREF, url);
  }

  public static void setPromptedDozeMsgId(Context context, int msg_id) {
    setIntegerPreference(context, PROMPTED_DOZE_MSG_ID_PREF, msg_id);
  }

  public static int getPrompteDozeMsgId(Context context) {
    return getIntegerPreference(context, PROMPTED_DOZE_MSG_ID_PREF, 0);
  }

  public static void setStatsDeviceMsgId(Context context, int msg_id) {
    setIntegerPreference(context, STATS_DEVICE_MSG_ID_PREF, msg_id);
  }

  public static int getStatsDeviceMsgId(Context context) {
    return getIntegerPreference(context, STATS_DEVICE_MSG_ID_PREF, 0);
  }

  public static boolean isFcmPushEnabled(Context context) {
    return BuildConfig.USE_PLAY_SERVICES;
  }

  public static boolean isHardCompressionEnabled(Context context) {
    return DcHelper.getContext(context).getConfigInt(DcHelper.CONFIG_MEDIA_QUALITY)
        == DcContext.DC_MEDIA_QUALITY_WORSE;
  }

  public static boolean isLocationStreamingEnabled(Context context) {
    return true;
  }

  // ringtone

  public static @NonNull Uri getNotificationRingtone(Context context) {
    String result =
        getStringPreference(
            context, RINGTONE_PREF, Settings.System.DEFAULT_NOTIFICATION_URI.toString());

    if (result != null && result.startsWith("file:")) {
      result = Settings.System.DEFAULT_NOTIFICATION_URI.toString();
    }

    return Uri.parse(result);
  }

  public static void removeNotificationRingtone(Context context) {
    removePreference(context, RINGTONE_PREF);
  }

  public static void setNotificationRingtone(Context context, Uri ringtone) {
    setStringPreference(context, RINGTONE_PREF, ringtone.toString());
  }

  public static void setChatRingtone(Context context, int accountId, int chatId, Uri ringtone) {
    final String KEY =
        (accountId != 0 && chatId != 0) ? CHAT_RINGTONE + accountId + "." + chatId : CHAT_RINGTONE;
    if (ringtone != null) {
      setStringPreference(context, KEY, ringtone.toString());
    } else {
      removePreference(context, KEY);
    }
  }

  public static @Nullable Uri getChatRingtone(Context context, int accountId, int chatId) {
    final String KEY =
        (accountId != 0 && chatId != 0) ? CHAT_RINGTONE + accountId + "." + chatId : CHAT_RINGTONE;
    String result = getStringPreference(context, KEY, null);
    return result == null ? null : Uri.parse(result);
  }

  public static void resetReliableService(Context context) {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    prefs.edit().remove(RELIABLE_SERVICE_PREF).apply();
  }

  public static void setReliableService(Context context, boolean value) {
    setBooleanPreference(context, RELIABLE_SERVICE_PREF, value);
  }

  public static boolean reliableService(Context context) {
    boolean isPushEnabled =
        isFcmPushEnabled(context) || UnifiedPushUtils.hasPushDistributor(context, true);
    return getBooleanPreference(context, RELIABLE_SERVICE_PREF, !isPushEnabled);
  }

  /**
   * Allow UnifiedPush to be used if a distributor is available
   *
   * <p>UnifiedPush is never used if the flavor uses the Play Services
   *
   * <p>We use 2 functions enableUnifiedPush/disableUnifiedPush to make things more clear
   *
   * @param context
   */
  public static void enableUnifiedPush(Context context) {
    setBooleanPreference(context, DISABLE_UNIFIEDPUSH, false);
  }

  /**
   * Allow UnifiedPush to be used if a distributor is available
   *
   * <p>We use 2 functions enableUnifiedPush/disableUnifiedPush to make things more clear
   *
   * @param context
   */
  public static void disableUnifiedPush(Context context) {
    setBooleanPreference(context, DISABLE_UNIFIEDPUSH, true);
  }

  public static boolean unifiedPushDisabled(Context context) {
    // By default, allow UnifiedPush.
    // This is never used if the flavor supports Play Services.
    return getBooleanPreference(context, DISABLE_UNIFIEDPUSH, false);
  }

  // vibrate

  public static boolean isNotificationVibrateEnabled(Context context) {
    return getBooleanPreference(context, VIBRATE_PREF, true);
  }

  public static void setChatVibrate(
      Context context, int accountId, int chatId, VibrateState vibrateState) {
    final String KEY =
        (accountId != 0 && chatId != 0) ? CHAT_VIBRATE + accountId + "." + chatId : CHAT_VIBRATE;
    if (vibrateState != VibrateState.DEFAULT) {
      setIntegerPreference(context, KEY, vibrateState.getId());
    } else {
      removePreference(context, KEY);
    }
  }

  public static VibrateState getChatVibrate(Context context, int accountId, int chatId) {
    final String KEY =
        (accountId != 0 && chatId != 0) ? CHAT_VIBRATE + accountId + "." + chatId : CHAT_VIBRATE;
    return VibrateState.fromId(getIntegerPreference(context, KEY, VibrateState.DEFAULT.getId()));
  }

  // led

  public static String getNotificationLedColor(Context context) {
    return getStringPreference(context, LED_COLOR_PREF, "blue");
  }

  // misc.

  public static String getBackgroundImagePath(Context context, int accountId) {
    return getStringPreference(context, BACKGROUND_PREF + accountId, "");
  }

  public static void setBackgroundImagePath(Context context, int accountId, String path) {
    setStringPreference(context, BACKGROUND_PREF + accountId, path);
  }

  public static boolean getAlwaysLoadRemoteContent(Context context) {
    return getBooleanPreference(
        context, Prefs.ALWAYS_LOAD_REMOTE_CONTENT, Prefs.ALWAYS_LOAD_REMOTE_CONTENT_DEFAULT);
  }

  // generic preference functions

  public static void setBooleanPreference(Context context, String key, boolean value) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(key, value).apply();
  }

  public static boolean getBooleanPreference(Context context, String key, boolean defaultValue) {
    return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(key, defaultValue);
  }

  public static void setStringPreference(Context context, String key, String value) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putString(key, value).apply();
  }

  public static String getStringPreference(Context context, String key, String defaultValue) {
    return PreferenceManager.getDefaultSharedPreferences(context).getString(key, defaultValue);
  }

  private static int getIntegerPreference(Context context, String key, int defaultValue) {
    return PreferenceManager.getDefaultSharedPreferences(context).getInt(key, defaultValue);
  }

  private static void setIntegerPreference(Context context, String key, int value) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(key, value).apply();
  }

  public static long getLongPreference(Context context, String key, long defaultValue) {
    return PreferenceManager.getDefaultSharedPreferences(context).getLong(key, defaultValue);
  }

  private static void setLongPreference(Context context, String key, long value) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putLong(key, value).apply();
  }

  public static void removePreference(Context context, String key) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().remove(key).apply();
  }

  private static Set<String> getStringSetPreference(
      Context context, String key, Set<String> defaultValues) {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    if (prefs.contains(key)) {
      return prefs.getStringSet(key, Collections.<String>emptySet());
    } else {
      return defaultValues;
    }
  }

  public static void setSystemContactPhotos(Context context, Set<String> contactPhotoIdentifiers) {
    PreferenceManager.getDefaultSharedPreferences(context)
        .edit()
        .putStringSet(PREF_CONTACT_PHOTO_IDENTIFIERS, contactPhotoIdentifiers)
        .apply();
  }

  public static Uri getSystemContactPhoto(Context context, String identifier) {
    List<String> contactPhotoIdentifiers =
        new ArrayList<>(
            getStringSetPreference(context, PREF_CONTACT_PHOTO_IDENTIFIERS, new HashSet<>()));
    for (String contactPhotoIdentifier : contactPhotoIdentifiers) {
      if (contactPhotoIdentifier.contains(identifier)) {
        String[] parts = contactPhotoIdentifier.split("\\|");
        long contactId = Long.valueOf(parts[1]);
        return ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId);
      }
    }
    return null;
  }
}
