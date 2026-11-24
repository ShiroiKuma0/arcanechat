package org.thoughtcrime.securesms.shiroikuma;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.widget.TextView;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.thoughtcrime.securesms.R;

/**
 * shiroikuma fork: the black-yellow theme's button - an outlined pill, never filled. The main
 * action of a block has a 3 dp frame and a bold label, every other action 1.25 dp and normal
 * weight; a deleting action lends its red to frame and label. Colours are the settable roles of
 * {@link SkRole}.
 *
 * <p>Nothing in a layout asks for this: {@link SkViewInflater} hands every inflated button to
 * this class, so a screen nobody remembered is covered as well. A button built in code calls
 * {@link #button} itself. Left alone on purpose: a button that is only a glyph (no label).
 */
public final class SkPills {
  private SkPills() {}

  public static final float MAIN_DP = 3f;
  public static final float REGULAR_DP = 1.25f;

  /** Labels that delete something: a button carrying one is red. */
  private static final int[] DESTRUCTIVE = {
    R.string.delete,
    R.string.delete_for_me,
    R.string.delete_for_everyone,
    R.string.menu_delete_chat,
    R.string.clear_chat,
    R.string.delete_contact,
    R.string.delete_account,
    R.string.remove_desktop,
    R.string.remove_transport,
    R.string.remove_group_image,
    R.string.pref_reset_ui,
    R.string.sk_reset_colors,
  };

  public static int dp(@NonNull Context context, float value) {
    return Math.round(value * context.getResources().getDisplayMetrics().density);
  }

  @NonNull
  public static ColorStateList states(@ColorInt int color) {
    return new ColorStateList(
        new int[][] {{-android.R.attr.state_enabled}, {}},
        new int[] {SkRoles.disabled(color), color});
  }

  public static boolean isDestructiveLabel(@NonNull Context context, @Nullable CharSequence label) {
    if (TextUtils.isEmpty(label)) return false;
    String text = label.toString().trim();
    for (int res : DESTRUCTIVE) {
      try {
        if (context.getString(res).equalsIgnoreCase(text)) return true;
      } catch (Throwable ignore) {
      }
    }
    return false;
  }

  /** An outlined pill as a background for any view (a row, a hand-built button). */
  @NonNull
  public static Drawable frame(@NonNull Context context, boolean main, @ColorInt int color) {
    return frame(context, main ? MAIN_DP : REGULAR_DP, color, Color.TRANSPARENT);
  }

  @NonNull
  public static Drawable frame(
      @NonNull Context context, float strokeDp, @ColorInt int color, @ColorInt int ground) {
    float d = context.getResources().getDisplayMetrics().density;
    GradientDrawable frame = new GradientDrawable();
    frame.setColor(ground);
    frame.setCornerRadius(100 * d);
    frame.setStroke(Math.max(1, Math.round(strokeDp * d)), color);
    GradientDrawable mask = new GradientDrawable();
    mask.setCornerRadius(100 * d);
    mask.setColor(Color.WHITE);
    return new RippleDrawable(ColorStateList.valueOf(SkRoles.alpha(color, 0.3f)), frame, mask);
  }

  /** A ring: the round form of the pill, for a glyph button that is a control of its own. */
  @NonNull
  public static Drawable ring(
      @NonNull Context context, float strokeDp, @ColorInt int color, @ColorInt int ground) {
    float d = context.getResources().getDisplayMetrics().density;
    GradientDrawable ring = new GradientDrawable();
    ring.setShape(GradientDrawable.OVAL);
    ring.setColor(ground);
    ring.setStroke(Math.max(1, Math.round(strokeDp * d)), color);
    GradientDrawable mask = new GradientDrawable();
    mask.setShape(GradientDrawable.OVAL);
    mask.setColor(Color.WHITE);
    return new RippleDrawable(ColorStateList.valueOf(SkRoles.alpha(color, 0.3f)), ring, mask);
  }

  /** A framed panel with slightly rounded corners: dialogs, popups, the message bar. */
  @NonNull
  public static GradientDrawable panel(
      @NonNull Context context,
      float strokeDp,
      float radiusDp,
      @ColorInt int frame,
      @ColorInt int ground) {
    float d = context.getResources().getDisplayMetrics().density;
    GradientDrawable g = new GradientDrawable();
    g.setColor(ground);
    g.setCornerRadius(radiusDp * d);
    if (strokeDp > 0) g.setStroke(Math.max(1, Math.round(strokeDp * d)), frame);
    return g;
  }

  @ColorInt
  private static int ground(@NonNull Context context) {
    return SkRoles.isSet(context, SkRole.BUTTON_GROUND)
        ? SkRoles.get(context, SkRole.BUTTON_GROUND)
        : Color.TRANSPARENT;
  }

  /** Make {@code button} a pill now, red when its label deletes. */
  public static void button(@NonNull TextView button, boolean main) {
    button(button, main, isDestructiveLabel(button.getContext(), button.getText()));
  }

