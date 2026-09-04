package org.thoughtcrime.securesms.automation;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipFile;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ShiroikumaExport;
import org.thoughtcrime.securesms.util.Util;

/**
 * shiroikuma fork — where a data-door export or import actually runs. Contract v2 §2a; ported from
 * {@code AutomationDataService.kt} in {@code shiroikuma-jiyusagyoban}.
 *
 * <h3>Why a foreground service and not the provider call</h3>
 *
 * <p>The call returns in milliseconds; this can run for minutes (an account tar is a whole Delta
 * Chat profile). Two hard reasons it cannot be done anywhere cheaper:
 *
 * <ul>
 *   <li><b>A binder call holds the caller.</b> 応用管理 is drawing a list; a multi-minute synchronous
 *       call would freeze its UI, report no progress, and refuse cancellation.
 *   <li><b>A backgrounded app writing for minutes is frozen mid-stream on this phone</b>, which
 *       yields a truncated archive underneath a success reply — the worst possible failure, because
 *       it is indistinguishable from a good backup until the day it is restored.
 * </ul>
 *
 * <h3>The descriptor</h3>
 *
 * <p>Already duplicated by {@link AutomationProvider} before it got here, because the original
 * belongs to the binder transaction and is closed the moment {@code call()} returns. This service
 * owns the copy and closes it in a {@code finally} — leaking one would hold the caller's file open
 * indefinitely, and the caller cannot checksum or encrypt a file that is still open.
 *
 * <p>It travels through {@link #HANDOVER} rather than through the Intent: a {@code
 * ParcelFileDescriptor} in an Intent extra is duplicated by the system on delivery and the copy's
 * lifetime stops being ours to reason about. A map keyed by the job id keeps exactly one open
 * descriptor with exactly one owner.
 */
public class AutomationDataService extends Service {

  private static final String TAG = AutomationDataService.class.getSimpleName();

  private static final String CHANNEL = "ch_automation_data";
  private static final int NOTIFICATION_ID = 9714;

  private static final String EXTRA_JOB = "job";
  private static final String EXTRA_IMPORTING = "importing";

  /** See the class comment — an Intent is the wrong vehicle for a descriptor. */
  private static final ConcurrentHashMap<String, ParcelFileDescriptor> HANDOVER =
      new ConcurrentHashMap<>();

