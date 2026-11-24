package org.thoughtcrime.securesms.shiroikuma;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.DialogPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.TwoStatePreference;
import java.util.Locale;
import org.thoughtcrime.securesms.R;

/**
 * shiroikuma fork: draws every row of every settings screen - upstream's and the UI page - by the
 * black-yellow theme's rules, in the one place all of them pass through (the adapter of {@code
 * CorrectedPreferenceFragment}), so upstream's preference XML is never edited:
 *
 * <ul>
 *   <li>an icon at the start of every label, coloured by what the row does and sized by the label;
 *   <li>a row that opens or runs something is an outlined pill that hugs its label, with a chevron
 *       when it opens something, its explanation beneath; the main action is 3 dp and bold;
 *   <li>control rows (a switch, a value that opens a picker, a swatch) stay plain;
 *   <li>a row with an explanation: bold label, dim-gold text, a 2 dp dim-gold rule at its left;
 *   <li>no minimum height, 2 dp above and below.
 * </ul>
 */
@SuppressLint("RestrictedApi")
public final class SkPreferenceRows {
  private SkPreferenceRows() {}

  /** The adapter {@code CorrectedPreferenceFragment} hands to its list. */
  public static final class Adapter extends PreferenceGroupAdapter {
    public Adapter(@NonNull PreferenceGroup group) {
      super(group);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder, int position) {
      super.onBindViewHolder(holder, position);
      try {
        Preference pref = getItem(position);
        if (pref != null) bind(pref, holder);
      } catch (Throwable ignore) {
      }
    }
  }

  // ---- which icon, in which colour: told from the row's key

  private static final Object[][] WORDS = {
    {"eximport", SkIcons.Icon.EXPORT, SkIcons.Does.SHARE},
    {"backup", SkIcons.Icon.EXPORT, SkIcons.Does.SHARE},
    {"automation_token", SkIcons.Icon.LOCK, SkIcons.Does.BLOCK},
    {"automation", SkIcons.Icon.BRANCH, SkIcons.Does.CONNECT},
    {"reset_order", SkIcons.Icon.SORT, SkIcons.Does.CONNECT},
    {"reset", SkIcons.Icon.RESET, SkIcons.Does.CONNECT},
    {"app_settings", SkIcons.Icon.GEAR, SkIcons.Does.VIEW},
    {"font_add", SkIcons.Icon.ADD, SkIcons.Does.CONNECT},
    {"font", SkIcons.Icon.TEXT, SkIcons.Does.VIEW},
    {"ticks_size", SkIcons.Icon.SIZE, SkIcons.Does.VIEW},
    {"ticks_spin", SkIcons.Icon.RESET, SkIcons.Does.VIEW},
    {"ticks_group", SkIcons.Icon.GROUP, SkIcons.Does.CONTACT},
    {"ticks", SkIcons.Icon.TICK2, SkIcons.Does.VIEW},
    {"chatlist_style", SkIcons.Icon.LIST, SkIcons.Does.VIEW},
    {"sk_row_", SkIcons.Icon.PALETTE, SkIcons.Does.VIEW},
    {"profile", SkIcons.Icon.PERSON, SkIcons.Does.CONTACT},
    {"shiroikuma_ui", SkIcons.Icon.PALETTE, SkIcons.Does.VIEW},
    {"category_chats", SkIcons.Icon.BUBBLE, SkIcons.Does.MESSAGE},
    {"privacy", SkIcons.Icon.PRIVACY, SkIcons.Does.BLOCK},
    {"category_notifications", SkIcons.Icon.BELL, SkIcons.Does.VIEW},
    {"appearance", SkIcons.Icon.PALETTE, SkIcons.Does.VIEW},
    {"multidevice", SkIcons.Icon.DEVICE, SkIcons.Does.CONNECT},
    {"connectivity", SkIcons.Icon.BRANCH, SkIcons.Does.CONNECT},
    {"advanced", SkIcons.Icon.SLIDERS, SkIcons.Does.VIEW},
    {"donate", SkIcons.Icon.STAR, SkIcons.Does.VIEW},
    {"help", SkIcons.Icon.HELP, SkIcons.Does.VIEW},
    {"blocked", SkIcons.Icon.BLOCK, SkIcons.Does.BLOCK},
    {"theme", SkIcons.Icon.PALETTE, SkIcons.Does.VIEW},
    {"language", SkIcons.Icon.GLOBE, SkIcons.Does.VIEW},
    {"background", SkIcons.Icon.IMAGE, SkIcons.Does.VIEW},
    {"view_log", SkIcons.Icon.LIST, SkIcons.Does.VIEW},
    {"relay", SkIcons.Icon.MAIL, SkIcons.Does.CONNECT},
    {"proxy", SkIcons.Icon.PRIVACY, SkIcons.Does.CONNECT},
    {"bcc_self", SkIcons.Icon.MAIL, SkIcons.Does.MESSAGE},
    {"webxdc", SkIcons.Icon.STORE, SkIcons.Does.VIEW},
    {"stats", SkIcons.Icon.GAUGE, SkIcons.Does.SHARE},
    {"mention", SkIcons.Icon.PERSON, SkIcons.Does.CONTACT},
    {"unifiedpush", SkIcons.Icon.BRANCH, SkIcons.Does.CONNECT},
    {"ringtone", SkIcons.Icon.BELL, SkIcons.Does.VIEW},
    {"vibrate", SkIcons.Icon.DEVICE, SkIcons.Does.VIEW},
    {"led", SkIcons.Icon.SPARK, SkIcons.Does.VIEW},
    {"notification_privacy", SkIcons.Icon.PRIVACY, SkIcons.Does.BLOCK},
    {"notify_calls", SkIcons.Icon.PHONE, SkIcons.Does.CONNECT},
    {"notification", SkIcons.Icon.BELL, SkIcons.Does.VIEW},
    {"battery", SkIcons.Icon.GAUGE, SkIcons.Does.CONNECT},
    {"reliable", SkIcons.Icon.BRANCH, SkIcons.Does.CONNECT},
    {"enter_sends", SkIcons.Icon.KEYBOARD, SkIcons.Does.MESSAGE},
    {"compression", SkIcons.Icon.IMAGE, SkIcons.Does.VIEW},
    {"download", SkIcons.Icon.SAVE, SkIcons.Does.SHARE},
    {"read_receipts", SkIcons.Icon.TICK2, SkIcons.Does.MESSAGE},
    {"screen_security", SkIcons.Icon.LOCK, SkIcons.Does.BLOCK},
    {"incognito", SkIcons.Icon.KEYBOARD, SkIcons.Does.BLOCK},
    {"autodel", SkIcons.Icon.DELETE, SkIcons.Does.DELETE},
  };

