package org.thoughtcrime.securesms.automation;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.util.ShiroikumaExport;

/**
 * shiroikuma fork — the data door: export this app's own state, and put it back, for a caller we can
 * identify. Contract v2 §2a; ported from {@code AutomationProvider.kt} in
 * {@code shiroikuma-jiyusagyoban}.
 *
 * <h3>Why a provider and not the broadcast receiver next to it</h3>
 *
 * <p><b>A broadcast cannot tell you who sent it.</b> The old contract's answer to that was a shared
 * secret, which cannot survive the wipe this feature exists to recover from. A provider gets the
 * caller's identity from the framework for free — see {@link AutomationCallers} for what is actually
 * checked and why a package-name prefix would have been worse than the token it replaced.
 *
 * <p><b>And a list needs a synchronous answer.</b> 白い熊 応用管理 draws a row per installed app before
 * any export exists; a broadcast round trip per app to fill a list is the wrong shape entirely.
 *
 * <h3>What does NOT happen here</h3>
 *
 * <p>The payload. {@link #call} validates, starts a foreground service and returns — tens of
 * megabytes over minutes inside a binder call would block the caller, report no progress, refuse
 * cancellation and die silently if this process were killed. The bytes go through a file descriptor
 * the caller opened, and the terminal answer comes back on the broadcast the family already proved
 * on EMUI.
 *
 * <h3>Why a descriptor and not a path</h3>
 *
 * <p>Because a backup is not a stable directory while it is being assembled: 応用管理 writes into a
 * temporary path and renames on commit, and it encrypts and checksums <b>per file it knows
 * about</b>. A file this app dropped into that directory itself would be renamed out from under it,
 * would sit in plaintext inside an encrypted backup, and would be unverified rather than
 * verified-and-failing. A descriptor is also a capability that <b>expires when it is closed</b>.
 *
 * <p><b>{@code import} exists only here</b> and never gets a broadcast action: an import overwrites
 * this app's data, and {@link StateExportReceiver} is {@code exported="true"} with no permission —
 * an import there would let any app on the phone wipe any sister app.
 */
public class AutomationProvider extends ContentProvider {

  private static final String TAG = AutomationProvider.class.getSimpleName();

  public static final String METHOD_DESCRIBE = "describe";
  public static final String METHOD_EXPORT = "export";
  public static final String METHOD_IMPORT = "import";
  public static final String METHOD_CANCEL = "cancel";

  public static final String KEY_RESULT = "result";
  public static final String KEY_FD = "fd";
  public static final String KEY_TOKEN = "token";
  public static final String KEY_JOB_ID = "job_id";
  public static final String KEY_ITEMS = "items";
  public static final String KEY_REPLY_ACTION = "reply_action";
  public static final String KEY_REPLY_PACKAGE = "reply_package";
  public static final String KEY_PROGRESS_ACTION = "progress_action";

  /**
   * This app's archive format — the same number the export zip's {@code manifest.json} carries, so
   * the two can never disagree. Bumped when an older build could no longer read what we write.
   */
  public static final int FORMAT = ShiroikumaExport.VERSION;

  /**
   * The oldest archive this build can still read.
   *
   * <p>Version skew has a direction: old data into a newer app is normally fine, because an app
   * migrates its own storage; newer data into an older app is not. This field is what lets a caller
   * refuse the second case at discovery time, before anything is streamed.
   */
  public static final int MIN_FORMAT_READABLE = 1;

  @Override
  public boolean onCreate() {
    return true;
  }

  /**
   * Every method answers a {@link Bundle} with {@link #KEY_RESULT} — {@code OK…} or {@code ERROR:…},
   * the same vocabulary the broadcast contract uses, so a caller has one grammar to parse rather
   * than two.
   *
   * <p>A refusal is <b>returned, never thrown</b>: an exception across a binder reaches the caller
   * as a {@code RuntimeException} with our stack trace in it, which tells 白い熊 nothing and tells a
   * misbehaving caller rather more than it should.
   */
  @Nullable
  @Override
  public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
    final Context ctx = getContext();
    if (ctx == null) return result("ERROR:not ready");

    // WHO, before WHAT. A caller we cannot identify gets the same answer whatever it asked for.
    // Both getCallingPackage() and Binder.getCallingUid() are only meaningful on this binder
    // thread, so the identity check happens before anything else and before any hand-off.
    String refusal = AutomationCallers.verify(ctx, getCallingPackage());
    if (refusal != null) {
      Log.w(TAG, "automation " + method + " refused: " + refusal);
      return result(refusal);
    }
    // Then the app's own switches - a token is ignored unless this app asks for one.
    refusal = AutomationAuth.refuse(ctx, extras == null ? null : extras.getString(KEY_TOKEN));
    if (refusal != null) return result(refusal);

