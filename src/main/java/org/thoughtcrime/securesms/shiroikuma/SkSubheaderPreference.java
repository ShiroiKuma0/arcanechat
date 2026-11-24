package org.thoughtcrime.securesms.shiroikuma;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import org.thoughtcrime.securesms.R;

/** shiroikuma fork: a sub-heading inside a section of the UI page - bold, underlined, unpressable. */
public class SkSubheaderPreference extends Preference {
  public SkSubheaderPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
    setLayoutResource(R.layout.sk_pref_subheader);
    setSelectable(false);
  }

  public SkSubheaderPreference(Context context) {
    this(context, null);
  }

  @Override
  public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
    super.onBindViewHolder(holder);
    holder.setDividerAllowedAbove(false);
    holder.setDividerAllowedBelow(false);
    TextView title = (TextView) holder.findViewById(android.R.id.title);
    if (title != null) {
      title.setTextColor(SkRoles.get(getContext(), SkRole.HEADING));
      title.setTypeface(Typeface.create(title.getTypeface(), Typeface.BOLD));
      title.setPaintFlags(title.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
    }
  }
}