  public static void start(
      @NonNull Context context,
      @NonNull String jobId,
      @NonNull ParcelFileDescriptor fd,
      boolean importing,
      @Nullable Bundle extras) {
    HANDOVER.put(jobId, fd);
    Intent intent = new Intent(context, AutomationDataService.class);
    intent.putExtra(EXTRA_JOB, jobId);
    intent.putExtra(EXTRA_IMPORTING, importing);
    if (extras != null) {
      intent.putExtra(AutomationProvider.KEY_ITEMS, extras.getString(AutomationProvider.KEY_ITEMS));
      intent.putExtra(
          AutomationProvider.KEY_REPLY_ACTION, extras.getString(AutomationProvider.KEY_REPLY_ACTION));
      intent.putExtra(
          AutomationProvider.KEY_REPLY_PACKAGE,
          extras.getString(AutomationProvider.KEY_REPLY_PACKAGE));
      intent.putExtra(
          AutomationProvider.KEY_PROGRESS_ACTION,
          extras.getString(AutomationProvider.KEY_PROGRESS_ACTION));
    }
    try {
      ContextCompat.startForegroundService(context, intent);
    } catch (Throwable t) {
      HANDOVER.remove(jobId);
      throw t;
    }
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  /**
   * <b>Go foreground FIRST, validate after.</b> Once {@code startForegroundService()} has been
   * invoked the platform requires a {@code startForeground()} call whatever this method then decides
   * — and it enforces that by killing the process with {@code
   * ForegroundServiceDidNotStartInTimeException}. Returning early on a missing intent or a drained
   * handover entry <i>without</i> going foreground therefore turns "ignore this stale start" into
   * "kill 白い熊's messenger", which is a far worse answer than the one it was avoiding.
   */
  @Override
  public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
    final boolean importing = intent != null && intent.getBooleanExtra(EXTRA_IMPORTING, false);
    enterForeground(importing);

    final String jobId = intent == null ? null : intent.getStringExtra(EXTRA_JOB);
    if (jobId == null) return stop(startId);
    final ParcelFileDescriptor fd = HANDOVER.remove(jobId);
    if (fd == null) {
      // A start whose descriptor has already been taken - nothing to do, and nothing to leak.
      AutomationJobs.finish(jobId);
      return stop(startId);
    }

    final String items = intent.getStringExtra(AutomationProvider.KEY_ITEMS);
    final String replyAction = intent.getStringExtra(AutomationProvider.KEY_REPLY_ACTION);
    final String replyPackage = intent.getStringExtra(AutomationProvider.KEY_REPLY_PACKAGE);
    final String progressAction = intent.getStringExtra(AutomationProvider.KEY_PROGRESS_ACTION);

    final Context app = getApplicationContext();
    final AtomicBoolean replied = new AtomicBoolean(false);

    // The descriptor has left HANDOVER and the background job does not own it yet. ONE flag covers
    // that whole window rather than a guard per failure: whatever goes wrong between here and the
    // hand-off - a rejected executor, anything thrown while reading extras - must still close the
    // caller's file and answer, or 応用管理 waits forever on a descriptor nobody will ever write to.
    boolean handedOff = false;
    try {
      Util.runOnAnyBackgroundThread(
          () -> {
            try {
              if (importing) {
                runImport(app, fd, jobId, replied, replyAction, replyPackage);
              } else {
                runExport(
                    app, fd, jobId, items, progressAction, replied, replyAction, replyPackage);
              }
            } catch (Throwable t) {
              Log.w(TAG, "automation data job failed", t);
              reply(app, replied, replyAction, replyPackage, jobId, failure(t));
            } finally {
              try {
                fd.close();
              } catch (Throwable ignored) {
                // Already closed by the Auto*Stream wrapper on the happy path; twice is harmless.
              }
              AutomationJobs.finish(jobId);
              ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
              stopSelf(startId);
            }
          });
      handedOff = true;
    } finally {
      if (!handedOff) {
        try {
          fd.close();
        } catch (Throwable ignored) {
        }
        reply(app, replied, replyAction, replyPackage, jobId, "ERROR:could not start the job");
        AutomationJobs.finish(jobId);
        stop(startId);
      }
    }
    return START_NOT_STICKY;
  }

  // --- export ---------------------------------------------------------------------------------

  /**
   * Writes the category zip straight into the caller's descriptor.
   *
   * <p>The bytes are counted as they go rather than stat'ed afterwards: the caller owns the file and
   * we may not be able to see it at all — it can be an anonymous pipe, or a descriptor into a
   * directory this app cannot list.
   */
  private void runExport(
      Context app,
      ParcelFileDescriptor fd,
      String jobId,
      @Nullable String items,
      @Nullable String progressAction,
      AtomicBoolean replied,
      @Nullable String replyAction,
      @Nullable String replyPackage) {
    StateExportReceiver.Selection selection;
    try {
      selection = StateExportReceiver.parseItems(app, items);
    } catch (IllegalArgumentException e) {
      reply(
          app,
          replied,
          replyAction,
          replyPackage,
          jobId,
          "ERROR:unknown category in items: " + String.valueOf(items).trim());
      return;
    }
    if (selection.cats.isEmpty()) {
      reply(app, replied, replyAction, replyPackage, jobId, "ERROR:no categories selected");
      return;
    }

    AutomationProgress progress =
        isEmpty(progressAction)
            ? null
            : new AutomationProgress(app, progressAction, replyPackage, jobId);

    final long[] written = {0};
    try (OutputStream raw = new ParcelFileDescriptor.AutoCloseOutputStream(fd)) {
      OutputStream counting =
          new OutputStream() {
            @Override
            public void write(int b) throws java.io.IOException {
              raw.write(b);
              written[0]++;
            }

            @Override
            public void write(@NonNull byte[] b, int off, int len) throws java.io.IOException {
              raw.write(b, off, len);
              written[0] += len;
            }

            @Override
            public void flush() throws java.io.IOException {
              raw.flush();
            }
          };
      ShiroikumaExport.export(app, selection.cats, selection.accountIds, counting, progress);
    } catch (Throwable t) {
      // The caller owns the file: it deletes or discards its temporary on a failed reply. All we
      // must not do is claim success over a short archive.
      reply(app, replied, replyAction, replyPackage, jobId, failure(t));
      return;
    } finally {
      if (progress != null) progress.close();
    }

    if (AutomationJobs.isCancelled(jobId)) {
      reply(app, replied, replyAction, replyPackage, jobId, "ERROR:cancelled");
      return;
    }
    reply(
        app,
        replied,
        replyAction,
        replyPackage,
        jobId,
        "OK:" + written[0] + "|" + selection.cats.size() + " categories");
  }

