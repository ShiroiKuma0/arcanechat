package org.thoughtcrime.securesms.shiroikuma;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import androidx.preference.Preference;
import org.thoughtcrime.securesms.R;

/**
 * shiroikuma fork: a row of the UI page that does something when tapped - opens a screen, a
 * panel or a picker, or runs an action. Drawn by {@link SkPreferenceRows} as an outlined pill that
 * hugs its label, its explanation beneath; the main action of a section is 3 dp and bold, and a
 * row that opens something carries a chevron.
 */
public class SkActionPreference extends Preference {
  private boolean main;
  private boolean opens;
  private boolean destructive;

  public SkActionPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
    setLayoutResource(R.layout.sk_pref_row);
    if (attrs != null) {
      TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SkActionPreference);
      main = a.getBoolean(R.styleable.SkActionPreference_skMain, false);
      opens = a.getBoolean(R.styleable.SkActionPreference_skOpens, false);
      destructive = a.getBoolean(R.styleable.SkActionPreference_skDestructive, false);
      a.recycle();
    }
  }

  public SkActionPreference(Context context) {
    this(context, null);
  }

  public SkActionPreference set(boolean main, boolean opens, boolean destructive) {
    this.main = main;
    this.opens = opens;
    this.destructive = destructive;
    return this;
  }

  public boolean isMain() {
    return main;
  }

  public boolean opens() {
    return opens;
  }

  public boolean isDestructive() {
    return destructive;
  }
}
