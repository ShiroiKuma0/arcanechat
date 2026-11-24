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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.shiroikuma.SkActionPreference;
import org.thoughtcrime.securesms.shiroikuma.SkIcons;
import org.thoughtcrime.securesms.shiroikuma.SkIndexPreference;
import org.thoughtcrime.securesms.shiroikuma.SkPageState;
import org.thoughtcrime.securesms.shiroikuma.SkPills;
import org.thoughtcrime.securesms.shiroikuma.SkRole;
import org.thoughtcrime.securesms.shiroikuma.SkRolePreference;
import org.thoughtcrime.securesms.shiroikuma.SkRoles;
import org.thoughtcrime.securesms.shiroikuma.SkSectionCategory;
import org.thoughtcrime.securesms.shiroikuma.SkSubheaderPreference;
import org.thoughtcrime.securesms.shiroikuma.SkSwitches;
import org.thoughtcrime.securesms.automation.AutomationAuth;
import org.thoughtcrime.securesms.automation.StateExportReceiver;
import org.thoughtcrime.securesms.components.AutomationTokenPreference;
import org.thoughtcrime.securesms.components.ColorPickerDialog;
import org.thoughtcrime.securesms.components.DeliveryStatusView;
import org.thoughtcrime.securesms.components.FontPickerDialog;
import org.thoughtcrime.securesms.components.SwitchPreferenceCompat;
import org.thoughtcrime.securesms.components.TickGlyphDialog;
import org.thoughtcrime.securesms.components.TickSizeDialog;
import org.thoughtcrime.securesms.util.FontUtil;
import org.thoughtcrime.securesms.util.Prefs;
import org.thoughtcrime.securesms.util.ResUtil;
import org.thoughtcrime.securesms.util.ShiroikumaExport;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.views.ProgressDialog;

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
            else Toast.makeText(getContext(), R.string.sk_storage_not_granted, Toast.LENGTH_SHORT).show();
          });
  private final ActivityResultLauncher<String> readPermLauncher =
      registerForActivityResult(
          new ActivityResultContracts.RequestPermission(),
          granted -> {
            if (granted) launchFontPicker();
            else Toast.makeText(getContext(), R.string.sk_storage_not_granted, Toast.LENGTH_SHORT).show();
          });
  private final ActivityResultLauncher<String[]> fontPickLauncher =
      registerForActivityResult(
          new ActivityResultContracts.OpenDocument(),
          uri -> {
            if (uri != null) onFontPicked(uri);
          });

  // shiroikuma fork: Export/Import (Kōjiki-style). SAF folder picker for the export directory,
  // save-as fallback when no directory is set, and the import file picker.
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
  private List<ShiroikumaExport.Cat> pendingExportCats;
  private List<ShiroikumaExport.Cat> pendingImportCats;
  @Nullable private TextView eimFolderTv;
  @Nullable private TextView eimStatusTv;
  @Nullable private ProgressDialog eimProgressDialog;
  @Nullable private AlertDialog eximDialog;

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    FontUtil.localize(requireContext());
    addPreferencesFromResource(R.xml.preferences_shiroikuma_ui);
    buildPage();
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


    initializeFontPref(Prefs.FONT_CHAT_TEXT, "pref_font_chat_text");
    initializeFontPref(Prefs.FONT_CONV_TITLE, "pref_font_conv_title");
    initializeFontPref(Prefs.FONT_LIST_TITLE, "pref_font_list_title");
    initializeFontPref(Prefs.FONT_LIST_PREVIEW, "pref_font_list_preview");
    initializeFontPref(Prefs.FONT_LIST_DATE, "pref_font_list_date");
    initializeFontPref(Prefs.FONT_SETTINGS, "pref_font_settings");

    initializeTickPrefs();

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

    initializeAutomationPrefs();

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

  // --- automation (保存復元 contract) -------------------------------------------------------

  /**
   * The three rows below Export/Import (contract v2): the master switch for {@link
   * StateExportReceiver} and the {@link org.thoughtcrime.securesms.automation.AutomationProvider}
   * data door (default <b>ON</b>), 「Use authorization token?」 (default <b>OFF</b>), and the token row
   * — tap copies the full token, the right-hand button regenerates it.
   *
   * <p>The token row is <b>shown only when the token is being asked for</b>: a 48-character secret
   * sitting under an off switch invites 白い熊 to paste it somewhere it will do nothing.
   *
   * <p>All three are {@code persistent="false"} and stored in {@link AutomationAuth}'s own
   * device-local prefs file, so the token never travels inside an export zip.
   */
  private void initializeAutomationPrefs() {
    SwitchPreferenceCompat switchPref =
        (SwitchPreferenceCompat) findPreference("pref_automation_enabled");
    if (switchPref != null) {
      switchPref.setChecked(AutomationAuth.isEnabled(requireContext()));
      switchPref.setOnPreferenceChangeListener(
          (p, value) -> {
            AutomationAuth.setEnabled(requireContext(), Boolean.TRUE.equals(value));
            return true;
          });
    }

    AutomationTokenPreference tokenPref =
        (AutomationTokenPreference) findPreference("pref_automation_token");

    SwitchPreferenceCompat requireTokenPref =
        (SwitchPreferenceCompat) findPreference("pref_automation_require_token");
    if (requireTokenPref != null) {
      boolean required = AutomationAuth.isTokenRequired(requireContext());
      requireTokenPref.setChecked(required);
      if (tokenPref != null) SkSectionCategory.setWanted(tokenPref, required);
      requireTokenPref.setOnPreferenceChangeListener(
          (p, value) -> {
            boolean now = Boolean.TRUE.equals(value);
            AutomationAuth.setTokenRequired(requireContext(), now);
            if (tokenPref != null) SkSectionCategory.setWanted(tokenPref, now);
            return true;
          });
    }

    if (tokenPref == null) return;
    updateAutomationTokenRow(tokenPref);
    tokenPref.setOnPreferenceClickListener(
        p -> {
          Context ctx = getContext();
          if (ctx == null) return true;
          Util.writeTextToClipboard(ctx, AutomationAuth.getToken(ctx));
          Toast.makeText(ctx, R.string.pref_automation_token_copied, Toast.LENGTH_SHORT).show();
          return true;
        });
    tokenPref.setOnRegenerateListener(
        () -> {
          Context ctx = getContext();
          if (ctx == null) return;
          AlertDialog dialog =
              new AlertDialog.Builder(ctx)
                  .setTitle(R.string.pref_automation_regenerate_title)
                  .setMessage(R.string.pref_automation_regenerate_message)
                  .setPositiveButton(
                      R.string.pref_automation_regenerate,
                      (d, w) -> {
                        AutomationAuth.regenerateToken(ctx);
                        updateAutomationTokenRow(tokenPref);
                        Toast.makeText(ctx, R.string.pref_automation_regenerated, Toast.LENGTH_SHORT)
                            .show();
                      })
                  .setNegativeButton(android.R.string.cancel, null)
                  .show();
          styleEximDialog(dialog);
        });
  }

  private void updateAutomationTokenRow(AutomationTokenPreference pref) {
    Context ctx = getContext();
    if (ctx == null) return;
    pref.setSummary(
        AutomationAuth.abbreviate(AutomationAuth.getToken(ctx))
            + "\n"
            + getString(R.string.pref_automation_token_summary));
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
   * Every dialog is themed in one place now - the dialog theme names colour roles and {@code
   * shiroikuma/SkViewInflater} makes its buttons pills - so nothing is left to restyle here. Kept
   * as the one hook the dialogs of this page pass through.
   */
  private void styleEximDialog(AlertDialog dialog) {}

  private GradientDrawable eimBorder(int frame) {
    return SkPills.panel(
        requireContext(), 1.25f, 8f, frame, SkRoles.get(requireContext(), SkRole.DIALOG_GROUND));
  }

  private void showExportImportDialog() {
    Context ctx = requireContext();
    int accent = SkRoles.get(ctx, SkRole.DIALOG_TEXT);
    int dim = SkRoles.get(ctx, SkRole.NOTE);
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
    dirBox.setBackground(eimBorder(SkRoles.get(ctx, SkRole.DIALOG_FRAME)));
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

    // select-all + one checkbox per category, seeded from Cat.defaultSelected - the same flag the
    // automation LIST_CATEGORIES reply sends as its fourth field, so both pickers open identically
    List<CheckBox> catBoxes = new ArrayList<>();
    CheckBox selectAll = new CheckBox(ctx);
    selectAll.setText(R.string.eim_select_all);
    SkSwitches.mark(selectAll);
    selectAll.setTypeface(null, Typeface.BOLD);
    root.addView(selectAll);
    boolean allOn = true;
    for (ShiroikumaExport.Cat cat : ShiroikumaExport.Cat.values()) {
      CheckBox cb = new CheckBox(ctx);
      cb.setText(cat.labelRes);
      SkSwitches.mark(cb);
      cb.setChecked(cat.defaultSelected);
      cb.setTag(cat);
      catBoxes.add(cb);
      root.addView(cb);
      allOn &= cat.defaultSelected;
    }
    // set before the listener is attached, so seeding never cascades over the per-category defaults
    selectAll.setChecked(allOn);
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
            .setPositiveButton(R.string.eim_export, null)
            .setNegativeButton(R.string.eim_import, null)
            .setNeutralButton(android.R.string.cancel, null)
            .setOnDismissListener(
                d -> {
                  eimFolderTv = null;
                  eimStatusTv = null;
                  eximDialog = null;
                })
            .show();
    styleEximDialog(dialog);
    eximDialog = dialog;
    // Export/Import must NOT auto-dismiss the panel: failures leave it open, and on success the
    // whole chain (info dialog -> panel -> UI page) is closed by closeEximportChain().
    Button exportBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
    if (exportBtn != null) {
      exportBtn.setOnClickListener(v -> onEximportExport(selectedCats(catBoxes)));
    }
    Button importBtn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
    if (importBtn != null) {
      importBtn.setOnClickListener(v -> onEximportImport(selectedCats(catBoxes)));
    }
  }

  /** Closes the info dialog's underlying chain: the Export/Import panel, then the UI page. */
  private void closeEximportChain() {
    AlertDialog dialog = eximDialog;
    eximDialog = null;
    if (dialog != null) {
      try {
        dialog.dismiss();
      } catch (Exception ignored) {
      }
    }
    if (getActivity() != null) getActivity().finish();
  }

  /** Black-and-accent OK dialog after a successful export; OK tears down the whole chain. */
  private void showEximportExportDone(String name) {
    Context ctx = getContext();
    if (ctx == null) return;
    AlertDialog dialog =
        new AlertDialog.Builder(ctx)
            .setTitle(R.string.eim_export_done_title)
            .setMessage(getString(R.string.eim_export_ok, name))
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok, (d, w) -> closeEximportChain())
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
      eimFolderTv.setTextColor(SkRoles.get(requireContext(), SkRole.DELETE));
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
                statusTv.setTextColor(warn ? SkRoles.get(requireContext(), SkRole.DELETE) : accent);
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

  /** Modal progress while an export/import runs (account backups can take a while). */
  private void showEximProgress() {
    dismissEximProgress();
    ProgressDialog dialog = new ProgressDialog(requireActivity());
    dialog.setMessage(getString(R.string.one_moment));
    dialog.setCanceledOnTouchOutside(false);
    dialog.setCancelable(false);
    dialog.show();
    styleEximDialog(dialog); // accent border (the dialog theme would render it teal)
    eimProgressDialog = dialog;
  }

  private void dismissEximProgress() {
    ProgressDialog dialog = eimProgressDialog;
    eimProgressDialog = null;
    if (dialog != null) {
      try {
        dialog.dismiss();
      } catch (Exception ignored) {
      }
    }
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
      showEximProgress();
      Util.runOnAnyBackgroundThread(
          () -> {
            DocumentFile file = null;
            try {
              DocumentFile dir = ShiroikumaExport.getExportDir(app);
              String name = ShiroikumaExport.exportFileName();
              file = dir != null ? dir.createFile("application/zip", name) : null;
              if (file == null) throw new IllegalStateException("could not create " + name);
              try (OutputStream out =
                  app.getContentResolver().openOutputStream(file.getUri())) {
                if (out == null) throw new IllegalStateException("no stream");
                ShiroikumaExport.export(app, cats, out);
              }
              String fName = name;
              Util.runOnMain(
                  () -> {
                    dismissEximProgress();
                    showEximportExportDone(fName);
                  });
            } catch (Exception e) {
              if (file != null) file.delete(); // don't leave a truncated export behind
              Util.runOnMain(
                  () -> {
                    dismissEximProgress();
                    Toast.makeText(
                            app,
                            app.getString(R.string.eim_export_fail, String.valueOf(e.getMessage())),
                            Toast.LENGTH_LONG)
                        .show();
                  });
            }
          });
    } else {
      // no directory configured - fall back to a save-as picker, export runs once the uri is known
      pendingExportCats = cats;
      eimSaveAsLauncher.launch(ShiroikumaExport.exportFileName());
    }
  }

  private void writePendingExportTo(Uri uri) {
    Context ctx = getContext();
    List<ShiroikumaExport.Cat> cats = pendingExportCats;
    pendingExportCats = null;
    if (ctx == null || cats == null) return;
    Context app = ctx.getApplicationContext();
    showEximProgress();
    Util.runOnAnyBackgroundThread(
        () -> {
          try {
            try (OutputStream out = app.getContentResolver().openOutputStream(uri)) {
              if (out == null) throw new IllegalStateException("no stream");
              ShiroikumaExport.export(app, cats, out);
            }
            Util.runOnMain(
                () -> {
                  dismissEximProgress();
                  showEximportExportDone(String.valueOf(uri.getLastPathSegment()));
                });
          } catch (Exception e) {
            try {
              android.provider.DocumentsContract.deleteDocument(app.getContentResolver(), uri);
            } catch (Exception ignored) {
            }
            Util.runOnMain(
                () -> {
                  dismissEximProgress();
                  Toast.makeText(
                          app,
                          app.getString(R.string.eim_export_fail, String.valueOf(e.getMessage())),
                          Toast.LENGTH_LONG)
                      .show();
                });
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
    showEximProgress();
    Util.runOnAnyBackgroundThread(
        () -> {
          String summary = null;
          String error = null;
          // copy to a temp file so the zip is random-access and account tars stream from disk
          File tmp = null;
          try {
            tmp = File.createTempFile("shiroikuma-eximport", ".zip", app.getCacheDir());
            try (InputStream in = app.getContentResolver().openInputStream(uri);
                java.io.OutputStream out = new java.io.FileOutputStream(tmp)) {
              if (in == null) throw new IllegalStateException("no stream");
              byte[] buf = new byte[65536];
              int n;
              while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(tmp)) {
              if (ShiroikumaExport.categoriesIn(zip).isEmpty()) {
                error = app.getString(R.string.eim_import_none);
              } else {
                summary = ShiroikumaExport.importData(app, zip, cats);
                if (summary == null) error = app.getString(R.string.eim_import_none);
              }
            }
          } catch (Exception e) {
            error = String.valueOf(e.getMessage());
          } finally {
            if (tmp != null) //noinspection ResultOfMethodCallIgnored
              tmp.delete();
          }
          String fSummary = summary;
          String fError = error;
          Util.runOnMain(
              () -> {
                dismissEximProgress();
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
            .setNegativeButton(R.string.eim_restart_later, (d, w) -> closeEximportChain())
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
      Toast.makeText(getContext(), R.string.sk_font_grant_access, Toast.LENGTH_LONG).show();
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
      Toast.makeText(ctx, R.string.sk_font_unreadable, Toast.LENGTH_SHORT).show();
      return;
    }
    Prefs.addFontFile(ctx, path);
    FontUtil.clearCache();
    Toast.makeText(ctx, getString(R.string.sk_font_added, new File(path).getName()), Toast.LENGTH_SHORT).show();
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

  // shiroikuma fork (Step 11): the delivery-tick section - one shared size, then a colour + glyph
  // pair per state (plus the spin and group-opt-in switches). Everything applies at the next bind,
  // so no recreate is needed; the chat list repaints on return via ConversationListFragment.
  private void initializeTickPrefs() {
    Preference sizePref = findPreference(Prefs.TICK_SIZE_PREF);
    if (sizePref != null) {
      updateTickSizeSummary(sizePref);
      sizePref.setOnPreferenceClickListener(
          p -> {
            TickSizeDialog.show(
                getContext(),
                p.getTitle() == null ? "" : p.getTitle().toString(),
                Prefs.getTickSize(getContext()),
                size -> {
                  Prefs.setTickSize(getContext(), size);
                  updateTickSizeSummary(p);
                  refreshAllTickGlyphRows(); // every preview is drawn at the tick size
                });
            return true;
          });
    }

    for (String state :
        new String[] {
          Prefs.TICK_SENDING,
          Prefs.TICK_SENT,
          Prefs.TICK_RECEIVED,
          Prefs.TICK_RECEIVED_ALL,
          Prefs.TICK_FAILED
        }) {
      initializeTickGlyphPref(state);
    }

    SwitchPreferenceCompat spinPref =
        (SwitchPreferenceCompat) findPreference(Prefs.TICK_SPIN_PREF);
    if (spinPref != null) {
      spinPref.setChecked(Prefs.isTickSpin(getContext()));
      spinPref.setOnPreferenceChangeListener(
          (p, value) -> {
            Prefs.setTickSpin(getContext(), Boolean.TRUE.equals(value));
            return true;
          });
    }

    SwitchPreferenceCompat groupPref =
        (SwitchPreferenceCompat) findPreference(Prefs.TICK_GROUP_ALL_PREF);
    if (groupPref != null) {
      groupPref.setChecked(Prefs.isTickGroupAll(getContext()));
      groupPref.setOnPreferenceChangeListener(
          (p, value) -> {
            Prefs.setTickGroupAll(getContext(), Boolean.TRUE.equals(value));
            return true;
          });
    }
  }

  private void updateTickSizeSummary(Preference pref) {
    pref.setSummary(getString(R.string.pref_ticks_size_summary, Prefs.getTickSize(getContext())));
  }

  // The glyph row itself previews the chosen glyph, at the configured tick size and colour, so the
  // page shows the whole ladder as it will actually render beside a message.
  private void initializeTickGlyphPref(String state) {
    Preference pref = findPreference(Prefs.tickGlyphKey(state));
    if (pref == null) return;
    updateTickGlyphRow(state);
    pref.setOnPreferenceClickListener(
        p -> {
          TickGlyphDialog.show(
              getContext(),
              p.getTitle() == null ? "" : p.getTitle().toString(),
              state,
              glyph -> {
                Prefs.setTickGlyph(getContext(), state, glyph);
                updateTickGlyphRow(state);
              });
          return true;
        });
  }

  private void updateTickGlyphRow(String state) {
    Context ctx = getContext();
    if (ctx == null) return;
    Preference pref = findPreference(Prefs.tickGlyphKey(state));
    if (pref == null) return;

    String glyph = Prefs.getTickGlyph(ctx, state);
    String[] values = getResources().getStringArray(R.array.tick_glyph_values);
    String[] labels = getResources().getStringArray(R.array.tick_glyph_entries);
    for (int i = 0; i < values.length; i++) {
      if (values[i].equals(glyph)) {
        pref.setSummary(labels[i]);
        break;
      }
    }
    pref.setIcon(
        DeliveryStatusView.glyphPreview(
            ctx, glyph, Prefs.getTickColor(ctx, state), Prefs.getTickSize(ctx)));
  }

  private void refreshAllTickGlyphRows() {
    for (String state :
        new String[] {
          Prefs.TICK_SENDING,
          Prefs.TICK_SENT,
          Prefs.TICK_RECEIVED,
          Prefs.TICK_RECEIVED_ALL,
          Prefs.TICK_FAILED
        }) {
      updateTickGlyphRow(state);
    }
  }

  // --- the page: sections, chip index, role rows ------------------------------------------

  /** Icon and colour of every section, by its id; the colour says what kind of thing it is. */
  private static final Object[][] SECTION_LOOKS = {
    {"eximport", SkIcons.Icon.SHARE, SkRole.SECTION_REACH, null},
    {"foundation", SkIcons.Icon.PALETTE, SkRole.SECTION_BASE, SkRole.Section.FOUNDATION},
    {"text", SkIcons.Icon.TEXT, SkRole.SECTION_WORDS, SkRole.Section.TEXT},
    {"buttons", SkIcons.Icon.POINTER, SkRole.SECTION_CONTROLS, SkRole.Section.BUTTONS},
    {"bars", SkIcons.Icon.DESKTOP, SkRole.SECTION_FRAME, SkRole.Section.BARS},
    {"dialogs", SkIcons.Icon.BUBBLE, SkRole.SECTION_REACH, SkRole.Section.DIALOGS},
    {"switches", SkIcons.Icon.TOGGLE, SkRole.SECTION_CONTROLS, SkRole.Section.SWITCHES},
    {"lists", SkIcons.Icon.LIST, SkRole.SECTION_FRAME, SkRole.Section.LISTS},
    {"menus", SkIcons.Icon.LAYERS, SkRole.SECTION_REACH, SkRole.Section.MENUS},
    {"marks", SkIcons.Icon.SELECT, SkRole.SECTION_CONTROLS, SkRole.Section.MARKS},
    {"fields", SkIcons.Icon.KEYBOARD, SkRole.SECTION_WORDS, SkRole.Section.FIELDS},
    {"chatlist", SkIcons.Icon.GROUP, SkRole.SECTION_BASE, SkRole.Section.CHATLIST},
    {"conversation", SkIcons.Icon.FRAME, SkRole.SECTION_FRAME, SkRole.Section.CONVERSATION},
    {"compose", SkIcons.Icon.EDIT, SkRole.SECTION_WORDS, SkRole.Section.COMPOSE},
    {"ticks", SkIcons.Icon.TICK2, SkRole.SECTION_BASE, SkRole.Section.TICKS},
    {"calls", SkIcons.Icon.PHONE, SkRole.SECTION_CONTROLS, SkRole.Section.CALLS},
    {"page", SkIcons.Icon.GEAR, SkRole.SECTION_BASE, SkRole.Section.PAGE},
    {"custom_fonts", SkIcons.Icon.BOOK, SkRole.SECTION_WORDS, null},
    {"maintenance", SkIcons.Icon.WARNING, SkRole.SECTION_WARNING, null},
  };

  private final List<SkSectionCategory> sections = new ArrayList<>();
  private final List<String> defaultOrder = new ArrayList<>();

  /**
   * Turns the inflated preference tree into the page: gives every section its icon and colour,
   * appends the swatch row of every colour role to the section of its item, puts the chip index
   * under the settings link, applies the stored section order and folds what is not open.
   */
  private void buildPage() {
    final Context ctx = requireContext();
    PreferenceScreen screen = getPreferenceScreen();
    sections.clear();
    defaultOrder.clear();
    for (int i = 0; i < screen.getPreferenceCount(); i++) {
      Preference p = screen.getPreference(i);
      if (p instanceof SkSectionCategory) sections.add((SkSectionCategory) p);
    }
    for (SkSectionCategory section : sections) {
      defaultOrder.add(section.id());
      SkRole.Section roles = null;
      for (Object[] look : SECTION_LOOKS) {
        if (look[0].equals(section.id())) {
          section.look((SkIcons.Icon) look[1], (SkRole) look[2]);
          roles = (SkRole.Section) look[3];
        }
      }
      // rows under a sub-heading sit one step further in
      int level = 0;
      for (int i = 0; i < section.getPreferenceCount(); i++) {
        Preference p = section.getPreference(i);
        if (p instanceof SkSubheaderPreference) level = 1;
        else p.getExtras().putInt("sk_level", level);
      }
      if (roles != null) {
        if (section.getPreferenceCount() > 0) {
          SkSubheaderPreference colours = new SkSubheaderPreference(ctx);
          colours.setTitle(R.string.pref_colors_category);
          colours.setPersistent(false);
          section.addPreference(colours);
          level = 1;
        }
        for (SkRole role : SkRole.values()) {
          if (role.section != roles) continue;
          SkRolePreference row = new SkRolePreference(ctx, role);
          row.getExtras().putInt("sk_level", level);
          row.setOnPreferenceClickListener(
              p -> {
                pickRole((SkRolePreference) p);
                return true;
              });
          section.addPreference(row);
        }
      }
      section.setOpen(SkPageState.isOpen(ctx, section.id()), false);
    }

    Preference settings = findPreference("sk_app_settings");
    if (settings != null) {
      settings.setOrder(0);
      settings.setOnPreferenceClickListener(
          p -> {
            startActivity(new Intent(ctx, ApplicationPreferencesActivity.class));
            return true;
          });
    }
    SkIndexPreference index =
        new SkIndexPreference(
            ctx,
            new SkIndexPreference.Host() {
              @NonNull
              @Override
              public List<SkSectionCategory> sections() {
                return orderedSections();
              }

              @Override
              public void jump(@NonNull SkSectionCategory section) {
                if (!section.isOpen()) section.setOpen(true, true);
                final String key = section.getKey();
                if (getListView() != null) getListView().post(() -> scrollToPreference(key));
              }

              @Override
              public void reorder(@NonNull List<String> order) {
                SkPageState.setOrder(ctx, order);
                applyOrder();
              }
            });
    index.setOrder(1);
    screen.addPreference(index);
    applyOrder();

    Preference resetOrder = findPreference("sk_reset_order");
    if (resetOrder != null) {
      resetOrder.setOnPreferenceClickListener(
          p -> {
            SkPageState.resetOrder(ctx);
            applyOrder();
            Preference idx = findPreference("sk_index");
            if (idx instanceof SkIndexPreference) ((SkIndexPreference) idx).redraw();
            return true;
          });
    }
    Preference resetColours = findPreference("sk_reset_colors");
    if (resetColours != null) {
      resetColours.setOnPreferenceClickListener(
          p -> {
            new AlertDialog.Builder(ctx)
                .setTitle(R.string.sk_reset_colors)
                .setMessage(R.string.sk_reset_colors_confirm)
                .setPositiveButton(
                    R.string.sk_reset_colors,
                    (d, w) -> {
                      SkRoles.resetAll(ctx);
                      requireActivity().recreate();
                    })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
            return true;
          });
    }
  }

  @NonNull
  private List<SkSectionCategory> orderedSections() {
    List<SkSectionCategory> out = new ArrayList<>();
    for (String id : SkPageState.order(requireContext(), defaultOrder)) {
      for (SkSectionCategory s : sections) if (s.id().equals(id)) out.add(s);
    }
    return out;
  }

  private void applyOrder() {
    int order = 10;
    for (SkSectionCategory s : orderedSections()) s.setOrder(order++);
  }

  /** The colour picker of one role; its Default button gives the role back its default. */
  private void pickRole(final SkRolePreference row) {
    final Context ctx = requireContext();
    final SkRole role = row.role();
    final int now = SkRoles.get(ctx, role);
    ColorPickerDialog.show(
        ctx,
        row.getTitle() == null ? "" : row.getTitle().toString(),
        now,
        color -> {
          // the picker is opaque; a role that is see-through by design keeps its alpha
          int alpha = role.kind == SkRole.Kind.ALPHA ? (now & 0xFF000000) : 0xFF000000;
          SkRoles.set(ctx, role, alpha | (color & 0x00FFFFFF));
          requireActivity().recreate();
        },
        () -> {
          SkRoles.reset(ctx, role);
          requireActivity().recreate();
        });
  }
}
