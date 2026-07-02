package org.thoughtcrime.securesms.automation;

import android.content.Context;
import androidx.annotation.Nullable;
import com.b44t.messenger.DcContact;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.thoughtcrime.securesms.util.Prefs;

/**
 * shiroikuma fork — "protected contacts".
 *
 * <p>A local, same-device control channel (see {@link ProtectedContactsReceiver}) lets the
 * companion app mark certain contacts as <b>protected</b>. When a protected contact sends a
 * message, the notification pipeline posts a content-free ("vague") notification — no sender name,
 * no message text — while still posting it, so the companion's edge-blink alert keeps firing.
 *
 * <p>This class owns the persisted state (the protected set + the optional vague title/body) and
 * the case-insensitive, best-effort, non-blocking sender matching. Everything is stored as plain
 * string preferences via {@link Prefs} so it survives process death / reboot.
 */
public final class ProtectedContacts {

  private ProtectedContacts() {}

  /** Pipe-joined, trimmed, lowercased identifiers of protected contacts. */
  private static final String PREF_SET = "pref_protected_contacts";
  /** Optional custom title for the vague notification ("" = use the default). */
  private static final String PREF_TITLE = "pref_protected_title";
  /** Optional custom body for the vague notification ("" = use the default). */
  private static final String PREF_BODY = "pref_protected_body";

  public static final String MODE_REPLACE = "replace";
  public static final String MODE_ADD = "add";
  public static final String MODE_REMOVE = "remove";

  // --- persistence -----------------------------------------------------------

  /** @return the stored protected identifiers (trimmed + lowercased); never null. */
  public static synchronized Set<String> getSet(Context context) {
    return parse(Prefs.getStringPreference(context, PREF_SET, ""));
  }

  public static boolean isEmpty(Context context) {
    return getSet(context).isEmpty();
  }

  /**
   * @return the stored set as a single pipe-joined, lowercase string ("" when empty) — the exact
   *     read-back format of the {@code GET_PROTECTED_CONTACTS} query.
   */
  public static synchronized String getJoined(Context context) {
    return join(getSet(context));
  }

  /**
   * Apply an incoming update from the companion.
   *
   * @param contactsRaw the {@code contacts} extra — a single {@code |}-separated string. May be
   *     null/empty (with {@code replace} that clears the list).
   * @param mode {@code replace} (default) | {@code add} | {@code remove}.
   */
  public static synchronized void update(
      Context context, @Nullable String contactsRaw, @Nullable String mode) {
    Set<String> incoming = parse(contactsRaw);
    String m =
        (mode == null || mode.trim().isEmpty())
            ? MODE_REPLACE
            : mode.trim().toLowerCase(Locale.ROOT);

    Set<String> result;
    switch (m) {
      case MODE_ADD:
        result = getSet(context);
        result.addAll(incoming);
        break;
      case MODE_REMOVE:
        result = getSet(context);
        result.removeAll(incoming);
        break;
      case MODE_REPLACE:
      default:
        result = incoming; // replace — an empty 'contacts' therefore clears the list
        break;
    }
    Prefs.setStringPreference(context, PREF_SET, join(result));
  }

  public static void setTitle(Context context, @Nullable String title) {
    Prefs.setStringPreference(context, PREF_TITLE, title == null ? "" : title);
  }

  public static void setBody(Context context, @Nullable String body) {
    Prefs.setStringPreference(context, PREF_BODY, body == null ? "" : body);
  }

  /** @return the configured vague-notification title, or "" if unset. */
  public static String getTitle(Context context) {
    String t = Prefs.getStringPreference(context, PREF_TITLE, "");
    return t == null ? "" : t;
  }

  /** @return the configured vague-notification body, or "" if unset. */
  public static String getBody(Context context) {
    String b = Prefs.getStringPreference(context, PREF_BODY, "");
    return b == null ? "" : b;
  }

  // --- matching --------------------------------------------------------------

  /**
   * Best-effort, non-blocking, case-insensitive test of whether {@code contact} is protected. Any
   * of the contact's identifiers (address, local name, authorized name, display name) may match an
   * entry in the stored set. Entries that resolve to no contact simply never match — they cause no
   * crash and never drop the rest of the list.
   */
  public static boolean isProtected(Context context, @Nullable DcContact contact) {
    if (contact == null) {
      return false;
    }
    Set<String> set = getSet(context);
    if (set.isEmpty()) {
      return false;
    }
    return matches(set, contact.getAddr())
        || matches(set, contact.getName())
        || matches(set, contact.getAuthName())
        || matches(set, contact.getDisplayName());
  }

  // --- helpers ---------------------------------------------------------------

  private static boolean matches(Set<String> set, @Nullable String candidate) {
    if (candidate == null) {
      return false;
    }
    String c = candidate.trim().toLowerCase(Locale.ROOT);
    return !c.isEmpty() && set.contains(c);
  }

  /** Split on {@code |}, trim + lowercase each entry, drop blanks. Order-preserving, deduped. */
  private static Set<String> parse(@Nullable String raw) {
    Set<String> out = new LinkedHashSet<>();
    if (raw != null && !raw.isEmpty()) {
      for (String part : raw.split("\\|")) {
        String v = part.trim().toLowerCase(Locale.ROOT);
        if (!v.isEmpty()) {
          out.add(v);
        }
      }
    }
    return out;
  }

  private static String join(Set<String> set) {
    StringBuilder sb = new StringBuilder();
    for (String s : set) {
      if (sb.length() > 0) {
        sb.append('|');
      }
      sb.append(s);
    }
    return sb.toString();
  }
}
