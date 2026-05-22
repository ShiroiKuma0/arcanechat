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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.os.LocaleListCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import java.io.File;
import java.util.Arrays;
import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.util.FontUtil;
import org.thoughtcrime.securesms.util.Prefs;

public class AppearancePreferenceFragment extends ListSummaryPreferenceFragment {

  // shiroikuma fork (Step 3): pick a .ttf/.otf file directly and reference it in place (no copy)
  // by its real path; loading arbitrary paths needs All-files access on API 30+. After a grant
  // returns we proceed straight to the file picker.
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
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);

    this.findPreference(Prefs.THEME_PREF).setOnPreferenceChangeListener(new ListSummaryListener());
    initializeListSummary((ListPreference) findPreference(Prefs.THEME_PREF));
    this.findPreference(Prefs.BACKGROUND_PREF)
        .setOnPreferenceClickListener(new BackgroundClickListener());

    initializeColorPref(Prefs.COLOR_MESSAGE_TEXT_PREF);
    initializeColorPref(Prefs.COLOR_BUBBLE_FILL_PREF);
    initializeColorPref(Prefs.COLOR_BUBBLE_BORDER_PREF);
    initializeColorPref(Prefs.COLOR_CONVERSATION_BG_PREF);
    initializeColorPref(Prefs.COLOR_LIST_TITLE_PREF);
    initializeColorPref(Prefs.COLOR_LIST_PREVIEW_PREF);
    initializeColorPref(Prefs.COLOR_FAB_PREF);

    initializeFontPref(Prefs.FONT_CHAT_TEXT, "pref_font_chat_text");
    initializeFontPref(Prefs.FONT_CONV_TITLE, "pref_font_conv_title");
    initializeFontPref(Prefs.FONT_LIST_TITLE, "pref_font_list_title");
    initializeFontPref(Prefs.FONT_LIST_PREVIEW, "pref_font_list_preview");
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

    initializeLanguagePref();
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

  // shiroikuma fork (Step 3): a font-category preference - shows a summary of the current
  // family/weight/size and opens the font picker on tap. Applies on next render.
  private void initializeFontPref(String category, String key) {
    Preference pref = findPreference(key);
    if (pref == null) return;
    Context c = getContext();
    pref.setSummary(
        org.thoughtcrime.securesms.util.FontUtil.describe(
            Prefs.getFontFamily(c, category),
            Prefs.getFontWeight(c, category),
            Prefs.getFontSize(c, category)));
    pref.setOnPreferenceClickListener(
        p -> {
          Context ctx = getContext();
          org.thoughtcrime.securesms.components.FontPickerDialog.show(
              ctx,
              p.getTitle() == null ? "" : p.getTitle().toString(),
              Prefs.getFontFamily(ctx, category),
              Prefs.getFontWeight(ctx, category),
              Prefs.getFontSize(ctx, category),
              (family, weight, size) -> {
                Prefs.setFont(ctx, category, family, weight, size);
                p.setSummary(
                    org.thoughtcrime.securesms.util.FontUtil.describe(family, weight, size));
              });
          return true;
        });
  }

  // shiroikuma fork (Step 2): a colour-role preference - shows a swatch of the current colour and
  // opens the RGB/hex picker on tap. The colour applies on next render (ConversationItem reads
  // Prefs at bind time), so no activity recreation is needed here.
  private void initializeColorPref(String key) {
    Preference pref = findPreference(key);
    if (pref == null) return;
    updateColorSwatch(pref, getColorFor(key));
    pref.setOnPreferenceClickListener(
        p -> {
          org.thoughtcrime.securesms.components.ColorPickerDialog.show(
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
      case Prefs.COLOR_BUBBLE_FILL_PREF:
        return Prefs.getBubbleFillColor(c);
      case Prefs.COLOR_BUBBLE_BORDER_PREF:
        return Prefs.getBubbleBorderColor(c);
      case Prefs.COLOR_CONVERSATION_BG_PREF:
        return Prefs.getConversationBackgroundColor(c);
      case Prefs.COLOR_LIST_TITLE_PREF:
        return Prefs.getListTitleColor(c);
      case Prefs.COLOR_LIST_PREVIEW_PREF:
        return Prefs.getListPreviewColor(c);
      case Prefs.COLOR_FAB_PREF:
        return Prefs.getFabColor(c);
      case Prefs.COLOR_MESSAGE_TEXT_PREF:
      default:
        return Prefs.getMessageTextColor(c);
    }
  }

  private void setColorFor(String key, int color) {
    Context c = getContext();
    switch (key) {
      case Prefs.COLOR_BUBBLE_FILL_PREF:
        Prefs.setBubbleFillColor(c, color);
        break;
      case Prefs.COLOR_BUBBLE_BORDER_PREF:
        Prefs.setBubbleBorderColor(c, color);
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
      case Prefs.COLOR_FAB_PREF:
        Prefs.setFabColor(c, color);
        break;
      case Prefs.COLOR_MESSAGE_TEXT_PREF:
      default:
        Prefs.setMessageTextColor(c, color);
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

  // shiroikuma fork: in-app language override via AndroidX per-app locales.
  // AppCompatDelegate is the single source of truth (persisted by the manifest
  // AppLocalesMetadataHolderService on API < 33, by the framework on API 33+),
  // so the ListPreference is non-persistent and just mirrors the current locale.
  private void initializeLanguagePref() {
    ListPreference languagePref = (ListPreference) findPreference(Prefs.LANGUAGE_PREF);
    if (languagePref == null) return;

    LocaleListCompat current = AppCompatDelegate.getApplicationLocales();
    languagePref.setValue(current.isEmpty() ? "" : current.get(0).toLanguageTag());

    languagePref.setOnPreferenceChangeListener(
        (preference, value) -> {
          String tag = value == null ? "" : value.toString();
          LocaleListCompat locales =
              tag.isEmpty()
                  ? LocaleListCompat.getEmptyLocaleList()
                  : LocaleListCompat.forLanguageTags(tag);
          updateListSummary(preference, value);
          AppCompatDelegate.setApplicationLocales(locales);
          return true;
        });
    initializeListSummary(languagePref);
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_appearance);
  }

  @Override
  public void onStart() {
    super.onStart();
    getPreferenceScreen()
        .getSharedPreferences()
        .registerOnSharedPreferenceChangeListener((ApplicationPreferencesActivity) getActivity());
  }

  @Override
  public void onResume() {
    super.onResume();
    ((ApplicationPreferencesActivity) getActivity())
        .getSupportActionBar()
        .setTitle(R.string.pref_appearance);
    String imagePath = Prefs.getBackgroundImagePath(getContext(), dcContext.getAccountId());
    String backgroundString;
    if (imagePath.isEmpty()) {
      backgroundString = this.getString(R.string.def);
    } else {
      backgroundString = this.getString(R.string.custom);
    }
    this.findPreference(Prefs.BACKGROUND_PREF).setSummary(backgroundString);
  }

  @Override
  public void onStop() {
    super.onStop();
    getPreferenceScreen()
        .getSharedPreferences()
        .unregisterOnSharedPreferenceChangeListener((ApplicationPreferencesActivity) getActivity());
  }

  public static CharSequence getSummary(Context context) {
    String[] themeEntries = context.getResources().getStringArray(R.array.pref_theme_entries);
    String[] themeEntryValues = context.getResources().getStringArray(R.array.pref_theme_values);
    int themeIndex = Arrays.asList(themeEntryValues).indexOf(Prefs.getTheme(context));
    if (themeIndex == -1) themeIndex = 0;

    String imagePath =
        Prefs.getBackgroundImagePath(context, DcHelper.getContext(context).getAccountId());
    String backgroundString;
    if (imagePath.isEmpty()) {
      backgroundString = context.getString(R.string.def);
    } else {
      backgroundString = context.getString(R.string.custom);
    }

    // adding combined strings as "Read receipt: %1$s, Screen lock: %1$s, "
    // makes things inflexible on changes and/or adds lot of additional works to programmers.
    // however, if needed, we can refine this later.
    return themeEntries[themeIndex]
        + ", "
        + context.getString(R.string.pref_background)
        + " "
        + backgroundString;
  }

  private class BackgroundClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(@NonNull Preference preference) {
      Intent intent = new Intent(getContext(), ChatBackgroundActivity.class);
      requireActivity().startActivity(intent);
      return true;
    }
  }
}
