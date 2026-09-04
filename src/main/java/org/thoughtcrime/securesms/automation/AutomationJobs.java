package org.thoughtcrime.securesms.automation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * shiroikuma fork — the jobs the data door has started, and the flag each of them watches to stop.
 *
 * <p>Ported from {@code AutomationJobs.kt} in {@code shiroikuma-jiyusagyoban}. What this owns is the
 * mapping from the id a caller was handed to a cancellation it can act on, which must outlive the
 * binder call that created it and be reachable from a service that never saw the caller.
 *
 * <p>One export at a time is not enforced here — {@link
 * org.thoughtcrime.securesms.util.ShiroikumaExport} already refuses a second concurrent run with
 * {@code ERROR:export already running}, because both paths stage account tars in the same cache
 * directory.
 */
public final class AutomationJobs {

  private static final ConcurrentHashMap<String, Boolean> CANCELLED = new ConcurrentHashMap<>();

  private AutomationJobs() {}

  @NonNull
  public static String begin() {
    String jobId = UUID.randomUUID().toString();
    CANCELLED.put(jobId, Boolean.FALSE);
    return jobId;
  }

  /**
   * Ask a job to stop.
   *
   * <p>Deliberately silent about an id that is finished or was never real: a cancel arriving after
   * the work completed is the normal race, not an error, and answering it as one would make every
   * well-behaved caller look broken.
   *
   * @return true when a live job was actually marked — the provider uses this to decide whether to
   *     also signal the export engine, so a stale id can never unwind somebody else's export.
   */
  public static boolean cancel(@Nullable String jobId) {
    if (jobId == null || jobId.isEmpty()) return false;
    return CANCELLED.replace(jobId, Boolean.TRUE) != null;
  }

  /** Polled at write boundaries — never mid-write, so a cancelled archive is never half a file. */
  public static boolean isCancelled(@NonNull String jobId) {
    return Boolean.TRUE.equals(CANCELLED.get(jobId));
  }

  /** True when any job is still live — a bare {@code cancel} with no id means "the running one". */
  public static boolean anyRunning() {
    return !CANCELLED.isEmpty();
  }

  public static void finish(@NonNull String jobId) {
    CANCELLED.remove(jobId);
  }
}
