package org.thoughtcrime.securesms.preferences;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import java.io.File;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ColorPickerDialog;
import org.thoughtcrime.securesms.components.FontPickerDialog;
import org.thoughtcrime.securesms.util.FontUtil;
import org.thoughtcrime.securesms.util.Prefs;

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

    Preference resetPref = findPreference("pref_reset_ui");
    if (resetPref != null) {
      resetPref.setOnPreferenceClickListener(
          p -> {
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
            return true;
          });
    }
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
