package org.thoughtcrime.securesms;

import android.os.Bundle;
import org.thoughtcrime.securesms.preferences.ShiroikumaUiPreferenceFragment;
import org.thoughtcrime.securesms.util.ViewUtil;

/**
 * shiroikuma fork (Step 10): host for the consolidated "白い熊 ArcaneChat UI" customization page.
 *
 * <p>Reached from the conversation-list overflow menu and from the top of Settings. Theme + accent
 * overlay are applied for free by {@link BaseActionBarActivity} ({@code dynamicTheme.onCreate}).
 */
public class ShiroikumaUiActivity extends PassphraseRequiredActionBarActivity {

  @Override
  protected void onCreate(Bundle icicle, boolean ready) {
    setContentView(R.layout.activity_application_preferences);

    //noinspection ConstantConditions
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setTitle(R.string.shiroikuma_ui_category);

    // add padding to avoid content hidden behind system bars
    ViewUtil.applyWindowInsets(findViewById(R.id.fragment));

    if (icicle == null) {
      initFragment(R.id.fragment, new ShiroikumaUiPreferenceFragment());
    }
  }

  @Override
  public boolean onSupportNavigateUp() {
    finish();
    return true;
  }
}
