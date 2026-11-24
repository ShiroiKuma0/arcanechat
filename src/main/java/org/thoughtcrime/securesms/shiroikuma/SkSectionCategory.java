package org.thoughtcrime.securesms.shiroikuma;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceViewHolder;
import org.thoughtcrime.securesms.R;

/**
 * shiroikuma fork: a top-level section of the UI page. Its heading - bold, underlined as wide as
 * its text, with the section's icon at the left in the section's colour and a chevron at the
 * right - folds and unfolds its rows on a tap. Folded by default; the state is remembered
 * ({@link SkPageState}).
 */
public class SkSectionCategory extends PreferenceCategory {
  private SkIcons.Icon icon = SkIcons.Icon.FRAME;
  private SkRole color = SkRole.SECTION_BASE;
  private boolean open;

  public SkSectionCategory(Context context, AttributeSet attrs) {
    super(context, attrs);
    setLayoutResource(R.layout.sk_pref_section);
  }

  private static final String HIDDEN = "sk_hidden";

  /**
   * A row that is only shown under a condition of its own (the token row) says so here, so that
   * unfolding its section does not bring it back.
   */
  public static void setWanted(@NonNull Preference row, boolean wanted) {
    row.getExtras().putBoolean(HIDDEN, !wanted);
    androidx.preference.PreferenceGroup parent = row.getParent();
    boolean open = !(parent instanceof SkSectionCategory) || ((SkSectionCategory) parent).open;
    row.setVisible(wanted && open);
  }

  /** The section's id: its key without the {@code sk_sec_} prefix. */
  @NonNull
  public String id() {
    String key = getKey();
    return key != null && key.startsWith("sk_sec_") ? key.substring(7) : String.valueOf(key);
  }

  public void look(@NonNull SkIcons.Icon icon, @NonNull SkRole color) {
    this.icon = icon;
    this.color = color;
  }

  @NonNull
  public SkIcons.Icon icon() {
    return icon;
  }

  @NonNull
  public SkRole color() {
    return color;
  }

  public boolean isOpen() {
    return open;
  }

  /** Show or hide the section's rows; the heading and the line above it always stay. */
  public void setOpen(boolean value, boolean remember) {
    open = value;
    for (int i = 0; i < getPreferenceCount(); i++) {
      Preference p = getPreference(i);
      p.setVisible(value && !p.getExtras().getBoolean(HIDDEN, false));
    }
    if (remember) SkPageState.setOpen(getContext(), id(), value);
    notifyChanged();
  }

  @Override
  public boolean addPreference(@NonNull Preference preference) {
    boolean added = super.addPreference(preference);
    if (added && !open) preference.setVisible(false);
    return added;
  }

  @Override
  public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
    super.onBindViewHolder(holder);
    Context c = getContext();
    holder.setDividerAllowedAbove(false);
    holder.setDividerAllowedBelow(false);
    View divider = holder.findViewById(R.id.sk_divider);
    if (divider != null) divider.setBackgroundColor(SkRoles.get(c, SkRole.DIVIDER));
    TextView title = (TextView) holder.findViewById(android.R.id.title);
    if (title != null) {
      int heading = SkRoles.get(c, SkRole.HEADING);
      title.setTextColor(heading);
      title.setTypeface(Typeface.create(title.getTypeface(), Typeface.BOLD));
      title.setPaintFlags(title.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
      title.setAllCaps(false);
      SkIcons.onLabel(title, icon, SkRoles.get(c, color), false, 0);
    }
    TextView chevron = (TextView) holder.findViewById(R.id.sk_chevron);
    if (chevron != null) {
      chevron.setText(open ? "⌄" : "›");
      chevron.setTextColor(SkRoles.get(c, SkRole.HEADING));
    }
    holder.itemView.setClickable(true);
    holder.itemView.setOnClickListener(v -> setOpen(!open, true));
  }
}
