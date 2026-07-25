package org.thoughtcrime.securesms.automation;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ShiroikumaExport;
import org.thoughtcrime.securesms.util.Util;

/**
 * shiroikuma fork — the 保存復元 state-export automation contract. The companion app (白い熊
 * 自由作業盤) backs every sister app up in one run: it fires a token-gated broadcast, the app
 * exports itself headlessly, reports progress with real counts, and replies with the written path
 * and size.
 *
 * <p>Two exported actions, both gated by {@link AutomationAuth} (switch first, then token — they are
 * reported as distinct errors because they debug differently):
 *
 * <ul>
 *   <li><b>{@code shiroikuma.arcanechat.action.LIST_CATEGORIES}</b> — replies {@code OK:} plus one
 *       {@code id<TAB>label} line per exportable category. The Accounts category also lists each
 *       configured account as an {@code accounts.<accountId><TAB>label<TAB>accounts} sub-option.
 *   <li><b>{@code shiroikuma.arcanechat.action.EXPORT_STATE}</b> — runs the same category zip the
 *       Export/Import page writes, headlessly, and replies {@code OK:<path>|<bytes>|<human>|<n>
 *       categories}. Extras: {@code path} (absolute directory, overrides the configured one),
 *       {@code items} (comma-separated category ids; absent = everything), {@code progress_action}.
 * </ul>
 *
 * <p>The reply is always a <b>fresh broadcast</b> with {@code FLAG_INCLUDE_STOPPED_PACKAGES} — on
 * EMUI a live Binder ({@code ResultReceiver}/{@code PendingIntent}) or the ordered-broadcast result
 * channel does not reliably survive between third-party apps (verified on 白い熊's Mate XT,
 * 2026-07-23). The ordered result is set too when the broadcast happens to be ordered, but it is
 * never the only reply. Exactly one terminal reply is sent per request ({@link AtomicBoolean}).
 */
public class StateExportReceiver extends BroadcastReceiver {

  private static final String TAG = StateExportReceiver.class.getSimpleName();

  public static final String ACTION_EXPORT_STATE = "shiroikuma.arcanechat.action.EXPORT_STATE";
  public static final String ACTION_LIST_CATEGORIES =
      "shiroikuma.arcanechat.action.LIST_CATEGORIES";

  private static final String EXTRA_TOKEN = "token";
  private static final String EXTRA_PATH = "path";
  private static final String EXTRA_ITEMS = "items";
  private static final String EXTRA_PROGRESS_ACTION = "progress_action";
  private static final String EXTRA_REPLY_ACTION = "reply_action";
  private static final String EXTRA_REPLY_PACKAGE = "reply_package";
  private static final String EXTRA_REPLY_ID = "reply_id";

  private static final String EXTRA_RESULT = "result";
  private static final String EXTRA_APP = "app";
  private static final String EXTRA_TEXT = "text";
  private static final String EXTRA_CURRENT = "current";
  private static final String EXTRA_TOTAL = "total";
  private static final String EXTRA_UNIT = "unit";

  /** At most one progress broadcast per this many ms (the completion one always goes out). */
  private static final long PROGRESS_INTERVAL_MS = 500;

