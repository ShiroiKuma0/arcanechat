package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;
import chat.delta.rpc.Rpc;
import com.b44t.messenger.DcAccounts;
import com.b44t.messenger.DcContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.connect.DcHelper;

/**
 * shiroikuma fork: Export/Import engine for all app settings, mirroring the Kōjiki fork's model —
 * a zip of one JSON file per category plus a manifest, written to a user-chosen SAF directory.
 * Every SharedPreferences key round-trips with a type tag ({@code {"t":..,"v":..}}), and import is
 * a per-key MERGE (never a clear), so unknown/missing keys are simply left alone — old exports
 * load into new app versions and vice versa.
 */
public final class ShiroikumaExport {

  public static final String FORMAT = "arcanechat-export";
  public static final int VERSION = 1;

  /**
   * Family-wide backup-name convention (白い熊, 2026-07-25): every sister app writes
   * {@code <english-dash-separated-app-name>_<yyyy-MM-dd_HH-mm-ss>.zip} — no version, no infix, no
   * suffix — so all apps' backups sort and read uniformly in one directory.
   */
  public static final String EXPORT_PREFIX = "shiroikuma-arcanechat_";

  /** Pre-convention name ({@code …-<version>-export_<stamp>.zip}); still recognised on read. */
  public static final String LEGACY_EXPORT_PREFIX = "shiroikuma-arcanechat-";

  /** Device-local prefs holding the export-directory URI; deliberately never exported. */
  public static final String EXIMPORT_PREFS = "shiroikuma_eximport";
  public static final String KEY_DIR_URI = "dir_uri";

  /**
   * One export at a time, process-wide: the UI panel and the automation receiver share the temp
   * directory account tars are staged in, so a second concurrent run would delete the first one's
   * files. Fails fast rather than queueing, so the caller can report it.
   */
  private static final java.util.concurrent.atomic.AtomicBoolean EXPORT_RUNNING =
      new java.util.concurrent.atomic.AtomicBoolean(false);

  /**
   * Keys that must not travel between installs (device/session-local state). The export directory
   * and the automation switch/token live in their own prefs files ({@link #EXIMPORT_PREFS} /
   * {@code AutomationAuth.PREFS}), which this engine never reads — so the token can never end up
   * inside a backup zip.
   */
  private static final Set<String> APP_EXCLUDE = new HashSet<>();

  static {
    APP_EXCLUDE.add("ndk_arch_warned");
  }

  /** The selectable categories; {@code id} doubles as the JSON entry name inside the zip. */
  public enum Cat {
    // declaration order is dialog order - accounts first
    ACCOUNTS("accounts", R.string.eim_cat_accounts),
    UI("ui", R.string.eim_cat_ui),
    PROTECTED("protected_contacts", R.string.eim_cat_protected),
    APP("app_settings", R.string.eim_cat_app);

    public final String id;
    @StringRes public final int labelRes;

    Cat(String id, @StringRes int labelRes) {
      this.id = id;
      this.labelRes = labelRes;
    }
  }

  /**
   * Progress sink for a long export. 白い熊's rule for the automation contract: report real counts,
   * never a percentage — {@code text} is the display line, {@code current}/{@code total}/{@code
   * unit} the structured form of the same numbers.
   */
  public interface ProgressListener {
    void onProgress(@NonNull String text, long current, long total, @NonNull String unit);
  }

  /**
   * A configured account, offered to automation as the {@code accounts.<accountId>} sub-option of
   * the Accounts category so a batch can back up one profile instead of all of them.
   */
  public static final class AccountEntry {
    public final int accountId;
    public final String label;

    AccountEntry(int accountId, String label) {
      this.accountId = accountId;
      this.label = label;
    }

    /** The id accepted in the automation {@code items} extra. */
    public String itemId() {
      return Cat.ACCOUNTS.id + "." + accountId;
    }
  }

  private ShiroikumaExport() {}

  /** The configured accounts, in core order; unconfigured ones have nothing to back up. */
  @NonNull
  public static List<AccountEntry> listAccounts(@NonNull Context context) {
    List<AccountEntry> out = new ArrayList<>();
    DcAccounts accounts = DcHelper.getAccounts(context);
    for (int accountId : accounts.getAll()) {
      DcContext acc = accounts.getAccount(accountId);
      if (acc.isConfigured() == 0) continue;
      out.add(new AccountEntry(accountId, accountLabel(acc, accountId)));
    }
    return out;
  }

