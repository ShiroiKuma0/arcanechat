package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.util.TypedValue;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * shiroikuma fork (Step 3): resolves and applies a configurable font (family + weight + size) to a
 * TextView. Mirrors the per-surface model used for colours.
 *
 * Family values: "" = keep the view's current family; a system family name (e.g. "serif"); or
 * "file:<absolute path>" for a font read in place from the user's custom fonts folder (no copy).
 * Weight is a real per-weight axis on API 28+, BOLD/NORMAL fallback below. Size is sp; 0 = default.
 *
 * Fonts are read directly from the folder configured in Prefs (FONT_FOLDER_PREF); reading arbitrary
 * files there needs All-files access on API 30+ (handled in the settings UI).
 */
public final class FontUtil {

  private FontUtil() {}

  public static final String FILE_PREFIX = "file:";

  public static final String[] BUILTIN_FAMILY_VALUES = {
    "", "sans-serif", "sans-serif-light", "sans-serif-medium", "sans-serif-condensed",
    "sans-serif-thin", "sans-serif-black", "serif", "monospace", "cursive"
  };
  public static final String[] BUILTIN_FAMILY_LABELS = {
    "Default", "Sans Serif", "Sans Serif Light", "Sans Serif Medium", "Sans Serif Condensed",
    "Sans Serif Thin", "Sans Serif Black", "Serif", "Monospace", "Cursive"
  };

  public static final int[] WEIGHT_VALUES = {0, 100, 300, 400, 500, 600, 700, 900};
  public static final String[] WEIGHT_LABELS = {
    "Default", "Thin", "Light", "Normal", "Medium", "Semi-Bold", "Bold", "Black"
  };

  public static final int SIZE_MAX = 96;       // SeekBar upper bound (sp)
  public static final int SIZE_HARD_CAP = 300; // typed upper bound (sp) - "even bigger if desired"

  private static final Map<String, Typeface> fileCache = new HashMap<>();

  /** "file:<absolute path>" values for every existing user-picked custom font file. */
  public static List<String> externalValues(Context c) {
    List<String> out = new ArrayList<>();
    for (String path : Prefs.getFontFiles(c)) {
      File f = new File(path);
      String n = path.toLowerCase();
      if (f.isFile() && (n.endsWith(".ttf") || n.endsWith(".otf"))) {
        out.add(FILE_PREFIX + f.getAbsolutePath());
      }
    }
    return out;
  }

  /** Fallback when a picked URI can't be resolved to a real path: copy into filesDir/fonts. */
  public static String copyToInternal(Context c, android.net.Uri uri, String displayName) {
    String name = (displayName == null || displayName.isEmpty())
        ? ("font_" + System.currentTimeMillis() + ".ttf")
        : displayName.replaceAll("[^A-Za-z0-9._-]", "_");
    String lower = name.toLowerCase();
    if (!lower.endsWith(".ttf") && !lower.endsWith(".otf")) name = name + ".ttf";
    File dir = new File(c.getFilesDir(), "fonts");
    if (!dir.exists()) dir.mkdirs();
    File out = new File(dir, name);
    try (java.io.InputStream in = c.getContentResolver().openInputStream(uri);
        java.io.OutputStream os = new java.io.FileOutputStream(out)) {
      if (in == null) return null;
      byte[] buf = new byte[8192];
      int r;
      while ((r = in.read(buf)) > 0) os.write(buf, 0, r);
    } catch (Exception e) {
      return null;
    }
    return out.getAbsolutePath();
  }

  /** (value,label) pairs for every family: builtins + picked files, sorted with Default first. */
  private static List<String[]> sortedEntries(Context c) {
    List<String[]> e = new ArrayList<>();
    for (int i = 0; i < BUILTIN_FAMILY_VALUES.length; i++) {
      e.add(new String[] {BUILTIN_FAMILY_VALUES[i], BUILTIN_FAMILY_LABELS[i]});
    }
    for (String v : externalValues(c)) e.add(new String[] {v, fileLabel(v)});
    e.sort(
        (a, b) -> {
          if (a[0].isEmpty()) return -1; // "Default" always first
          if (b[0].isEmpty()) return 1;
          return a[1].compareToIgnoreCase(b[1]);
        });
    return e;
  }