  @Override
  public void onReceive(Context context, Intent intent) {
    if (intent == null) return;
    final String action = intent.getAction();
    if (!ACTION_EXPORT_STATE.equals(action) && !ACTION_LIST_CATEGORIES.equals(action)) return;

    final Context app = context.getApplicationContext();
    final String token = intent.getStringExtra(EXTRA_TOKEN);
    final String path = intent.getStringExtra(EXTRA_PATH);
    final String items = intent.getStringExtra(EXTRA_ITEMS);
    final String progressAction = intent.getStringExtra(EXTRA_PROGRESS_ACTION);
    final String replyAction = intent.getStringExtra(EXTRA_REPLY_ACTION);
    final String replyPackage = intent.getStringExtra(EXTRA_REPLY_PACKAGE);
    final String replyId = intent.getStringExtra(EXTRA_REPLY_ID);

    if (isEmpty(replyAction) || isEmpty(replyPackage) || replyId == null) {
      Log.w(TAG, "ignoring " + action + " — no reply channel (reply_action/package/id)");
      return;
    }

    final boolean ordered = isOrderedBroadcast();
    final PendingResult pendingResult = goAsync();
    final AtomicBoolean replied = new AtomicBoolean(false);

    Util.runOnAnyBackgroundThread(
        () -> {
          String result;
          try {
            if (!AutomationAuth.isEnabled(app)) {
              result = "ERROR:automation disabled";
            } else if (!AutomationAuth.matches(app, token)) {
              result = "ERROR:bad token";
            } else if (ACTION_LIST_CATEGORIES.equals(action)) {
              result = listCategories(app);
            } else {
              result = runExport(app, path, items, progressAction, replyPackage, replyId);
            }
          } catch (Throwable t) {
            Log.w(TAG, "automation request failed", t);
            result = "ERROR:" + reason(t);
          }
          try {
            if (ordered) {
              pendingResult.setResultCode(Activity.RESULT_OK);
              pendingResult.setResultData(result);
            }
          } catch (Throwable ignored) {
            // EMUI severs the ordered result between third-party apps; the broadcast below is the
            // reply that actually arrives.
          }
          sendReply(app, replied, replyAction, replyPackage, replyId, result);
          pendingResult.finish();
        });
  }

  // --- LIST_CATEGORIES ------------------------------------------------------------------------

  /** {@code OK:} + one {@code id<TAB>label[<TAB>parent-id]} line per category / sub-option. */
  private static String listCategories(Context app) {
    StringBuilder sb = new StringBuilder("OK:");
    boolean first = true;
    for (ShiroikumaExport.Cat cat : ShiroikumaExport.Cat.values()) {
      if (!first) sb.append('\n');
      first = false;
      sb.append(cat.id).append('\t').append(app.getString(cat.labelRes));
      if (cat == ShiroikumaExport.Cat.ACCOUNTS) {
        // each configured profile is independently selectable via items=accounts.<id>
        for (ShiroikumaExport.AccountEntry entry : ShiroikumaExport.listAccounts(app)) {
          sb.append('\n')
              .append(entry.itemId())
              .append('\t')
              .append(entry.label)
              .append('\t')
              .append(cat.id);
        }
      }
    }
    return sb.toString();
  }

  // --- EXPORT_STATE ---------------------------------------------------------------------------

  /** The categories (and, for Accounts, the profiles) an {@code items} extra asks for. */
  private static final class Selection {
    final List<ShiroikumaExport.Cat> cats = new ArrayList<>();
    /** null = every configured account. */
    @Nullable Set<Integer> accountIds;
  }

  /**
   * Runs the export and returns the single-line result. Directory precedence: the {@code path}
   * extra, then the app's configured (SAF) export directory, then {@code ERROR:no-directory}.
   */
  private String runExport(
      Context app,
      @Nullable String pathExtra,
      @Nullable String items,
      @Nullable String progressAction,
      String replyPackage,
      String replyId) {
    Selection selection;
    try {
      selection = parseItems(app, items);
    } catch (IllegalArgumentException e) {
      return "ERROR:unknown category in items: " + String.valueOf(items).trim();
    }
    if (selection.cats.isEmpty()) return "ERROR:no categories selected";

    File plainDir = null;
    DocumentFile safDir = null;
    if (!isEmpty(pathExtra)) {
      // writing to an arbitrary absolute path needs All-Files-Access (declared for the font picker)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
          && !Environment.isExternalStorageManager()) {
        return "ERROR:no-storage-access";
      }
      plainDir = new File(pathExtra.trim());
      if (!plainDir.isDirectory() && !plainDir.mkdirs()) {
        return "ERROR:cannot create directory " + plainDir.getAbsolutePath();
      }
    } else {
      safDir = ShiroikumaExport.getExportDir(app);
      if (safDir == null) return "ERROR:no-directory";
    }

    ShiroikumaExport.ProgressListener progress =
        isEmpty(progressAction)
            ? null
            : newProgressSender(app, progressAction, replyPackage, replyId);

