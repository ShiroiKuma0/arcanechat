package org.thoughtcrime.securesms.automation;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Binder;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * shiroikuma fork — who is allowed through the automation data door, and how that is decided.
 *
 * <p>Ported from the family-wide {@code AutomationCallers.kt} in {@code shiroikuma-jiyusagyoban};
 * the pins are the same constants and are deliberately app-independent.
 *
 * <h3>Why not a token</h3>
 *
 * <p>The token this replaces was a 48-character secret 白い熊 pasted from one app's settings into
 * another's. It cannot survive a wipe, which is fatal for the case the whole family now exists to
 * serve: 白い熊 応用管理 restoring apps and their data onto a clean phone, where nothing is configured
 * yet.
 *
 * <h3>Why not a {@code shiroikuma.*} prefix</h3>
 *
 * <p>Because that is not an identity. What makes {@code getCallingPackage()} worth anything is that
 * a package name <b>cannot be taken while the real package is installed</b> — package names are not
 * a namespace anyone owns, so any sideloaded app may call itself {@code shiroikuma.evil} and pass a
 * prefix test. Since the caller supplies the file descriptor an export is written into, a prefix
 * check would hand such an app the complete data of every sister app in turn: strictly weaker than
 * the token it replaces.
 *
 * <h3>What is actually checked, in order</h3>
 *
 * <ol>
 *   <li><b>An exact name</b> from {@link #CALLERS}.
 *   <li><b>The uid agrees.</b> {@code getCallingPackage()} reflects the caller's declared
 *       attribution, and packages sharing a uid are not distinguished by it, so it is confirmed
 *       against the uid the kernel reports.
 *   <li><b>The signing certificate matches a pinned hash.</b> This is the one that closes the real
 *       gap: <i>whichever caller package is absent from the device is a name anyone can take</i>,
 *       and the clean-phone case this contract exists for is precisely a device where not
 *       everything is installed yet — the moment the assumption is weakest is the moment it is most
 *       needed.
 * </ol>
 */
public final class AutomationCallers {

  /**
   * The apps allowed to drive this one's data door.
   *
   * <p>応用管理 backs up and restores; 自由作業盤 runs the 保存復元 batch. Nothing else has any business
   * exporting another app's data, and an entry added here is a deliberate act.
   */
  private static final Map<String, String> CALLERS;

  static {
    Map<String, String> callers = new HashMap<>();
    callers.put(
        "shiroikuma.oyokanri",
        "9c585f4d118cb97ff653f949a8872875548403b9083ce6b9baa2e8f0c55ac6cc");
    callers.put(
        "shiroikuma.jiyusagyoban",
        "efd0d352192651593a92288ecdc64fc87262ec8648c24ed8f51a5587d46ac602");
    CALLERS = Collections.unmodifiableMap(callers);
  }

  /**
   * Where those hashes come from, so the next person can re-derive them rather than trust them:
   *
   * <pre>
   *   apksigner verify --print-certs &lt;the app's signed release APK&gt; | grep 'SHA-256 digest'
   * </pre>
   *
   * <p>Every app in the family has <b>its own keystore</b> — 42 of them under
   * {@code ~/.android-keystores/} — so there is no shared signing key to compare against and each
   * caller must be pinned by name. That is also why a {@code protectionLevel="signature"} permission
   * was never an option here.
   *
   * <p><b>If a caller's key is ever rotated, its APK stops being able to call and the fix is
   * here.</b> That is the intended failure: a signing key changing without anyone noticing is
   * exactly what a pin exists to catch.
   */
  private AutomationCallers() {}

  /**
   * The verdict, as a string rather than a boolean.
   *
   * <p>A refusal that says only "no" is a refusal nobody can debug from the other side of an IPC
   * boundary. Each refusal below is a different mistake with a different fix, and the caller shows
   * it to 白い熊 verbatim.
   *
   * @return {@code null} when the caller is allowed, otherwise the {@code ERROR:} line to answer.
   */
  @Nullable
  public static String verify(@NonNull Context context, @Nullable String declared) {
    if (declared == null || declared.isEmpty()) return "ERROR:caller unknown";
    String pin = CALLERS.get(declared);
    if (pin == null) return "ERROR:caller not permitted: " + declared;

    // The kernel's answer, not the caller's. A package may declare an attribution it does not own;
    // the uid cannot be borrowed.
    String[] real;
    try {
      real = context.getPackageManager().getPackagesForUid(Binder.getCallingUid());
    } catch (Throwable t) {
      real = null;
    }
    if (real == null || !Arrays.asList(real).contains(declared)) {
      return "ERROR:caller uid mismatch: " + declared;
    }

    String signature = signingSha256(context, declared);
    if (signature == null) return "ERROR:caller signature unreadable: " + declared;
    // Constant-time, like the token compare it replaces - the value is a public hash, but the habit
    // is worth keeping and costs nothing.
    if (!MessageDigest.isEqual(signature.getBytes(), pin.getBytes())) {
      return "ERROR:caller signature mismatch: " + declared;
    }
    return null;
  }

  /**
   * The SHA-256 of the caller's current signing certificate, lower-case hex.
   *
   * <p>{@code signingInfo} rather than the deprecated {@code signatures}: a rotated key reports its
   * whole history and we want the certificate actually in force. An app with more than one current
   * signer is refused by returning null — our apps have exactly one, and "several signers, one of
   * which matches" is a question about key rotation that nothing in this family needs to answer.
   *
   * <p>The pre-P branch is not a compromise but the correct answer there: {@code signingInfo} and
   * {@code GET_SIGNING_CERTIFICATES} are API 28, this fork's {@code minSdkVersion} is 21, and on an
   * older device the flag is accepted while {@code signingInfo} comes back null — without the
   * branch the door would refuse <b>every</b> caller, a total failure that never appears on 白い熊's
   * phone (API 31) and would only surface on an older one. Before key rotation existed,
   * {@code signatures} <i>was</i> the signing certificate.
   */
  @Nullable
  private static String signingSha256(@NonNull Context context, @NonNull String pkg) {
    try {
      PackageManager pm = context.getPackageManager();
      Signature[] certs;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        SigningInfo info =
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo;
        certs = info == null ? null : info.getApkContentsSigners();
      } else {
        @SuppressWarnings("deprecation")
        Signature[] legacy = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures;
        certs = legacy;
      }
      // Exactly one signer, or we decline to guess.
      if (certs == null || certs.length != 1) return null;
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(certs[0].toByteArray());
      StringBuilder sb = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
      }
      return sb.toString();
    } catch (Throwable t) {
      return null;
    }
  }

  /** The caller names this door knows about — for logging and diagnostics only. */
  @NonNull
  public static List<String> known() {
    return Collections.unmodifiableList(new java.util.ArrayList<>(CALLERS.keySet()));
  }
}