  public static String[] allFamilyValues(Context c) {
    List<String[]> e = sortedEntries(c);
    String[] r = new String[e.size()];
    for (int i = 0; i < e.size(); i++) r[i] = e.get(i)[0];
    return r;
  }

  public static String[] allFamilyLabels(Context c) {
    List<String[]> e = sortedEntries(c);
    String[] r = new String[e.size()];
    for (int i = 0; i < e.size(); i++) r[i] = e.get(i)[1];
    return r;
  }

  public static int indexOf(String[] arr, String value) {
    for (int i = 0; i < arr.length; i++) if (arr[i].equals(value)) return i;
    return 0;
  }

  public static int weightIndex(int value) {
    for (int i = 0; i < WEIGHT_VALUES.length; i++) if (WEIGHT_VALUES[i] == value) return i;
    return 0;
  }

  public static Typeface resolveTypeface(Typeface current, String family, int weight) {
    Typeface base;
    if (family == null || family.isEmpty()) {
      base = current;
    } else if (family.startsWith(FILE_PREFIX)) {
      base = loadFile(family);
      if (base == null) base = current; // folder/file went missing -> keep current
    } else {
      base = Typeface.create(family, Typeface.NORMAL);
    }
    if (weight > 0) {
      Typeface b = (base == null) ? Typeface.DEFAULT : base;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        return Typeface.create(b, weight, false);
      }
      return Typeface.create(b, weight >= 600 ? Typeface.BOLD : Typeface.NORMAL);
    }
    return base;
  }

  public static void apply(TextView tv, String family, int weight, int sizeSp) {
    Typeface tf = resolveTypeface(tv.getTypeface(), family, weight);
    if (tf != null) tv.setTypeface(tf);
    if (sizeSp > 0) tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
  }

  private static Typeface loadFile(String family) {
    Typeface t = fileCache.get(family);
    if (t != null) return t;
    File f = new File(family.substring(FILE_PREFIX.length()));
    if (!f.exists()) return null;
    try {
      t = Typeface.createFromFile(f);
      fileCache.put(family, t);
      return t;
    } catch (Exception e) {
      return null;
    }
  }

  /** Call after the fonts folder changes so newly visible files are (re)loaded. */
  public static void clearCache() {
    fileCache.clear();
  }

  private static String fileLabel(String value) {
    String n = value.substring(FILE_PREFIX.length());
    int slash = n.lastIndexOf('/');
    if (slash >= 0) n = n.substring(slash + 1);
    int dot = n.lastIndexOf('.');
    return dot > 0 ? n.substring(0, dot) : n;
  }

  public static String describe(String family, int weight, int sizeSp) {
    StringBuilder sb = new StringBuilder();
    if (family != null && !family.isEmpty()) {
      sb.append(family.startsWith(FILE_PREFIX) ? fileLabel(family) : labelFor(family));
    }
    if (weight > 0) {
      if (sb.length() > 0) sb.append(" \u00b7 ");
      sb.append(WEIGHT_LABELS[weightIndex(weight)]);
    }
    if (sizeSp > 0) {
      if (sb.length() > 0) sb.append(" \u00b7 ");
      sb.append(sizeSp).append("sp");
    }
    return sb.length() == 0 ? "Default" : sb.toString();
  }

  private static String labelFor(String value) {
    for (int i = 0; i < BUILTIN_FAMILY_VALUES.length; i++) {
      if (BUILTIN_FAMILY_VALUES[i].equals(value)) return BUILTIN_FAMILY_LABELS[i];
    }
    return value;
  }
}