    try {
      switch (method) {
        case METHOD_DESCRIBE:
          return result(describe(ctx));
        case METHOD_EXPORT:
          return start(ctx, extras, false);
        case METHOD_IMPORT:
          return start(ctx, extras, true);
        case METHOD_CANCEL:
          return cancel(extras);
        default:
          return result("ERROR:unknown method: " + method);
      }
    } catch (Throwable t) {
      Log.w(TAG, "automation " + method + " failed", t);
      return result("ERROR:" + reason(t));
    }
  }

  // --- describe -------------------------------------------------------------------------------

  /**
   * What this app would export, answered without exporting anything.
   *
   * <p>Returned from the call rather than written into the archive, deliberately: 応用管理 must draw a
   * row <b>before</b> an export exists, and at restore must judge compatibility before streaming
   * tens of megabytes into an app that would reject them — which it cannot do if the header is
   * buried inside an encrypted archive.
   *
   * <p>{@code requires_launch_first} is false: an import merges into the default SharedPreferences
   * and restores account tars through the core, both of which come up with the process the provider
   * call itself started. Nothing here needs an Activity to have run.
   */
  private static String describe(@NonNull Context ctx) throws Exception {
    JSONArray contains = new JSONArray();
    for (ShiroikumaExport.Cat cat : ShiroikumaExport.Cat.values()) {
      if (cat.defaultSelected) contains.put(ctx.getString(cat.labelRes));
    }
    JSONObject header =
        new JSONObject()
            .put("app_id", ctx.getPackageName())
            .put("version_code", BuildConfig.VERSION_CODE)
            .put("version_name", BuildConfig.VERSION_NAME)
            .put("format", FORMAT)
            .put("min_format_readable", MIN_FORMAT_READABLE)
            .put("requires_launch_first", false)
            .put("contains", contains);
    return "OK:" + header.toString();
  }

  // --- export / import ------------------------------------------------------------------------

  /**
   * Hand the descriptor to a foreground service and get out of the way.
   *
   * <p>The descriptor is <b>duplicated</b> before it leaves this method. The one in {@code extras}
   * belongs to the binder transaction and is closed when {@code call()} returns; a service reading
   * it afterwards would find it shut. That is a bug you only see under load, so it is not left to
   * the service to remember.
   */
  private Bundle start(@NonNull Context ctx, @Nullable Bundle extras, boolean importing) {
    if (extras == null) return result("ERROR:no descriptor");
    @SuppressWarnings("deprecation")
    ParcelFileDescriptor fd = extras.getParcelable(KEY_FD);
    if (fd == null) return result("ERROR:no descriptor");

    ParcelFileDescriptor dup;
    try {
      dup = fd.dup();
    } catch (Throwable t) {
      Log.w(TAG, "could not duplicate the caller's descriptor", t);
      return result("ERROR:descriptor unusable");
    }

    String jobId = AutomationJobs.begin();
    try {
      AutomationDataService.start(ctx, jobId, dup, importing, extras);
    } catch (Throwable t) {
      AutomationJobs.finish(jobId);
      try {
        dup.close();
      } catch (Throwable ignored) {
      }
      Log.w(TAG, "could not start the automation data service", t);
      return result("ERROR:" + reason(t));
    }
    return result("OK:" + jobId);
  }

  /**
   * Signal the running job. Safe at any time — a cancel that arrives when nothing is running, or
   * after the work already finished, is a silent no-op rather than an error.
   *
   * <p>A <b>stale</b> id is deliberately not passed on to the export engine: {@link
   * ShiroikumaExport#requestCancel()} unwinds whatever export is running process-wide, so honouring
   * an id nobody recognises could tear down a different run — including the one 白い熊 started by
   * hand from the Export/Import panel.
   */
  private Bundle cancel(@Nullable Bundle extras) {
    String jobId = extras == null ? null : extras.getString(KEY_JOB_ID);
    boolean bare = jobId == null || jobId.isEmpty();
    boolean matched = AutomationJobs.cancel(jobId);
    if (bare || matched) ShiroikumaExport.requestCancel();
    return result("OK:cancelled");
  }

  // --- helpers --------------------------------------------------------------------------------

  private static Bundle result(@NonNull String result) {
    Bundle bundle = new Bundle();
    bundle.putString(KEY_RESULT, result);
    return bundle;
  }

  /** One short line, whatever the throwable carried — the answer is a single line by contract. */
  static String reason(@NonNull Throwable t) {
    String message = t.getMessage();
    if (message == null || message.trim().isEmpty()) message = t.getClass().getSimpleName();
    message = message.replace('\n', ' ').replace('\r', ' ').trim();
    return message.length() > 160 ? message.substring(0, 160) : message;
  }

  // A provider that is only ever call()ed still has to answer these. Refusing loudly beats returning
  // an empty cursor, which reads downstream as "there is no data" rather than "wrong door".

  @Nullable
  @Override
  public Cursor query(
      @NonNull Uri uri,
      @Nullable String[] projection,
      @Nullable String selection,
      @Nullable String[] selectionArgs,
      @Nullable String sortOrder) {
    throw new UnsupportedOperationException("automation is call() only");
  }

  @Nullable
  @Override
  public String getType(@NonNull Uri uri) {
    return null;
  }

  @Nullable
  @Override
  public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
    throw new UnsupportedOperationException("automation is call() only");
  }

  @Override
  public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] args) {
    throw new UnsupportedOperationException("automation is call() only");
  }

  @Override
  public int update(
      @NonNull Uri uri,
      @Nullable ContentValues values,
      @Nullable String selection,
      @Nullable String[] args) {
    throw new UnsupportedOperationException("automation is call() only");
  }
}