  private static String accountLabel(DcContext acc, int accountId) {
    String addr = acc.getConfig("addr");
    String name = acc.getConfig("displayname");
    boolean hasAddr = addr != null && !addr.isEmpty();
    boolean hasName = name != null && !name.isEmpty();
    if (hasName && hasAddr && !name.equals(addr)) return name + " (" + addr + ")";
    if (hasAddr) return addr;
    if (hasName) return name;
    return "#" + accountId;
  }

  /** The category label without its parenthesised detail — for one-line progress text. */
  @NonNull
  public static String shortLabel(@NonNull Context context, @NonNull Cat cat) {
    String label = context.getString(cat.labelRes);
    int cut = label.indexOf(" (");
    return cut > 0 ? label.substring(0, cut) : label;
  }

  // --- category membership ------------------------------------------------------------------

  private static boolean isUiKey(String k) {
    return k.startsWith("pref_color_")
        || k.startsWith("pref_font_")
        || k.startsWith("pref_ticks_") // Step 11: delivery-tick size, colours, glyphs, switches
        || k.equals(Prefs.ACCENT_PREF)
        || k.equals(Prefs.CHATLIST_STYLE_PREF);
  }

  private static boolean isProtectedKey(String k) {
    return k.startsWith("pref_protected_");
  }

  private static boolean inCategory(Cat cat, String key) {
    switch (cat) {
      case ACCOUNTS:
        return false; // not prefs-based - handled by exportAccounts()/importAccounts()
      case UI:
        return isUiKey(key);
      case PROTECTED:
        return isProtectedKey(key);
      case APP:
      default:
        return !isUiKey(key) && !isProtectedKey(key) && !APP_EXCLUDE.contains(key);
    }
  }

  // --- export -------------------------------------------------------------------------------

  public static String exportFileName() {
    return EXPORT_PREFIX
        + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(new Date())
        + ".zip";
  }

  /** Everything, every account, no progress — the plain UI-panel export. */
  public static void export(
      @NonNull Context context, @NonNull List<Cat> cats, @NonNull OutputStream out)
      throws Exception {
    export(context, cats, null, out, null);
  }

