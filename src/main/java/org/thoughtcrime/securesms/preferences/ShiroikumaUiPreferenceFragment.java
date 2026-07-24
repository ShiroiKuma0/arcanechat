package org.thoughtcrime.securesms.preferences;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ColorPickerDialog;
import org.thoughtcrime.securesms.components.FontPickerDialog;
import org.thoughtcrime.securesms.util.FontUtil;
import org.thoughtcrime.securesms.util.Prefs;
import org.thoughtcrime.securesms.util.ResUtil;
import org.thoughtcrime.securesms.util.ShiroikumaExport;
import org.thoughtcrime.securesms.util.Util;

/**
 * shiroikuma fork (Step 10): the consolidated "白い熊 ArcaneChat UI" page. Holds every customization
 * that used to live under Settings → Appearance (colours, fonts, accent preset, chat-list row style)
 * plus the Step-10 chat-list date colour/font and a reset-to-defaults action.
 *
 * <p>Extends {@link CorrectedPreferenceFragment} so the Step-3 settings-font walker applies here too;
 * it does not depend on {@code ApplicationPreferencesActivity} (the page has its own host activity).
 */
public class ShiroikumaUiPreferenceFragment extends CorrectedPreferenceFragment {

  // Pick a .ttf/.otf file directly and reference it in place (no copy) by its real path; loading
  // arbitrary paths needs All-files access on API 30+. After a grant returns we proceed to the picker.
  private final ActivityResultLauncher<Intent> allFilesLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            if (hasStoragePermission()) launchFontPicker();
            else Toast.makeText(getContext(), "Storage access not granted", Toast.LENGTH_SHORT).show();
          });
  private final ActivityResultLauncher<String> readPermLauncher =
      registerForActivityResult(
          new ActivityResultContracts.RequestPermission(),
          granted -> {
            if (granted) launchFontPicker();
            else Toast.makeText(getContext(), "Storage permission denied", Toast.LENGTH_SHORT).show();
          });
  private final ActivityResultLauncher<String[]> fontPickLauncher =
      registerForActivityResult(
          new ActivityResultContracts.OpenDocument(),
          uri -> {
            if (uri != null) onFontPicked(uri);
          });

  // shiroikuma fork: Export/Import (Kōjiki-style). SAF folder picker for the export directory,
  // save-as fallback when no directory is set, and the import file picker.
  private static final int EIM_WARN_COLOR = 0xFFFF5252;
  private final ActivityResultLauncher<Uri> eimDirPickerLauncher =
      registerForActivityResult(
          new ActivityResultContracts.OpenDocumentTree(),
          uri -> {
            if (uri != null) onEximportDirPicked(uri);
          });
  private final ActivityResultLauncher<String> eimSaveAsLauncher =
      registerForActivityResult(
          new ActivityResultContracts.CreateDocument("application/zip"),
          uri -> {
            if (uri != null) writePendingExportTo(uri);
          });
  private final ActivityResultLauncher<String[]> eimImportLauncher =
      registerForActivityResult(
          new ActivityResultContracts.OpenDocument(),
          uri -> {
            if (uri != null) onEximportFilePicked(uri);
          });
  private byte[] pendingExportBytes;
  private List<ShiroikumaExport.Cat> pendingImportCats;
  @Nullable private TextView eimFolderTv;
  @Nullable private TextView eimStatusTv;

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_shiroikuma_ui);
  }

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);

    // chat-list row style - saved here, applied at bind time by ConversationListItem; the list
    // repaints on return via ConversationListFragment.onResume.
    ListPreference chatListStylePref = (ListPreference) findPreference(Prefs.CHATLIST_STYLE_PREF);
    if (chatListStylePref != null) {
      chatListStylePref.setValue(Prefs.getChatListStyle(getContext()));
      chatListStylePref.setSummary(chatListStylePref.getEntry());
      chatListStylePref.setOnPreferenceChangeListener(
          (p, value) -> {
            Prefs.setChatListStyle(getContext(), (String) value);
            int idx = chatListStylePref.findIndexOfValue((String) value);
            if (idx >= 0) p.setSummary(chatListStylePref.getEntries()[idx]);
            return false;
          });
    }

    // accent preset picker - applies on recreate via DynamicTheme.
    ListPreference accentPref = (ListPreference) findPreference(Prefs.ACCENT_PREF);
    if (accentPref != null) {
      accentPref.setValue(Prefs.getAccent(getContext()));
      accentPref.setSummary(accentPref.getEntry());
      accentPref.setOnPreferenceChangeListener(
          (p, value) -> {
            Prefs.setAccent(getContext(), (String) value);
            int idx = accentPref.findIndexOfValue((String) value);
            if (idx >= 0) p.setSummary(accentPref.getEntries()[idx]);
            requireActivity().recreate();
            return false; // stored manually (persistent=false); value reset on recreate
          });
    }

    initializeColorPref(Prefs.COLOR_MSG_TEXT_IN_PREF);
    initializeColorPref(Prefs.COLOR_MSG_TEXT_OUT_PREF);
    initializeColorPref(Prefs.COLOR_BUBBLE_FILL_IN_PREF);
    initializeColorPref(Prefs.COLOR_BUBBLE_FILL_OUT_PREF);
    initializeColorPref(Prefs.COLOR_BUBBLE_BORDER_IN_PREF);
    initializeColorPref(Prefs.COLOR_BUBBLE_BORDER_OUT_PREF);
    initializeColorPref(Prefs.COLOR_CONVERSATION_BG_PREF);
    initializeColorPref(Prefs.COLOR_LIST_TITLE_PREF);
    initializeColorPref(Prefs.COLOR_LIST_PREVIEW_PREF);
    initializeColorPref(Prefs.COLOR_LIST_DATE_PREF);
    initializeColorPref(Prefs.COLOR_FAB_PREF);

    initializeFontPref(Prefs.FONT_CHAT_TEXT, "pref_font_chat_text");
    initializeFontPref(Prefs.FONT_CONV_TITLE, "pref_font_conv_title");
    initializeFontPref(Prefs.FONT_LIST_TITLE, "pref_font_list_title");
    initializeFontPref(Prefs.FONT_LIST_PREVIEW, "pref_font_list_preview");
    initializeFontPref(Prefs.FONT_LIST_DATE, "pref_font_list_date");
    initializeFontPref(Prefs.FONT_SETTINGS, "pref_font_settings");

    Preference addFontPref = findPreference("pref_font_add");
    if (addFontPref != null) {
      addFontPref.setOnPreferenceClickListener(
          p -> {
            if (hasStoragePermission()) launchFontPicker();
            else requestStoragePermission();
            return true;
          });
    }

    Preference eximportPref = findPreference("pref_eximport");
    if (eximportPref != null) {
      eximportPref.setOnPreferenceClickListener(
          p -> {
            showExportImportDialog();
            return true;
          });
    }

    Preference resetPref = findPreference("pref_reset_ui");
    if (resetPref != null) {
      resetPref.setOnPreferenceClickListener(
          p -> {
            AlertDialog dialog =
                new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.pref_reset_ui_confirm_title)
                    .setMessage(R.string.pref_reset_ui_confirm_message)
                    .setPositiveButton(
                        android.R.string.ok,
                        (d, w) -> {
                          Prefs.resetShiroikumaUi(requireContext());
                          requireActivity().recreate();
                        })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            styleEximDialog(dialog);
            return true;
          });
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    refreshEximportRow();
  }

  // --- Export/Import (Kōjiki-style) ---------------------------------------------------------

  /** Queries the export directory for the latest export and mirrors it in the row summary. */
  private void refreshEximportRow() {
    Context ctx = getContext();
    if (ctx == null) return;
    Context app = ctx.getApplicationContext();
    Util.runOnAnyBackgroundThread(
        () -> {
          String status = ShiroikumaExport.lastExportStatus(app);
          Util.runOnMain(
              () -> {
                if (!isAdded()) return;
                Preference p = findPreference("pref_eximport");
                if (p != null) {
                  p.setSummary(getString(R.string.eim_row_summary) + "\n" + status);
                }
              });
        });
  }

  /**
   * The dialog theme (Theme.AppCompat.Dialog.Alert) redefines colorAccent to the Material default
   * teal, so ?attr-based window borders come out wrong inside dialogs. Restyle in code with the
   * ACTIVITY-resolved accent: black rounded window with an accent border, and pill buttons with a
   * round accent border. Call after show() — the buttons only exist then.
   */
  private void styleEximDialog(AlertDialog dialog) {
    Context ctx = requireContext();
    int accent = ResUtil.getColor(ctx, R.attr.colorAccent);
    float dp = getResources().getDisplayMetrics().density;

    if (dialog.getWindow() != null) {
      GradientDrawable bg = new GradientDrawable();
      bg.setColor(0xFF000000);
      bg.setCornerRadius(8 * dp);
      bg.setStroke((int) (2 * dp), accent);
      dialog.getWindow().setBackgroundDrawable(new InsetDrawable(bg, (int) (16 * dp)));
    }

    int[] which = {
      AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL
    };
    for (int w : which) {
      Button b = dialog.getButton(w);
      if (b == null) continue;
      GradientDrawable pill = new GradientDrawable();
      pill.setColor(0xFF000000);
      pill.setCornerRadius(50 * dp);
      pill.setStroke((int) (1.5f * dp), accent);
      RippleDrawable ripple =
          new RippleDrawable(
              ColorStateList.valueOf((accent & 0x00FFFFFF) | 0x33000000), pill, null);
      b.setBackground(ripple);
      b.setTextColor(accent);
      b.setPadding((int) (20 * dp), (int) (6 * dp), (int) (20 * dp), (int) (6 * dp));
      ViewGroup.LayoutParams lp = b.getLayoutParams();
      if (lp instanceof ViewGroup.MarginLayoutParams) {
        ((ViewGroup.MarginLayoutParams) lp).setMarginStart((int) (8 * dp));
        b.setLayoutParams(lp);
      }
    }
  }

  private GradientDrawable eimBorder(int accent) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(0xFF000000);
    d.setCornerRadius(8 * getResources().getDisplayMetrics().density);
    d.setStroke((int) (1.5f * getResources().getDisplayMetrics().density), accent);
    return d;
  }

  private void showExportImportDialog() {
    Context ctx = requireContext();
    int accent = ResUtil.getColor(ctx, R.attr.colorAccent);
    int dim = (accent & 0x00FFFFFF) | 0xC8000000;
    float dp = getResources().getDisplayMetrics().density;
    int pad = (int) (20 * dp);

    LinearLayout root = new LinearLayout(ctx);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(pad, (int) (12 * dp), pad, 0);

    TextView desc = new TextView(ctx);
    desc.setText(R.string.eim_dialog_desc);
    desc.setTextColor(dim);
    desc.setTextSize(13);
    root.addView(desc);

    // export-directory box: caption + current folder, tap to (re)choose
    LinearLayout dirBox = new LinearLayout(ctx);
    dirBox.setOrientation(LinearLayout.VERTICAL);
    int boxPad = (int) (12 * dp);
    dirBox.setPadding(boxPad, boxPad, boxPad, boxPad);
    dirBox.setBackground(eimBorder(accent));
    LinearLayout.LayoutParams dirLp =
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    dirLp.topMargin = (int) (12 * dp);
    TextView dirCaption = new TextView(ctx);
    dirCaption.setText(R.string.eim_dir_caption);
    dirCaption.setTextColor(dim);
    dirCaption.setTextSize(12);
    dirBox.addView(dirCaption);
    eimFolderTv = new TextView(ctx);
    eimFolderTv.setTextSize(15);
    dirBox.addView(eimFolderTv);
    dirBox.setOnClickListener(v -> eimDirPickerLauncher.launch(ShiroikumaExport.getDirUri(ctx)));
    root.addView(dirBox, dirLp);

    eimStatusTv = new TextView(ctx);
    eimStatusTv.setTextSize(13);
    LinearLayout.LayoutParams statusLp =
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    statusLp.topMargin = (int) (8 * dp);
    statusLp.bottomMargin = (int) (10 * dp);
    root.addView(eimStatusTv, statusLp);
    refreshEximportDialogStatus();

    // select-all + one checkbox per category, all ticked by default (Kōjiki flow)
    List<CheckBox> catBoxes = new ArrayList<>();
    CheckBox selectAll = new CheckBox(ctx);
    selectAll.setText(R.string.eim_select_all);
    selectAll.setTextColor(accent);
    selectAll.setTypeface(null, Typeface.BOLD);
    selectAll.setChecked(true);
    root.addView(selectAll);
    for (ShiroikumaExport.Cat cat : ShiroikumaExport.Cat.values()) {
      CheckBox cb = new CheckBox(ctx);
      cb.setText(cat.labelRes);
      cb.setTextColor(accent);
      cb.setChecked(true);
      cb.setTag(cat);
      catBoxes.add(cb);
      root.addView(cb);
    }
    selectAll.setOnCheckedChangeListener(
        (btn, checked) -> {
          for (CheckBox cb : catBoxes) cb.setChecked(checked);
        });

    ScrollView scroll = new ScrollView(ctx);
    scroll.addView(root);

    AlertDialog dialog =
        new AlertDialog.Builder(ctx)
            .setTitle(R.string.eim_dialog_title)
            .setView(scroll)
            .setPositiveButton(
                R.string.eim_export, (d, w) -> onEximportExport(selectedCats(catBoxes)))
            .setNegativeButton(
                R.string.eim_import, (d, w) -> onEximportImport(selectedCats(catBoxes)))
            .setNeutralButton(android.R.string.cancel, null)
            .setOnDismissListener(
                d -> {
                  eimFolderTv = null;
                  eimStatusTv = null;
                })
            .show();
    styleEximDialog(dialog);
  }

  private List<ShiroikumaExport.Cat> selectedCats(List<CheckBox> boxes) {
    List<ShiroikumaExport.Cat> out = new ArrayList<>();
    for (CheckBox cb : boxes) {
      if (cb.isChecked()) out.add((ShiroikumaExport.Cat) cb.getTag());
    }
    return out;
  }

  /** Updates the folder-name and last-export lines inside the open dialog. */
  private void refreshEximportDialogStatus() {
    Context ctx = getContext();
    if (ctx == null || eimFolderTv == null || eimStatusTv == null) return;
    int accent = ResUtil.getColor(ctx, R.attr.colorAccent);
    DocumentFile dir = ShiroikumaExport.getExportDir(ctx);
    if (dir != null) {
      String name = dir.getName();
      Uri uri = ShiroikumaExport.getDirUri(ctx);
      eimFolderTv.setText(name != null ? name : (uri != null ? uri.getLastPathSegment() : ""));
      eimFolderTv.setTextColor(accent);
    } else {
      eimFolderTv.setText(R.string.eim_dir_unset);
      eimFolderTv.setTextColor(EIM_WARN_COLOR);
    }
    TextView statusTv = eimStatusTv;
    Context app = ctx.getApplicationContext();
    Util.runOnAnyBackgroundThread(
        () -> {
          String status = ShiroikumaExport.lastExportStatus(app);
          boolean warn = ShiroikumaExport.latestExport(app) == null;
          Util.runOnMain(
              () -> {
                statusTv.setText(status);
                statusTv.setTextColor(warn ? EIM_WARN_COLOR : accent);
              });
        });
  }

  private void onEximportDirPicked(Uri uri) {
    Context ctx = getContext();
    if (ctx == null) return;
    try {
      ctx.getContentResolver()
          .takePersistableUriPermission(
              uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    } catch (Exception ignored) {
    }
    ShiroikumaExport.setDirUri(ctx, uri);
    refreshEximportDialogStatus();
    refreshEximportRow();
  }

  private void onEximportExport(List<ShiroikumaExport.Cat> cats) {
    Context ctx = getContext();
    if (ctx == null) return;
    if (cats.isEmpty()) {
      Toast.makeText(ctx, R.string.eim_none_selected, Toast.LENGTH_SHORT).show();
      return;
    }
    Context app = ctx.getApplicationContext();
    if (ShiroikumaExport.getExportDir(app) != null) {
      Toast.makeText(ctx, R.string.eim_exporting, Toast.LENGTH_SHORT).show();
      Util.runOnAnyBackgroundThread(
          () -> {
            try {
              byte[] bytes = ShiroikumaExport.export(app, cats);
              DocumentFile dir = ShiroikumaExport.getExportDir(app);
              String name = ShiroikumaExport.exportFileName();
              DocumentFile file =
                  dir != null ? dir.createFile("application/zip", name) : null;
              if (file == null) throw new IllegalStateException("could not create " + name);
              try (OutputStream out =
                  app.getContentResolver().openOutputStream(file.getUri())) {
                if (out == null) throw new IllegalStateException("no stream");
                out.write(bytes);
              }
              Util.runOnMain(
                  () -> {
                    Toast.makeText(app, app.getString(R.string.eim_export_ok, name), Toast.LENGTH_LONG)
                        .show();
                    refreshEximportRow();
                  });
            } catch (Exception e) {
              Util.runOnMain(
                  () ->
                      Toast.makeText(
                              app,
                              app.getString(R.string.eim_export_fail, String.valueOf(e.getMessage())),
                              Toast.LENGTH_LONG)
                          .show());
            }
          });
    } else {
      // no directory configured - fall back to a save-as picker
      try {
        pendingExportBytes = ShiroikumaExport.export(app, cats);
        eimSaveAsLauncher.launch(ShiroikumaExport.exportFileName());
      } catch (Exception e) {
        Toast.makeText(
                ctx,
                getString(R.string.eim_export_fail, String.valueOf(e.getMessage())),
                Toast.LENGTH_LONG)
            .show();
      }
    }
  }

  private void writePendingExportTo(Uri uri) {
    Context ctx = getContext();
    byte[] bytes = pendingExportBytes;
    pendingExportBytes = null;
    if (ctx == null || bytes == null) return;
    Context app = ctx.getApplicationContext();
    Util.runOnAnyBackgroundThread(
        () -> {
          try {
            try (OutputStream out = app.getContentResolver().openOutputStream(uri)) {
              if (out == null) throw new IllegalStateException("no stream");
              out.write(bytes);
            }
            Util.runOnMain(
                () ->
                    Toast.makeText(
                            app,
                            app.getString(R.string.eim_export_ok, String.valueOf(uri.getLastPathSegment())),
                            Toast.LENGTH_LONG)
                        .show());
          } catch (Exception e) {
            Util.runOnMain(
                () ->
                    Toast.makeText(
                            app,
                            app.getString(R.string.eim_export_fail, String.valueOf(e.getMessage())),
                            Toast.LENGTH_LONG)
                        .show());
          }
        });
  }

  private void onEximportImport(List<ShiroikumaExport.Cat> cats) {
    Context ctx = getContext();
    if (ctx == null) return;
    if (cats.isEmpty()) {
      Toast.makeText(ctx, R.string.eim_none_selected, Toast.LENGTH_SHORT).show();
      return;
    }
    pendingImportCats = cats;
    eimImportLauncher.launch(new String[] {"application/zip", "application/octet-stream", "*/*"});
  }

  private void onEximportFilePicked(Uri uri) {
    Context ctx = getContext();
    List<ShiroikumaExport.Cat> cats = pendingImportCats;
    pendingImportCats = null;
    if (ctx == null || cats == null) return;
    Context app = ctx.getApplicationContext();
    Toast.makeText(ctx, R.string.eim_importing, Toast.LENGTH_SHORT).show();
    Util.runOnAnyBackgroundThread(
        () -> {
          String summary = null;
          String error = null;
          try {
            byte[] bytes;
            try (InputStream in = app.getContentResolver().openInputStream(uri)) {
              if (in == null) throw new IllegalStateException("no stream");
              java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
              byte[] buf = new byte[8192];
              int n;
              while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
              bytes = bos.toByteArray();
            }
            if (ShiroikumaExport.categoriesIn(bytes).isEmpty()) {
              error = app.getString(R.string.eim_import_none);
            } else {
              summary = ShiroikumaExport.importData(app, bytes, cats);
              if (summary == null) error = app.getString(R.string.eim_import_none);
            }
          } catch (Exception e) {
            error = String.valueOf(e.getMessage());
          }
          String fSummary = summary;
          String fError = error;
          Util.runOnMain(
              () -> {
                if (fSummary != null) {
                  showEximportResult(fSummary);
                } else {
                  Toast.makeText(
                          app, app.getString(R.string.eim_import_fail, fError), Toast.LENGTH_LONG)
                      .show();
                }
              });
        });
  }

  private void showEximportResult(String summary) {
    Context ctx = getContext();
    if (ctx == null) return;
    AlertDialog dialog =
        new AlertDialog.Builder(ctx)
            .setTitle(R.string.eim_import_done_title)
            .setMessage(getString(R.string.eim_import_done_body, summary))
            .setCancelable(false)
            .setPositiveButton(R.string.eim_restart_now, (d, w) -> restartApp())
            .setNegativeButton(R.string.eim_restart_later, null)
            .show();
    styleEximDialog(dialog);
  }

  private void restartApp() {
    Context ctx = requireContext().getApplicationContext();
    Intent launch =
        ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
    if (launch != null && launch.getComponent() != null) {
      ctx.startActivity(Intent.makeRestartActivityTask(launch.getComponent()));
    }
    Runtime.getRuntime().exit(0);
  }

  private boolean hasStoragePermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      return Environment.isExternalStorageManager();
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return ContextCompat.checkSelfPermission(
              requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
          == PackageManager.PERMISSION_GRANTED;
    }
    return true;
  }

  private void requestStoragePermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      Toast.makeText(getContext(), "Grant all-files access, then pick again", Toast.LENGTH_LONG).show();
      try {
        allFilesLauncher.launch(
            new Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + requireContext().getPackageName())));
      } catch (Exception e) {
        allFilesLauncher.launch(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
      }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      readPermLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
    } else {
      launchFontPicker();
    }
  }

  private void launchFontPicker() {
    fontPickLauncher.launch(new String[] {"*/*"});
  }

  // Reference the picked file by its real path (no copy). Falls back to copying into the app's
  // files dir only when the URI can't be resolved to a path (unusual providers).
  private void onFontPicked(Uri uri) {
    Context ctx = getContext();
    if (ctx == null) return;
    String path = resolveFontPath(ctx, uri);
    if (path == null || !new File(path).exists()) {
      path = FontUtil.copyToInternal(ctx, uri, queryDisplayName(ctx, uri));
    }
    if (path == null) {
      Toast.makeText(ctx, "Could not read that file", Toast.LENGTH_SHORT).show();
      return;
    }
    Prefs.addFontFile(ctx, path);
    FontUtil.clearCache();
    Toast.makeText(ctx, "Added " + new File(path).getName(), Toast.LENGTH_SHORT).show();
  }

  private String resolveFontPath(Context ctx, Uri uri) {
    try {
      if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
        String docId = android.provider.DocumentsContract.getDocumentId(uri);
        String[] split = docId.split(":", 2);
        if (split.length == 2) {
          if ("primary".equalsIgnoreCase(split[0])) {
            return Environment.getExternalStorageDirectory() + "/" + split[1];
          }
          return "/storage/" + split[0] + "/" + split[1]; // removable volume, best effort
        }
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  private String queryDisplayName(Context ctx, Uri uri) {
    try (android.database.Cursor c = ctx.getContentResolver().query(uri, null, null, null, null)) {
      if (c != null && c.moveToFirst()) {
        int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
        if (idx >= 0) return c.getString(idx);
      }
    } catch (Exception ignored) {
    }
    return uri.getLastPathSegment();
  }

  // a font-category preference - shows a summary of the current family/weight/size and opens the
  // font picker on tap. Applies on next render.
  private void initializeFontPref(String category, String key) {
    Preference pref = findPreference(key);
    if (pref == null) return;
    Context c = getContext();
    pref.setSummary(
        FontUtil.describe(
            Prefs.getFontFamily(c, category),
            Prefs.getFontWeight(c, category),
            Prefs.getFontSize(c, category)));
    pref.setOnPreferenceClickListener(
        p -> {
          Context ctx = getContext();
          FontPickerDialog.show(
              ctx,
              p.getTitle() == null ? "" : p.getTitle().toString(),
              Prefs.getFontFamily(ctx, category),
              Prefs.getFontWeight(ctx, category),
              Prefs.getFontSize(ctx, category),
              (family, weight, size) -> {
                Prefs.setFont(ctx, category, family, weight, size);
                p.setSummary(FontUtil.describe(family, weight, size));
              });
          return true;
        });
  }

  // a colour-role preference - shows a swatch of the current colour and opens the RGB/hex picker on
  // tap. The colour applies on next render (the surface reads Prefs at bind time), so no recreation.
  private void initializeColorPref(String key) {
    Preference pref = findPreference(key);
    if (pref == null) return;
    updateColorSwatch(pref, getColorFor(key));
    pref.setOnPreferenceClickListener(
        p -> {
          ColorPickerDialog.show(
              getContext(),
              p.getTitle() == null ? "" : p.getTitle().toString(),
              getColorFor(key),
              color -> {
                setColorFor(key, color);
                updateColorSwatch(p, color);
              });
          return true;
        });
  }

  private int getColorFor(String key) {
    Context c = getContext();
    switch (key) {
      case Prefs.COLOR_MSG_TEXT_IN_PREF:
        return Prefs.getMessageTextColor(c, false);
      case Prefs.COLOR_MSG_TEXT_OUT_PREF:
        return Prefs.getMessageTextColor(c, true);
      case Prefs.COLOR_BUBBLE_FILL_IN_PREF:
        return Prefs.getBubbleFillColor(c, false);
      case Prefs.COLOR_BUBBLE_FILL_OUT_PREF:
        return Prefs.getBubbleFillColor(c, true);
      case Prefs.COLOR_BUBBLE_BORDER_IN_PREF:
        return Prefs.getBubbleBorderColor(c, false);
      case Prefs.COLOR_BUBBLE_BORDER_OUT_PREF:
        return Prefs.getBubbleBorderColor(c, true);
      case Prefs.COLOR_CONVERSATION_BG_PREF:
        return Prefs.getConversationBackgroundColor(c);
      case Prefs.COLOR_LIST_TITLE_PREF:
        return Prefs.getListTitleColor(c);
      case Prefs.COLOR_LIST_PREVIEW_PREF:
        return Prefs.getListPreviewColor(c);
      case Prefs.COLOR_LIST_DATE_PREF:
        return Prefs.getListDateColor(c);
      case Prefs.COLOR_FAB_PREF:
      default:
        return Prefs.getFabColor(c);
    }
  }

  private void setColorFor(String key, int color) {
    Context c = getContext();
    switch (key) {
      case Prefs.COLOR_MSG_TEXT_IN_PREF:
        Prefs.setMessageTextColor(c, false, color);
        break;
      case Prefs.COLOR_MSG_TEXT_OUT_PREF:
        Prefs.setMessageTextColor(c, true, color);
        break;
      case Prefs.COLOR_BUBBLE_FILL_IN_PREF:
        Prefs.setBubbleFillColor(c, false, color);
        break;
      case Prefs.COLOR_BUBBLE_FILL_OUT_PREF:
        Prefs.setBubbleFillColor(c, true, color);
        break;
      case Prefs.COLOR_BUBBLE_BORDER_IN_PREF:
        Prefs.setBubbleBorderColor(c, false, color);
        break;
      case Prefs.COLOR_BUBBLE_BORDER_OUT_PREF:
        Prefs.setBubbleBorderColor(c, true, color);
        break;
      case Prefs.COLOR_CONVERSATION_BG_PREF:
        Prefs.setConversationBackgroundColor(c, color);
        break;
      case Prefs.COLOR_LIST_TITLE_PREF:
        Prefs.setListTitleColor(c, color);
        break;
      case Prefs.COLOR_LIST_PREVIEW_PREF:
        Prefs.setListPreviewColor(c, color);
        break;
      case Prefs.COLOR_LIST_DATE_PREF:
        Prefs.setListDateColor(c, color);
        break;
      case Prefs.COLOR_FAB_PREF:
      default:
        Prefs.setFabColor(c, color);
        break;
    }
  }

  private void updateColorSwatch(Preference pref, int color) {
    android.graphics.drawable.GradientDrawable swatch =
        new android.graphics.drawable.GradientDrawable();
    swatch.setShape(android.graphics.drawable.GradientDrawable.OVAL);
    swatch.setColor(color);
    swatch.setStroke(2, 0xFF888888);
    int size = (int) (28 * getResources().getDisplayMetrics().density);
    swatch.setSize(size, size);
    pref.setIcon(swatch);
    pref.setSummary(String.format("#%06X", color & 0xFFFFFF));
  }
}
