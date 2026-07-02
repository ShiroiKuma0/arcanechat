package org.thoughtcrime.securesms.automation;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import org.thoughtcrime.securesms.util.Util;

/**
 * shiroikuma fork — exported control channel for the "protected contacts" feature.
 *
 * <p>The companion app fires an <b>explicit</b> broadcast from a background automation task:
 *
 * <pre>
 *   Intent("shiroikuma.arcanechat.action.SET_PROTECTED_CONTACTS")
 *       .setPackage("shiroikuma.arcanechat")   // may also add FLAG_INCLUDE_STOPPED_PACKAGES
 * </pre>
 *
 * <p>It is unauthenticated on purpose — a local, same-device channel. Extras:
 *
 * <ul>
 *   <li><b>contacts</b> — a single {@code |}-separated string, e.g. {@code alice|bob|charlie}.
 *   <li><b>mode</b> — {@code replace} (default) | {@code add} | {@code remove}.
 *   <li><b>protected_title</b> — optional String, the vague notification's title.
 *   <li><b>protected_body</b> — optional String, the vague notification's body.
 * </ul>
 *
 * <p>All work is done off the main thread via {@link #goAsync()} so {@code onReceive} never blocks
 * / ANRs, and persistence survives process death (see {@link ProtectedContacts}).
 *
 * <p>A second action, {@code GET_PROTECTED_CONTACTS}, lets the companion verify the stored list:
 * it answers an <b>ordered</b> broadcast synchronously with {@code RESULT_OK} and the pipe-joined
 * lowercase set as the result data ({@code "EMPTY"} when the list has no entries).
 */
public class ProtectedContactsReceiver extends BroadcastReceiver {

  private static final String TAG = ProtectedContactsReceiver.class.getSimpleName();

  public static final String ACTION_SET_PROTECTED_CONTACTS =
      "shiroikuma.arcanechat.action.SET_PROTECTED_CONTACTS";
  public static final String ACTION_GET_PROTECTED_CONTACTS =
      "shiroikuma.arcanechat.action.GET_PROTECTED_CONTACTS";

  /** Ordered-broadcast reply when the stored list has no entries (distinguishes from no answer). */
  public static final String RESULT_EMPTY = "EMPTY";

  private static final String EXTRA_CONTACTS = "contacts";
  private static final String EXTRA_MODE = "mode";
  private static final String EXTRA_TITLE = "protected_title";
  private static final String EXTRA_BODY = "protected_body";

  @Override
  public void onReceive(Context context, Intent intent) {
    if (intent == null) {
      return;
    }

    // Read-back channel: answer an ordered broadcast with the persisted list — synchronously,
    // in onReceive (a quick local preference read; no goAsync on this path). "EMPTY" when the
    // list has no entries, else the pipe-joined lowercase set exactly as stored.
    if (ACTION_GET_PROTECTED_CONTACTS.equals(intent.getAction())) {
      try {
        String joined = ProtectedContacts.getJoined(context.getApplicationContext());
        setResultCode(Activity.RESULT_OK);
        setResultData(joined.isEmpty() ? RESULT_EMPTY : joined);
      } catch (Throwable t) {
        Log.w(TAG, "failed to answer protected-contacts query", t);
      }
      return;
    }

    if (!ACTION_SET_PROTECTED_CONTACTS.equals(intent.getAction())) {
      return;
    }

    // Read the (trivial) extras on the calling thread, then do the storage writes off-main.
    final Context appContext = context.getApplicationContext();
    final String contacts = intent.getStringExtra(EXTRA_CONTACTS);
    final String mode = intent.getStringExtra(EXTRA_MODE);
    final boolean hasTitle = intent.hasExtra(EXTRA_TITLE);
    final String title = hasTitle ? intent.getStringExtra(EXTRA_TITLE) : null;
    final boolean hasBody = intent.hasExtra(EXTRA_BODY);
    final String body = hasBody ? intent.getStringExtra(EXTRA_BODY) : null;

    final PendingResult pendingResult = goAsync();
    Util.runOnAnyBackgroundThread(
        () -> {
          try {
            ProtectedContacts.update(appContext, contacts, mode);
            // Only touch the title/body when the companion actually sent them, so a plain
            // contacts update doesn't wipe a previously configured title/body. An empty value is
            // honoured (resets that field to its default).
            if (hasTitle) {
              ProtectedContacts.setTitle(appContext, title);
            }
            if (hasBody) {
              ProtectedContacts.setBody(appContext, body);
            }
            Log.i(
                TAG,
                "protected contacts updated: mode="
                    + mode
                    + " size="
                    + ProtectedContacts.getSet(appContext).size());
          } catch (Throwable t) {
            // Never crash / ANR on a malformed broadcast — tolerate anything.
            Log.w(TAG, "failed to apply protected-contacts update", t);
          } finally {
            pendingResult.finish();
          }
        });
  }
}
