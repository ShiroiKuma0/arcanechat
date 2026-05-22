package org.thoughtcrime.securesms.preferences;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import java.util.Arrays;
import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.util.Prefs;

public class AppearancePreferenceFragment extends ListSummaryPreferenceFragment {

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

    initializeLanguagePref();
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