  /**
   * Streams the export zip for the given categories to {@code out} (account backups can be far too
   * large for an in-memory build). The caller owns the stream and should delete the target file if
   * this throws midway.
   *
   * @param accountIds when non-null, only these accounts are included in the Accounts category
   *     (the {@code accounts.<id>} sub-options); null means every configured account.
   * @param progress optional sink for real counts while the export runs.
   */
  public static void export(
      @NonNull Context context,
      @NonNull List<Cat> cats,
      @Nullable Set<Integer> accountIds,
      @NonNull OutputStream out,
      @Nullable ProgressListener progress)
      throws Exception {
    if (!EXPORT_RUNNING.compareAndSet(false, true)) {
      throw new IllegalStateException("export already running");
    }
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      JSONArray catIds = new JSONArray();
      for (Cat c : cats) catIds.put(c.id);
      JSONObject manifest =
          new JSONObject()
              .put("format", FORMAT)
              .put("version", VERSION)
              .put("app", context.getPackageName())
              .put("appVersion", BuildConfig.VERSION_NAME)
              .put("createdTs", System.currentTimeMillis())
              .put("categories", catIds);
      writeEntry(zip, "manifest.json", manifest.toString(2));

      SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
      int total = cats.size();
      int n = 0;
      for (Cat cat : cats) {
        n++;
        if (progress != null) {
          progress.onProgress(
              context.getString(R.string.eim_progress_category, n, total, shortLabel(context, cat)),
              n,
              total,
              context.getString(R.string.eim_progress_unit_category));
        }
        if (cat == Cat.ACCOUNTS) {
          exportAccounts(context, zip, accountIds, progress);
        } else {
          writeEntry(zip, cat.id + ".json", exportPrefs(sp, cat));
        }
      }
      if (progress != null) {
        progress.onProgress(
            context.getString(R.string.eim_progress_done, total),
            total,
            total,
            context.getString(R.string.eim_progress_unit_category));
      }
    } finally {
      EXPORT_RUNNING.set(false);
    }
  }

  /**
   * One core backup tar per configured account, via the blocking JSON-RPC {@code export_backup}
   * (unencrypted, like the in-app backup), plus an {@code accounts.json} index whose presence marks
   * the category in the zip. Unconfigured accounts have nothing to back up and are skipped.
   */
  private static void exportAccounts(
      Context context,
      ZipOutputStream zip,
      @Nullable Set<Integer> accountIds,
      @Nullable ProgressListener progress)
      throws Exception {
    DcAccounts accounts = DcHelper.getAccounts(context);
    Rpc rpc = DcHelper.getRpc(context);
    File tmpRoot = new File(context.getCacheDir(), "shiroikuma-eximport");
    deleteRecursive(tmpRoot);
    JSONArray index = new JSONArray();
    List<AccountEntry> selected = new ArrayList<>();
    for (AccountEntry entry : listAccounts(context)) {
      if (accountIds == null || accountIds.contains(entry.accountId)) selected.add(entry);
    }
    try {
      int n = 0;
      for (AccountEntry entry : selected) {
        int accountId = entry.accountId;
        DcContext acc = accounts.getAccount(accountId);
        if (progress != null) {
          progress.onProgress(
              context.getString(
                  R.string.eim_progress_account, n + 1, selected.size(), entry.label),
              n + 1,
              selected.size(),
              context.getString(R.string.eim_progress_unit_account));
        }
        // a per-account empty dir makes the produced tar unambiguous
        File dir = new File(tmpRoot, "acc-" + accountId);
        if (!dir.mkdirs()) throw new IllegalStateException("cannot create " + dir);
        rpc.exportBackup(accountId, dir.getAbsolutePath(), null);
        File[] produced = dir.listFiles();
        if (produced == null || produced.length == 0) {
          throw new IllegalStateException("no backup produced for account " + accountId);
        }
        String entryName = "accounts/" + (++n) + ".tar";
        index.put(
            new JSONObject()
                .put("addr", String.valueOf(acc.getConfig("addr")))
                .put("name", String.valueOf(acc.getConfig("displayname")))
                .put("tar", entryName));
        zip.putNextEntry(new ZipEntry(entryName));
        try (InputStream in = new FileInputStream(produced[0])) {
          copy(in, zip);
        }
        zip.closeEntry();
        deleteRecursive(dir); // free the temp tar before the next account
      }
    } finally {
      deleteRecursive(tmpRoot);
    }
    writeEntry(zip, Cat.ACCOUNTS.id + ".json", new JSONObject().put("accounts", index).toString(2));
  }

  private static void copy(InputStream in, OutputStream out) throws Exception {
    byte[] buf = new byte[65536];
    int n;
    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
  }

  private static void deleteRecursive(File f) {
    File[] children = f.listFiles();
    if (children != null) for (File c : children) deleteRecursive(c);
    //noinspection ResultOfMethodCallIgnored
    f.delete();
  }

  private static void writeEntry(ZipOutputStream zip, String name, String content)
      throws Exception {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(content.getBytes("UTF-8"));
    zip.closeEntry();
  }

  /** Every matching key with a type tag: {"t":"b|i|l|f|s|ss","v":...}. */
  private static String exportPrefs(SharedPreferences sp, Cat cat) throws Exception {
    JSONObject obj = new JSONObject();
    for (Map.Entry<String, ?> entry : sp.getAll().entrySet()) {
      String k = entry.getKey();
      Object v = entry.getValue();
      if (!inCategory(cat, k)) continue;
      JSONObject e = new JSONObject();
      if (v instanceof Boolean) {
        e.put("t", "b").put("v", v);
      } else if (v instanceof Integer) {
        e.put("t", "i").put("v", v);
      } else if (v instanceof Long) {
        e.put("t", "l").put("v", v);
      } else if (v instanceof Float) {
        e.put("t", "f").put("v", ((Float) v).doubleValue());
      } else if (v instanceof String) {
        e.put("t", "s").put("v", v);
      } else if (v instanceof Set) {
        JSONArray a = new JSONArray();
        for (Object s : (Set<?>) v) a.put(String.valueOf(s));
        e.put("t", "ss").put("v", a);
      } else {
        continue;
      }
      obj.put(k, e);
    }
    return obj.toString(2);
  }

  // --- inspect ------------------------------------------------------------------------------

  /** The categories present in an export zip; empty if it isn't one of ours. */
  @NonNull
  public static List<Cat> categoriesIn(@NonNull ZipFile zip) {
    List<Cat> out = new ArrayList<>();
    try {
      String manifest = readEntry(zip, "manifest.json");
      if (manifest == null) return out;
      JSONObject m = new JSONObject(manifest);
      if (!FORMAT.equals(m.optString("format"))) return out;
      for (Cat c : Cat.values()) {
        if (zip.getEntry(c.id + ".json") != null) out.add(c);
      }
    } catch (Exception ignored) {
    }
    return out;
  }

  // --- import -------------------------------------------------------------------------------

  /**
   * Applies the selected categories from an export zip (backed by a file so account tars stream
   * rather than load into memory); categories missing from the zip are skipped. Returns a
   * human-readable per-category summary, or null when the file carried none.
   */
  @Nullable
  public static String importData(
      @NonNull Context context, @NonNull ZipFile zip, @NonNull List<Cat> cats) throws Exception {
    if (zip.getEntry("manifest.json") == null) return null;

    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
    StringBuilder summary = new StringBuilder();
    boolean any = false;
    for (Cat cat : cats) {
      String data = readEntry(zip, cat.id + ".json");
      if (data == null) continue;
      String line;
      if (cat == Cat.ACCOUNTS) {
        line = importAccounts(context, zip, data);
      } else {
        line = String.valueOf(importPrefs(sp, data, cat));
      }
      any = true;
      if (summary.length() > 0) summary.append('\n');
      summary.append(context.getString(cat.labelRes)).append(": ").append(line);
    }
    return any ? summary.toString() : null;
  }

  /**
   * Restores each backup tar into a freshly created account via the blocking JSON-RPC
   * {@code import_backup}. Merge semantics like the prefs categories: an address that already has a
   * local account is skipped, existing accounts are never touched, and a failed restore removes its
   * half-created account and continues with the rest. Returns the summary line for the category.
   */
  private static String importAccounts(Context context, ZipFile zip, String indexJson)
      throws Exception {
    DcAccounts accounts = DcHelper.getAccounts(context);
    Rpc rpc = DcHelper.getRpc(context);

    Set<String> existing = new HashSet<>();
    for (int accountId : accounts.getAll()) {
      String addr = accounts.getAccount(accountId).getConfig("addr");
      if (addr != null && !addr.isEmpty()) existing.add(addr.toLowerCase(Locale.ROOT));
    }
    int selectedBefore = accounts.getSelectedAccount().getAccountId();

    JSONArray index = new JSONObject(indexJson).optJSONArray("accounts");
    int imported = 0;
    int skipped = 0;
    int failed = 0;
    try {
      for (int i = 0; index != null && i < index.length(); i++) {
        JSONObject item = index.getJSONObject(i);
        String addr = item.optString("addr");
        if (!addr.isEmpty() && existing.contains(addr.toLowerCase(Locale.ROOT))) {
          skipped++;
          continue;
        }
        ZipEntry entry = zip.getEntry(item.getString("tar"));
        if (entry == null) {
          failed++;
          continue;
        }
        File tmp = File.createTempFile("shiroikuma-eximport", ".tar", context.getCacheDir());
        try {
          try (InputStream in = zip.getInputStream(entry);
              OutputStream out = new FileOutputStream(tmp)) {
            copy(in, out);
          }
          int newId = rpc.addAccount();
          try {
            rpc.importBackup(newId, tmp.getAbsolutePath(), null);
            imported++;
            if (!addr.isEmpty()) existing.add(addr.toLowerCase(Locale.ROOT));
          } catch (Exception e) {
            accounts.removeAccount(newId);
            failed++;
          }
        } finally {
          //noinspection ResultOfMethodCallIgnored
          tmp.delete();
        }
      }
    } finally {
      // addAccount() selects the new account - put the user's selection back
      accounts.selectAccount(selectedBefore);
      if (imported > 0) accounts.startIo();
    }

    String line = context.getString(R.string.eim_accounts_result, imported, skipped);
    if (failed > 0) line += context.getString(R.string.eim_accounts_failed, failed);
    return line;
  }

  @Nullable
  private static String readEntry(ZipFile zip, String name) throws Exception {
    ZipEntry entry = zip.getEntry(name);
    if (entry == null) return null;
    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
    try (InputStream in = zip.getInputStream(entry)) {
      copy(in, bos);
    }
    return bos.toString("UTF-8");
  }

  /** Per-key merge — never clears, so unrelated/device-local keys survive. Returns keys applied. */
  private static int importPrefs(SharedPreferences sp, String json, Cat cat) throws Exception {
    JSONObject obj = new JSONObject(json);
    SharedPreferences.Editor ed = sp.edit();
    int count = 0;
    for (java.util.Iterator<String> it = obj.keys(); it.hasNext(); ) {
      String k = it.next();
      if (!inCategory(cat, k)) continue; // don't let one category's file smuggle another's keys
      JSONObject e = obj.optJSONObject(k);
      if (e == null) continue;
      switch (e.optString("t")) {
        case "b":
          ed.putBoolean(k, e.optBoolean("v"));
          break;
        case "i":
          ed.putInt(k, e.optInt("v"));
          break;
        case "l":
          ed.putLong(k, e.optLong("v"));
          break;
        case "f":
          ed.putFloat(k, (float) e.optDouble("v"));
          break;
        case "s":
          ed.putString(k, e.optString("v"));
          break;
        case "ss":
          JSONArray a = e.optJSONArray("v");
          Set<String> set = new HashSet<>();
          if (a != null) for (int i = 0; i < a.length(); i++) set.add(a.optString(i));
          ed.putStringSet(k, set);
          break;
        default:
          continue;
      }
      count++;
    }
    ed.apply();
    return count;
  }

  // --- export directory + latest-export probe ----------------------------------------------

  private static SharedPreferences eximportPrefs(Context context) {
    return context.getSharedPreferences(EXIMPORT_PREFS, Context.MODE_PRIVATE);
  }

  @Nullable
  public static Uri getDirUri(Context context) {
    String raw = eximportPrefs(context).getString(KEY_DIR_URI, null);
    if (raw == null) return null;
    try {
      return Uri.parse(raw);
    } catch (Exception e) {
      return null;
    }
  }

  public static void setDirUri(Context context, @NonNull Uri uri) {
    eximportPrefs(context).edit().putString(KEY_DIR_URI, uri.toString()).apply();
  }

  @Nullable
  public static DocumentFile getExportDir(Context context) {
    Uri uri = getDirUri(context);
    if (uri == null) return null;
    try {
      DocumentFile dir = DocumentFile.fromTreeUri(context, uri);
      return (dir != null && dir.isDirectory()) ? dir : null;
    } catch (Exception e) {
      return null;
    }
  }

  /** The newest export zip in the configured directory, or null. */
  @Nullable
  public static DocumentFile latestExport(Context context) {
    DocumentFile dir = getExportDir(context);
    if (dir == null) return null;
    try {
      DocumentFile newest = null;
      for (DocumentFile f : dir.listFiles()) {
        String name = f.getName();
        if (!f.isFile() || name == null) continue;
        if (!name.endsWith(".zip")) continue;
        // the pre-convention name is still recognised, so older backups keep counting as "latest"
        if (!name.startsWith(EXPORT_PREFIX) && !name.startsWith(LEGACY_EXPORT_PREFIX)) continue;
        if (newest == null || f.lastModified() > newest.lastModified()) newest = f;
      }
      return newest;
    } catch (Exception e) {
      return null;
    }
  }

  /** The "last export" status line for the given context — never blocks on user interaction. */
  @NonNull
  public static String lastExportStatus(Context context) {
    if (getExportDir(context) == null) return context.getString(R.string.eim_warn_nodir);
    DocumentFile newest = latestExport(context);
    if (newest == null) return context.getString(R.string.eim_warn_none);
    String ts =
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
            .format(new Date(newest.lastModified()));
    return context.getString(R.string.eim_last_export, ts);
  }
}
