package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import org.thoughtcrime.securesms.R;

/**
 * shiroikuma fork — the automation-token row of the Export/Import section: tapping the row copies
 * the token, and a "Regenerate" button sits on the right (inside the row's widget frame, from
 * {@code @layout/preference_widget_regenerate}).
 */
public class AutomationTokenPreference extends Preference {

  @Nullable private Runnable onRegenerate;

  public AutomationTokenPreference(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public AutomationTokenPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public AutomationTokenPreference(Context context) {
    super(context);
  }

  public void setOnRegenerateListener(@Nullable Runnable onRegenerate) {
    this.onRegenerate = onRegenerate;
  }

  @Override
  public void onBindViewHolder(PreferenceViewHolder holder) {
    super.onBindViewHolder(holder);
    View button = holder.findViewById(R.id.automation_regenerate);
    if (button == null) return;
    // the button consumes the touch, so tapping it does not also fire the row's copy action
    button.setOnClickListener(
        v -> {
          if (onRegenerate != null) onRegenerate.run();
        });
  }
}