  @Nullable
  private static Object[] kind(@Nullable String key) {
    if (key == null) return null;
    String k = key.toLowerCase(Locale.ROOT);
    for (Object[] w : WORDS) if (k.contains((String) w[0])) return w;
    return null;
  }

  @NonNull
  public static SkIcons.Icon iconFor(@Nullable String key) {
    Object[] w = kind(key);
    return w != null ? (SkIcons.Icon) w[1] : SkIcons.Icon.SLIDERS;
  }

  @NonNull
  public static SkIcons.Does doesFor(@Nullable String key) {
    Object[] w = kind(key);
    return w != null ? (SkIcons.Does) w[2] : SkIcons.Does.VIEW;
  }

  // ---- the row

  private static boolean isAction(@NonNull Preference pref) {
    if (pref instanceof SkActionPreference) return true;
    if (pref.getLayoutResource() == R.layout.sk_pref_row) return false; // a control row of the page
    // Upstream's screens: a plain Preference that can be pressed opens a screen or runs something.
    return pref.getClass() == Preference.class && pref.isSelectable();
  }

  public static void bind(@NonNull Preference pref, @NonNull PreferenceViewHolder holder) {
    if (pref instanceof SkSectionCategory
        || pref instanceof SkSubheaderPreference
        || pref instanceof SkIndexPreference) {
      return; // they draw themselves
    }
    final Context c = pref.getContext();
    final View root = holder.itemView;
    TextView title = (TextView) holder.findViewById(android.R.id.title);
    TextView summary = (TextView) holder.findViewById(android.R.id.summary);
    if (title == null) return;

    if (pref instanceof PreferenceCategory) {
      title.setTextColor(SkRoles.get(c, SkRole.HEADING));
      title.setTypeface(Typeface.create(title.getTypeface(), Typeface.BOLD));
      return;
    }

    boolean enabled = pref.isEnabled();
    boolean action = isAction(pref);
    boolean hasSummary = summary != null && pref.getSummary() != null && pref.getSummary().length() > 0;
    boolean value = pref instanceof DialogPreference || pref instanceof SkRolePreference;

    SkActionPreference ap = pref instanceof SkActionPreference ? (SkActionPreference) pref : null;
    boolean main = ap != null && ap.isMain();
    boolean opens = ap != null ? ap.opens() : action;
    boolean destructive = ap != null && ap.isDestructive();

    // ---- label, icon
    int label = destructive ? SkRoles.get(c, SkRole.DELETE) : SkRoles.get(c, action ? SkRole.BUTTON_TEXT : SkRole.ROW_LABEL);
    if (!enabled) label = SkRoles.disabled(label);
    title.setTextColor(label);
    title.setSingleLine(false);
    boolean bold = action ? main : hasSummary;
    title.setTypeface(Typeface.create(title.getTypeface(), bold ? Typeface.BOLD : Typeface.NORMAL));

    // A row that brings an icon of its own (upstream's settings list) keeps it, in its role
    // colour; every other row gets a drawn one at the start of its label.
    int iconColor = destructive ? SkRoles.get(c, SkRole.DELETE) : SkIcons.color(c, doesFor(pref.getKey()));
    if (!enabled) iconColor = SkRoles.disabled(iconColor);
    boolean ownIcon = pref.getIcon() != null && !(pref instanceof SkRolePreference) && pref.getLayoutResource() != R.layout.sk_pref_row;
    if (ownIcon) {
      ImageView image = (ImageView) holder.findViewById(android.R.id.icon);
      if (image != null) image.setColorFilter(iconColor);
      SkIcons.onLabel(title, null, 0, opens && action, label);
    } else {
      SkIcons.onLabel(title, iconFor(pref.getKey()), iconColor, opens && action, label);
    }

    // ---- pill or plain
    if (action) {
      int frame = destructive ? SkRoles.get(c, SkRole.DELETE) : SkRoles.get(c, SkRole.BUTTON_FRAME);
      if (!enabled) frame = SkRoles.disabled(frame);
      title.setBackground(SkPills.frame(c, main, frame));
      title.setPaddingRelative(SkPills.dp(c, 14), SkPills.dp(c, 9), SkPills.dp(c, 14), SkPills.dp(c, 9));
      // The whole pill is the touch target; the row's rectangular highlight is switched off.
      root.setBackground(null);
      title.setOnClickListener(v -> root.performClick());
      title.setClickable(enabled);
    } else {
      title.setBackground(null);
      title.setPaddingRelative(0, 0, 0, 0);
      title.setOnClickListener(null);
      title.setClickable(false);
    }

    // ---- explanation and its rule
    if (summary != null) {
      summary.setAlpha(1f);
      int note = SkRoles.get(c, value ? SkRole.ROW_VALUE : SkRole.NOTE);
      summary.setTextColor(enabled ? note : SkRoles.disabled(note));
      // Beneath a pill, the explanation lines up with the pill's label, not its edge.
      summary.setPaddingRelative(action ? SkPills.dp(c, 14) : 0, action ? SkPills.dp(c, 2) : 0, 0, 0);
    }
    View column = title.getParent() instanceof View ? (View) title.getParent() : null;
    if (column != null && column != root) {
      if (hasSummary && !(pref instanceof SkRolePreference)) {
        column.setBackground(new LeftRule(SkRoles.get(c, SkRole.NOTE), SkPills.dp(c, 2)));
        column.setPaddingRelative(SkPills.dp(c, 12), SkPills.dp(c, 1), 0, SkPills.dp(c, 1));
      } else {
        column.setBackground(null);
        column.setPaddingRelative(0, 0, 0, 0);
      }
    }

    // ---- only as tall as its content
    root.setMinimumHeight(0);
    int pad = SkPills.dp(c, action ? 3 : 2);
    int level = pref.getExtras().getInt("sk_level", 0);
    int start = pref.getLayoutResource() == R.layout.sk_pref_row ? SkPills.dp(c, 16 + 16 * level) : root.getPaddingStart();
    root.setPaddingRelative(start, pad, root.getPaddingEnd(), pad);

    // A switch is told from the row, never from a greyed label alone.
    if (pref instanceof TwoStatePreference) holder.itemView.setAlpha(1f);
  }

  /** A thin bar down the whole left edge of an explained row. */
  private static final class LeftRule extends Drawable {
    private final Paint paint = new Paint();
    private final int width;

    LeftRule(int color, int width) {
      paint.setColor(color);
      this.width = width;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
      android.graphics.Rect b = getBounds();
      boolean rtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
      if (rtl) canvas.drawRect(b.right - width, b.top, b.right, b.bottom, paint);
      else canvas.drawRect(b.left, b.top, b.left + width, b.bottom, paint);
    }

    @Override
    public void setAlpha(int alpha) {}

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {}

    @Override
    public int getOpacity() {
      return PixelFormat.TRANSLUCENT;
    }
  }
}