    String name = ShiroikumaExport.exportFileName();
    long bytes;
    String writtenPath;
    if (plainDir != null) {
      File target = new File(plainDir, name);
      try (OutputStream out = new FileOutputStream(target)) {
        ShiroikumaExport.export(app, selection.cats, selection.accountIds, out, progress);
      } catch (Throwable t) {
        //noinspection ResultOfMethodCallIgnored
        target.delete(); // never leave a truncated export behind
        Log.w(TAG, "headless export failed", t);
        return "ERROR:" + reason(t);
      }
      bytes = target.length();
      writtenPath = target.getAbsolutePath();
    } else {
      DocumentFile file = safDir.createFile("application/zip", name);
      if (file == null) return "ERROR:cannot create " + name;
      CountingOutputStream counting = null;
      try (OutputStream raw = app.getContentResolver().openOutputStream(file.getUri())) {
        if (raw == null) throw new IllegalStateException("no output stream");
        counting = new CountingOutputStream(raw);
        ShiroikumaExport.export(app, selection.cats, selection.accountIds, counting, progress);
      } catch (Throwable t) {
        file.delete();
        Log.w(TAG, "headless export failed", t);
        return "ERROR:" + reason(t);
      }
      long reported = file.length();
      bytes = reported > 0 ? reported : counting.written;
      writtenPath = absolutePathOf(file.getUri());
    }

