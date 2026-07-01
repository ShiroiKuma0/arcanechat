package org.thoughtcrime.securesms.automation;

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
 */
public class ProtectedContactsReceiver extends BroadcastReceiver {

  private static final String TAG = ProtectedContactsReceiver.class.getSimpleName();

  public static final String ACTION_SET_PROTECTED_CONTACTS =
      "shiroikuma.arcanechat.action.SET_PROTECTED_CONTACTS";

  private static final String EXTRA_CONTACTS = "contacts";
  private static final String EXTRA_MODE = "mode";
  private static final String EXTRA_TITLE = "protected_title";
  private static final String EXTRA_BODY = "protected_body";

  @Override
  public void onReceive(Context context, Intent intent) {
    if (intent == null || !ACTION_SET_PROTECTED_CONTACTS.equals(intent.getAction())) {
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
