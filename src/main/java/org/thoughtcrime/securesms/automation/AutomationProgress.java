package org.thoughtcrime.securesms.automation;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.Closeable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ShiroikumaExport;

/**
 * shiroikuma fork — the automation contract's progress channel (§3), shared by both doors.
 *
 * <h3>A throttle is not a heartbeat</h3>
 *
 * <p>These are two different jobs and one does not do the other. The <b>throttle</b> stops a chatty
 * export flooding the caller: at most one broadcast per {@link #THROTTLE_MS}. The <b>heartbeat</b>
 * keeps a quiet export alive: 自由作業盤 and 応用管理 both treat every progress broadcast as proof the
 * app is still running and <b>give up on an app that goes silent for two minutes</b>, failing its
 * slot.
 *
 * <p>This app needs the second because of where its callbacks actually come from: {@link
 * ShiroikumaExport} reports once per <i>category</i> and once per <i>account</i>, and one of those
 * accounts is a whole Delta Chat profile written by a single blocking {@code Rpc.exportBackup} call.
 * A multi-gigabyte profile therefore ticks <b>once</b> and then says nothing for as long as the core
 * takes — with a perfectly correct 500 ms throttle in place. The throttle cannot save you when the
 * silence is upstream of it.
 *
 * <p>So a ticker re-emits the last line every {@link #HEARTBEAT_MS} whenever nothing real has gone
 * out. The contract asks for one at least every 30 s; 25 s leaves margin for a loaded phone.
 *
 * <p><b>A heartbeat is a promise, not a shield.</b> Because it keeps the caller waiting, an export
 * that hangs while still ticking holds its slot until the full timeout — which is why the engine
 * still checks its cancel flag at every entry boundary rather than relying on this to notice.
 */
final class AutomationProgress implements ShiroikumaExport.ProgressListener, Closeable {

  private static final String TAG = AutomationProgress.class.getSimpleName();

  /** At most one broadcast per this many ms (a completion event always goes out). */
  private static final long THROTTLE_MS = 500;

  /** Re-emit the last line after this much silence — the contract's floor is 30 s. */
  private static final long HEARTBEAT_MS = 25_000;

  private static final String EXTRA_REPLY_ID = "reply_id";
  private static final String EXTRA_APP = "app";
  private static final String EXTRA_TEXT = "text";
  private static final String EXTRA_CURRENT = "current";
  private static final String EXTRA_TOTAL = "total";
  private static final String EXTRA_UNIT = "unit";

  private final Context app;
  private final String progressAction;
  private final @Nullable String replyPackage;
  private final String correlationId;
  private final String label;

  private final ScheduledExecutorService ticker;

  // Written from the export thread, read from the ticker thread.
  private volatile long lastSentAt;
  private volatile @Nullable String lastText;
  private volatile long lastCurrent;
  private volatile long lastTotal;
  private volatile @Nullable String lastUnit;

  /**
   * @param correlationId the value the caller keys progress off — {@code reply_id} for the broadcast
   *     door, the {@code job_id} for the data door. It is echoed verbatim and never interpreted.
   */
  AutomationProgress(
      @NonNull Context app,
      @NonNull String progressAction,
      @Nullable String replyPackage,
      @NonNull String correlationId) {
    this.app = app.getApplicationContext();
    this.progressAction = progressAction;
    this.replyPackage = replyPackage;
    this.correlationId = correlationId;
    this.label = this.app.getString(R.string.app_name);
    this.lastSentAt = SystemClock.elapsedRealtime();

    this.ticker =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "automation-heartbeat");
              // Daemon: a stuck heartbeat must never be the reason this process stays alive.
              t.setDaemon(true);
              return t;
            });
    this.ticker.scheduleWithFixedDelay(
        this::beat, HEARTBEAT_MS, HEARTBEAT_MS / 2, TimeUnit.MILLISECONDS);
  }

  /** Real progress from the engine — throttled, except that completion always goes out. */
  @Override
  public void onProgress(@NonNull String text, long current, long total, @NonNull String unit) {
    lastText = text;
    lastCurrent = current;
    lastTotal = total;
    lastUnit = unit;

    long now = SystemClock.elapsedRealtime();
    if (current < total && now - lastSentAt < THROTTLE_MS) return;
    send(text, current, total, unit, now);
  }

  /** The ticker: re-send the last line if the engine has gone quiet, so we are not presumed dead. */
  private void beat() {
    try {
      String text = lastText;
      if (text == null) return; // nothing has happened yet; there is no line to repeat
      long now = SystemClock.elapsedRealtime();
      if (now - lastSentAt < HEARTBEAT_MS) return;
      send(text, lastCurrent, lastTotal, lastUnit == null ? "" : lastUnit, now);
    } catch (Throwable t) {
      // A failed heartbeat must never take the export down with it.
      Log.w(TAG, "heartbeat failed", t);
    }
  }

  private void send(String text, long current, long total, String unit, long now) {
    lastSentAt = now;
    try {
      Intent intent = new Intent(progressAction);
      // No package to aim at means nobody can hear it: since API 26 an implicit broadcast reaches no
      // manifest-declared receiver, so setPackage(null) is not a wider send, it is no send.
      if (replyPackage != null && !replyPackage.trim().isEmpty()) intent.setPackage(replyPackage);
      intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
      intent.putExtra(EXTRA_REPLY_ID, correlationId);
      intent.putExtra(AutomationProvider.KEY_JOB_ID, correlationId);
      intent.putExtra(EXTRA_APP, label);
      intent.putExtra(EXTRA_TEXT, text);
      intent.putExtra(EXTRA_CURRENT, current);
      intent.putExtra(EXTRA_TOTAL, total);
      intent.putExtra(EXTRA_UNIT, unit);
      app.sendBroadcast(intent);
    } catch (Throwable t) {
      Log.w(TAG, "could not send progress", t);
    }
  }

  @Override
  public void close() {
    ticker.shutdownNow();
  }
}