    return "OK:"
        + writtenPath
        + "|"
        + bytes
        + "|"
        + humanSize(bytes)
        + "|"
        + selection.cats.size()
        + " categories";
  }

  /**
   * Maps the {@code items} extra onto categories. A bare {@code accounts} means every profile; an
   * {@code accounts.<id>} sub-option means exactly that profile (several may be combined, and the
   * parent wins when both are present). Absent/empty = everything.
   */
  private static Selection parseItems(Context app, @Nullable String items) {
    Selection selection = new Selection();
    if (isEmpty(items)) {
      for (ShiroikumaExport.Cat cat : ShiroikumaExport.Cat.values()) selection.cats.add(cat);
      return selection;
    }

    boolean allAccounts = false;
    Set<Integer> accountIds = new HashSet<>();
    List<ShiroikumaExport.AccountEntry> accounts = null; // resolved lazily - it spins up the core
    for (String raw : items.split(",")) {
      String item = raw.trim();
      if (item.isEmpty()) continue;
      ShiroikumaExport.Cat match = null;
      for (ShiroikumaExport.Cat cat : ShiroikumaExport.Cat.values()) {
        if (cat.id.equals(item)) match = cat;
      }
      if (match != null) {
        if (!selection.cats.contains(match)) selection.cats.add(match);
        if (match == ShiroikumaExport.Cat.ACCOUNTS) allAccounts = true;
        continue;
      }
      if (!item.startsWith(ShiroikumaExport.Cat.ACCOUNTS.id + ".")) {
        throw new IllegalArgumentException(item);
      }
      if (accounts == null) accounts = ShiroikumaExport.listAccounts(app);
      ShiroikumaExport.AccountEntry hit = null;
      for (ShiroikumaExport.AccountEntry entry : accounts) {
        if (entry.itemId().equals(item)) hit = entry;
      }
      if (hit == null) throw new IllegalArgumentException(item);
      accountIds.add(hit.accountId);
      if (!selection.cats.contains(ShiroikumaExport.Cat.ACCOUNTS)) {
        selection.cats.add(ShiroikumaExport.Cat.ACCOUNTS);
      }
    }
    // keep the declaration order of the enum, whatever order items arrived in
    List<ShiroikumaExport.Cat> ordered = new ArrayList<>();
    for (ShiroikumaExport.Cat cat : ShiroikumaExport.Cat.values()) {
      if (selection.cats.contains(cat)) ordered.add(cat);
    }
    selection.cats.clear();
    selection.cats.addAll(ordered);
    selection.accountIds = (allAccounts || accountIds.isEmpty()) ? null : accountIds;
    return selection;
  }

  // --- progress + reply -----------------------------------------------------------------------

  /** Throttled progress sender; a completion event ({@code current >= total}) always goes out. */
  private ShiroikumaExport.ProgressListener newProgressSender(
      Context app, String progressAction, String replyPackage, String replyId) {
    final String label = app.getString(R.string.app_name);
    final long[] lastSent = {0};
    return (text, current, total, unit) -> {
      long now = SystemClock.elapsedRealtime();
      if (current < total && now - lastSent[0] < PROGRESS_INTERVAL_MS) return;
      lastSent[0] = now;
      try {
        Intent intent = new Intent(progressAction);
        intent.setPackage(replyPackage);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        intent.putExtra(EXTRA_REPLY_ID, replyId);
        intent.putExtra(EXTRA_APP, label);
        intent.putExtra(EXTRA_TEXT, text);
        intent.putExtra(EXTRA_CURRENT, current);
        intent.putExtra(EXTRA_TOTAL, total);
        intent.putExtra(EXTRA_UNIT, unit);
        app.sendBroadcast(intent);
      } catch (Throwable t) {
        Log.w(TAG, "could not send progress", t);
      }
    };
  }

  /** The one terminal reply per request — a fresh broadcast, never a Binder. */
  private static void sendReply(
      Context app,
      AtomicBoolean replied,
      String replyAction,
      String replyPackage,
      String replyId,
      String result) {
    if (!replied.compareAndSet(false, true)) return;
    try {
      Intent intent = new Intent(replyAction);
      intent.setPackage(replyPackage);
      intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
      intent.putExtra(EXTRA_REPLY_ID, replyId);
      intent.putExtra(EXTRA_RESULT, result);
      app.sendBroadcast(intent);
      Log.i(TAG, "replied to " + replyPackage + " [" + replyId + "]: " + result);
    } catch (Throwable t) {
      Log.w(TAG, "could not deliver the reply", t);
    }
  }

  // --- helpers --------------------------------------------------------------------------------

  /** Counts what actually reached the SAF stream, in case the provider can't stat the file. */
  private static final class CountingOutputStream extends OutputStream {
    private final OutputStream delegate;
    long written;

    CountingOutputStream(OutputStream delegate) {
      this.delegate = delegate;
    }

    @Override
    public void write(int b) throws java.io.IOException {
      delegate.write(b);
      written++;
    }

    @Override
    public void write(@NonNull byte[] b, int off, int len) throws java.io.IOException {
      delegate.write(b, off, len);
      written += len;
    }

    @Override
    public void flush() throws java.io.IOException {
      delegate.flush();
    }

    @Override
    public void close() throws java.io.IOException {
      delegate.close();
    }
  }

  /** Best-effort absolute path for a SAF document; falls back to the uri when it isn't derivable. */
  private static String absolutePathOf(Uri uri) {
    try {
      if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
        String[] split = DocumentsContract.getDocumentId(uri).split(":", 2);
        if (split.length == 2) {
          if ("primary".equalsIgnoreCase(split[0])) {
            return Environment.getExternalStorageDirectory() + "/" + split[1];
          }
          return "/storage/" + split[0] + "/" + split[1]; // removable volume, best effort
        }
      }
    } catch (Throwable ignored) {
    }
    return uri.toString();
  }

  /** Display size for the reply — the caller cannot stat the file, so we compute both forms. */
  private static String humanSize(long bytes) {
    if (bytes < 1024) return bytes + " B";
    double kb = bytes / 1024.0;
    if (kb < 1024) return String.format(Locale.ROOT, "%.1f KB", kb);
    double mb = kb / 1024.0;
    if (mb < 1024) return String.format(Locale.ROOT, "%.1f MB", mb);
    return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0);
  }

  /** One short line, whatever the throwable carried — the reply is a single line by contract. */
  private static String reason(Throwable t) {
    String message = t.getMessage();
    if (message == null || message.trim().isEmpty()) message = t.getClass().getSimpleName();
    message = message.replace('\n', ' ').replace('\r', ' ').trim();
    return message.length() > 160 ? message.substring(0, 160) : message;
  }

  private static boolean isEmpty(@Nullable String s) {
    return s == null || s.trim().isEmpty();
  }
}