  // --- import ---------------------------------------------------------------------------------

  /**
   * Reads the whole archive to a cache file before touching anything.
   *
   * <p>{@link ShiroikumaExport#importData} needs random access — account tars stream out of the zip
   * rather than loading into memory — and a descriptor is a one-pass stream. That is also the right
   * shape for a reason beyond mechanics: a partial read that failed halfway would otherwise import
   * half an archive, and a half-restored app is worse than one that refused.
   *
   * <p>The caller force-stops this app straight after a success reply, and that is deliberate: a
   * running process writes its cached SharedPreferences back out at orderly shutdown and would
   * silently undo the import that just happened. The guarantee lives on 応用管理's side so that
   * forty-two apps do not each have to remember it.
   */
  private void runImport(
      Context app,
      ParcelFileDescriptor fd,
      String jobId,
      AtomicBoolean replied,
      @Nullable String replyAction,
      @Nullable String replyPackage) {
    File temp = new File(getCacheDir(), "automation-import-" + jobId + ".zip");
    try {
      long copied = 0;
      try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(fd);
          OutputStream out = new FileOutputStream(temp)) {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
          out.write(buffer, 0, read);
          copied += read;
        }
      }
      if (copied == 0) {
        reply(app, replied, replyAction, replyPackage, jobId, "ERROR:empty archive");
        return;
      }

