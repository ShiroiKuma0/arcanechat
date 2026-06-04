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

  // shiroikuma fork (Step 10): the colour/font/accent/chat-list-style customizations formerly wired
  // here now live on the dedicated "白い熊 ArcaneChat UI" page (ShiroikumaUiPreferenceFragment).
  // Appearance keeps the genuinely-upstream theme + the in-app language picker + chat background.

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);

    this.findPreference(Prefs.THEME_PREF).setOnPreferenceChangeListener(new ListSummaryListener());
    initializeListSummary((ListPreference) findPreference(Prefs.THEME_PREF));
    this.findPreference(Prefs.BACKGROUND_PREF)
        .setOnPreferenceClickListener(new BackgroundClickListener());

    initializeLanguagePref();
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
