package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.R;

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
  public static final String EXPORT_PREFIX = "shiroikuma-arcanechat-";

  /** Device-local prefs holding the export-directory URI; deliberately never exported. */
  public static final String EXIMPORT_PREFS = "shiroikuma_eximport";
  public static final String KEY_DIR_URI = "dir_uri";

  /** Keys that must not travel between installs (device/session-local state). */
  private static final Set<String> APP_EXCLUDE = new HashSet<>();

  static {
    APP_EXCLUDE.add("ndk_arch_warned");
  }

  /** The selectable categories; {@code id} doubles as the JSON entry name inside the zip. */
  public enum Cat {
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

  private ShiroikumaExport() {}

  // --- category membership ------------------------------------------------------------------

  private static boolean isUiKey(String k) {
    return k.startsWith("pref_color_")
        || k.startsWith("pref_font_")
        || k.equals(Prefs.ACCENT_PREF)
        || k.equals(Prefs.CHATLIST_STYLE_PREF);
  }

  private static boolean isProtectedKey(String k) {
    return k.startsWith("pref_protected_");
  }

  private static boolean inCategory(Cat cat, String key) {
    switch (cat) {
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
        + BuildConfig.VERSION_NAME
        + "-export_"
        + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(new Date())
        + ".zip";
  }

  /** Builds the export zip for the given categories. */
  public static byte[] export(@NonNull Context context, @NonNull List<Cat> cats) throws Exception {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bos)) {
      JSONArray catIds = new JSONArray();
      for (Cat c : cats) catIds.put(c.id);
      JSONObject manifest =
          new JSONObject()
              .put("format", FORMAT)
              .put("version", VERSION)
              .put("app", context.getPackageName())
              .put("createdTs", System.currentTimeMillis())
              .put("categories", catIds);
      writeEntry(zip, "manifest.json", manifest.toString(2));

      SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
      for (Cat cat : cats) {
        writeEntry(zip, cat.id + ".json", exportPrefs(sp, cat));
      }
    }
    return bos.toByteArray();
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
  public static List<Cat> categoriesIn(@NonNull byte[] bytes) {
    List<Cat> out = new ArrayList<>();
    try {
      Map<String, byte[]> files = readZip(bytes);
      byte[] manifest = files.get("manifest.json");
      if (manifest == null) return out;
      JSONObject m = new JSONObject(new String(manifest, "UTF-8"));
      if (!FORMAT.equals(m.optString("format"))) return out;
      for (Cat c : Cat.values()) {
        if (files.containsKey(c.id + ".json")) out.add(c);
      }
    } catch (Exception ignored) {
    }
    return out;
  }

  // --- import -------------------------------------------------------------------------------

  /**
   * Applies the selected categories from an export zip; categories missing from the zip are
   * skipped. Returns a human-readable per-category summary, or null when the file carried none.
   */
  @Nullable
  public static String importData(
      @NonNull Context context, @NonNull byte[] bytes, @NonNull List<Cat> cats) throws Exception {
    Map<String, byte[]> files = readZip(bytes);
    if (files.get("manifest.json") == null) return null;

    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
    StringBuilder summary = new StringBuilder();
    boolean any = false;
    for (Cat cat : cats) {
      byte[] data = files.get(cat.id + ".json");
      if (data == null) continue;
      int n = importPrefs(sp, new String(data, "UTF-8"), cat);
      any = true;
      if (summary.length() > 0) summary.append('\n');
      summary.append(context.getString(cat.labelRes)).append(": ").append(n);
    }
    return any ? summary.toString() : null;
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

  private static Map<String, byte[]> readZip(byte[] bytes) throws Exception {
    Map<String, byte[]> out = new HashMap<>();
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      ZipEntry entry;
      byte[] buf = new byte[8192];
      while ((entry = zip.getNextEntry()) != null) {
        if (entry.isDirectory()) continue;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int n;
        while ((n = zip.read(buf)) > 0) bos.write(buf, 0, n);
        out.put(entry.getName(), bos.toByteArray());
      }
    }
    return out;
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
        if (!name.startsWith(EXPORT_PREFIX) || !name.endsWith(".zip")) continue;
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