      try (ZipFile zip = new ZipFile(temp)) {
        // Every category the archive actually carries, not every category we know about: asking for
        // one the archive lacks is how a restore ends up reporting success over nothing.
        List<ShiroikumaExport.Cat> present = ShiroikumaExport.categoriesIn(zip);
        if (present.isEmpty()) {
          reply(app, replied, replyAction, replyPackage, jobId, "ERROR:archive carries no categories");
          return;
        }
        String summary = ShiroikumaExport.importData(app, zip, present);
        if (summary == null) {
          reply(app, replied, replyAction, replyPackage, jobId, "ERROR:nothing restored");
          return;
        }
        // The kill that protects the import would otherwise truncate it. 応用管理 force-stops this
        // app the instant we answer OK - with Process.killProcess, a SIGKILL - precisely so a
        // running process cannot write its cached SharedPreferences back out at orderly shutdown
        // and silently undo the restore. But SIGKILL is equally fatal to a SharedPreferences
        // write that is still in flight, so an asynchronous apply() would be lost and the restore
        // would report success over data that never reached disk. ShiroikumaExport.importPrefs
        // therefore commits synchronously; this is the note that says why it may never go back to
        // apply(). Account tars are safe already - the core writes them through SQLite.
        reply(
            app,
            replied,
            replyAction,
            replyPackage,
            jobId,
            "OK:" + present.size() + " categories restored");
      }
    } catch (Throwable t) {
      reply(app, replied, replyAction, replyPackage, jobId, failure(t));
    } finally {
      //noinspection ResultOfMethodCallIgnored
      temp.delete();
    }
  }

  // --- progress + reply -----------------------------------------------------------------------

  /**
   * The one terminal answer per job, whatever path got here — a synchronous failure and an
   * asynchronous success must never both fire. A fresh broadcast, never a Binder: EMUI will not
   * reliably carry a live Binder into another app's manifest receiver.
   */
  private static void reply(
      Context app,
      AtomicBoolean replied,
      @Nullable String replyAction,
      @Nullable String replyPackage,
      String jobId,
      String result) {
    if (!replied.compareAndSet(false, true)) return;
    if (isEmpty(replyAction) || isEmpty(replyPackage)) {
      Log.i(TAG, "job " + jobId + " finished with no reply channel: " + result);
      return;
    }
    try {
      Intent intent = new Intent(replyAction);
      intent.setPackage(replyPackage);
      // Without this a caller that has been backgrounded never hears the answer, and on a clean
      // phone the caller may not have been launched at all.
      intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
      intent.putExtra(AutomationProvider.KEY_JOB_ID, jobId);
      intent.putExtra(AutomationProvider.KEY_RESULT, result);
      app.sendBroadcast(intent);
      Log.i(TAG, "replied to " + replyPackage + " [" + jobId + "]: " + result);
    } catch (Throwable t) {
      Log.w(TAG, "could not deliver the reply", t);
    }
  }

  // --- foreground -----------------------------------------------------------------------------

  /**
   * {@code specialUse} per contract §4, but declared defensively.
   *
   * <p>The typed {@code startForeground} overload is only asked for the {@code specialUse} type on
   * API 34+, where the constant exists; below that the plain overload lets the system use the
   * manifest's declared type. 白い熊's Mate XT is EMUI 14.2 reporting {@code SDK_INT = 31}, which is
   * exactly the platform where guessing at type validation would be wrong in both directions — so
   * the whole thing falls back to an untyped foreground rather than dying.
   */
  private void enterForeground(boolean importing) {
    Notification notification = buildNotification(importing);
    try {
      if (Build.VERSION.SDK_INT >= 34) {
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
      } else {
        startForeground(NOTIFICATION_ID, notification);
      }
    } catch (Throwable t) {
      Log.w(TAG, "typed startForeground refused, falling back", t);
      try {
        startForeground(NOTIFICATION_ID, notification);
      } catch (Throwable fatal) {
        Log.w(TAG, "could not enter the foreground at all", fatal);
      }
    }
  }

  private Notification buildNotification(boolean importing) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationManager manager = getSystemService(NotificationManager.class);
      if (manager != null && manager.getNotificationChannel(CHANNEL) == null) {
        manager.createNotificationChannel(
            new NotificationChannel(
                CHANNEL,
                getString(R.string.automation_data_channel_name),
                NotificationManager.IMPORTANCE_LOW));
      }
    }
    return new NotificationCompat.Builder(this, CHANNEL)
        .setContentTitle(
            getString(
                importing
                    ? R.string.automation_data_importing
                    : R.string.automation_data_exporting))
        .setSmallIcon(R.drawable.notification_permanent)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .build();
  }

  // --- helpers --------------------------------------------------------------------------------

  /** Always paired with {@link #enterForeground} — see the note on {@link #onStartCommand}. */
  private int stop(int startId) {
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
    stopSelf(startId);
    return START_NOT_STICKY;
  }

  /** A cancel is reported as the contract's fixed {@code ERROR:cancelled}, not as what it threw. */
  private static String failure(Throwable t) {
    if (t instanceof ShiroikumaExport.CancelledException) return "ERROR:cancelled";
    return "ERROR:" + AutomationProvider.reason(t);
  }

  private static boolean isEmpty(@Nullable String s) {
    return s == null || s.trim().isEmpty();
  }
}