  public static void button(@NonNull TextView button, boolean main, boolean destructive) {
    Context context = button.getContext();
    int frame =
        destructive
            ? SkRoles.get(context, SkRole.DELETE)
            : SkRoles.get(context, SkRole.BUTTON_FRAME);
    int text = destructive ? frame : SkRoles.get(context, SkRole.BUTTON_TEXT);
    style(button, main, frame, text, ground(context), 0, 0);
  }

  /** A pill in a colour that carries a meaning (answer green, decline red). */
  public static void colored(@NonNull TextView button, boolean main, @ColorInt int color) {
    style(button, main, color, color, ground(button.getContext()), 0, 0);
  }

  /** A button of a dialog's button bar: they touch each other, so the pill sits in a small inset. */
  public static void dialogButton(@NonNull TextView button, boolean main, boolean destructive) {
    Context context = button.getContext();
    int frame =
        destructive
            ? SkRoles.get(context, SkRole.DELETE)
            : SkRoles.get(context, SkRole.DIALOG_BUTTON_FRAME);
    int text = destructive ? frame : SkRoles.get(context, SkRole.DIALOG_BUTTON_TEXT);
    style(button, main, frame, text, Color.TRANSPARENT, 4, 5);
  }

  private static void style(
      @NonNull TextView button,
      boolean main,
      @ColorInt int frame,
      @ColorInt int text,
      @ColorInt int ground,
      int insetHDp,
      int insetVDp) {
    try {
      Context context = button.getContext();
      Drawable pill = frame(context, main ? MAIN_DP : REGULAR_DP, frame, ground);
      if (!button.isEnabled()) pill.setAlpha(97);
      int ih = dp(context, insetHDp), iv = dp(context, insetVDp);
      button.setBackground(ih > 0 || iv > 0 ? new InsetDrawable(pill, ih, iv, ih, iv) : pill);
      button.setTextColor(states(text));
      button.setTypeface(
          Typeface.create(button.getTypeface(), main ? Typeface.BOLD : Typeface.NORMAL));
      button.setAllCaps(false);
      int h = dp(context, 18) + ih, v = dp(context, 9) + iv;
      button.setPaddingRelative(h, v, h, v);
      button.setMinimumHeight(0);
      button.setMinHeight(0);
      button.setMinimumWidth(0);
      button.setMinWidth(0);
      button.setStateListAnimator(null);
      button.setElevation(0);
      // A drawable beside the label is coloured with the label.
      Drawable[] ds = button.getCompoundDrawablesRelative();
      for (Drawable d : ds) if (d != null) d.mutate().setTint(text);
    } catch (Throwable ignore) {
    }
  }

  /**
   * The rows of a dialog's action list (a list whose rows each DO something, such as "Copy to
   * clipboard") as full-width outlined pills with their icon inside at the left, like the rows
   * of a menu. Call it after {@code show()}. A list of pure choices is left as it is.
   */
  public static void actionList(
      @NonNull androidx.appcompat.app.AlertDialog dialog,
      @NonNull final SkIcons.Icon icon,
      @NonNull final SkIcons.Does does) {
    final android.widget.ListView list = dialog.getListView();
    if (list == null) return;
    final Context context = list.getContext();
    list.setDivider(null);
    list.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
    list.setOnHierarchyChangeListener(
        new android.view.ViewGroup.OnHierarchyChangeListener() {
          @Override
          public void onChildViewAdded(android.view.View parent, android.view.View child) {
            if (!(child instanceof TextView)) return;
            TextView row = (TextView) child;
            int h = dp(context, 8), v = dp(context, 3);
            row.setBackground(
                new InsetDrawable(
                    frame(context, REGULAR_DP, SkRoles.get(context, SkRole.MENU_ROW_FRAME), Color.TRANSPARENT),
                    h, v, h, v));
            row.setTextColor(SkRoles.get(context, SkRole.MENU_LABEL));
            row.setPaddingRelative(dp(context, 22), dp(context, 12), dp(context, 22), dp(context, 12));
            row.setMinHeight(0);
            row.setMinimumHeight(0);
            SkIcons.onLabel(row, icon, SkIcons.color(context, does), false, 0);
          }

          @Override
          public void onChildViewRemoved(android.view.View parent, android.view.View child) {}
        });
  }

  /**
   * A tile of a picker (the attachment panel): a ring round a bare glyph, ring and glyph in the
   * colour of what the tile does - never a filled disc.
   */
  public static void tile(@Nullable android.widget.ImageView tile, @NonNull SkIcons.Does does) {
    if (tile == null) return;
    try {
      Context context = tile.getContext();
      int color = SkIcons.color(context, does);
      tile.setBackground(ring(context, REGULAR_DP * 1.6f, color, Color.TRANSPARENT));
      tile.setColorFilter(color);
    } catch (Throwable ignore) {
    }
  }
}
