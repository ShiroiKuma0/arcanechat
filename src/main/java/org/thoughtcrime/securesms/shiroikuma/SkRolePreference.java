package org.thoughtcrime.securesms.shiroikuma;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import org.thoughtcrime.securesms.R;

/**
 * shiroikuma fork: the swatch row of one colour role on the UI page - label, the value as its
 * summary (or "Default"), and the colour at the right. A tap opens the colour picker, whose
 * Default button gives the role back its default.
 */
public class SkRolePreference extends Preference {
  private final SkRole role;

  public SkRolePreference(@NonNull Context context, @NonNull SkRole role) {
    super(context);
    this.role = role;
    setLayoutResource(R.layout.sk_pref_row);
    setKey("sk_row_" + role.name());
    setPersistent(false);
    setTitle(role.label);
    refresh();
  }

  @NonNull
  public SkRole role() {
    return role;
  }

  public void refresh() {
    Context c = getContext();
    int color = SkRoles.get(c, role);
    setSummary(
        SkRoles.isSet(c, role)
            ? String.format("#%08X", color)
            : c.getString(R.string.sk_color_default, String.format("#%08X", color)));
    float d = c.getResources().getDisplayMetrics().density;
    GradientDrawable swatch = new GradientDrawable();
    swatch.setShape(GradientDrawable.RECTANGLE);
    swatch.setCornerRadius(6 * d);
    swatch.setColor(color);
    swatch.setStroke(Math.max(1, Math.round(1.25f * d)), SkRoles.get(c, SkRole.DIVIDER));
    swatch.setSize(Math.round(40 * d), Math.round(26 * d));
    setIcon(swatch);
  }
}
