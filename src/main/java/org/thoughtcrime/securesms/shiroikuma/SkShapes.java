package org.thoughtcrime.securesms.shiroikuma;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

/**
 * shiroikuma fork: the fork's own shapes, built in code from the colour roles - no XML drawable
 * holds their colours. The rows of the chat list in their six styles, the message bar's frame
 * and the rings of its buttons.
 */
public final class SkShapes {
  private SkShapes() {}

  /** One state of a chat-list row: ground, an optional frame, an optional bar at its start. */
  private static Drawable rowState(
      Context c, @ColorInt int ground, @ColorInt int frame, boolean boxed, boolean bar) {
    float d = c.getResources().getDisplayMetrics().density;
    if (bar) {
      LayerDrawable layers =
          new LayerDrawable(new Drawable[] {new ColorDrawable(frame), new ColorDrawable(ground)});
      layers.setLayerInset(1, Math.round(4 * d), 0, 0, 0);
      return layers;
    }
    GradientDrawable g = new GradientDrawable();
    g.setColor(ground);
    if (boxed) {
      g.setCornerRadius(12 * d);
      g.setStroke(Math.max(1, Math.round(1.5f * d)), frame);
    }
    return g;
  }

  /**
   * The background of a chat-list row: {@code style} is one of the Prefs.CHATLIST_* values. Keeps
   * the tap ripple and the selected tint of multi-select.
   */
  @NonNull
  public static Drawable chatRow(@NonNull Context c, @NonNull String style, boolean pinned) {
    float d = c.getResources().getDisplayMetrics().density;
    boolean filled = "filled".equals(style);
    boolean boxed = filled || "cards".equals(style);
    boolean bar = "accentbar".equals(style);
    int base = SkRoles.get(c, SkRole.CARD_GROUND);
    int ground =
        pinned
            ? SkRoles.get(c, SkRole.PINNED_GROUND)
            : filled ? ColorUtils.compositeColors(SkRoles.get(c, SkRole.CARD_FILL), base) : base;
    int frame = SkRoles.get(c, SkRole.CARD_FRAME);
    int selected = ColorUtils.compositeColors(SkRoles.get(c, SkRole.SELECTION), ground);
    StateListDrawable states = new StateListDrawable();
    states.addState(
        new int[] {android.R.attr.state_selected}, rowState(c, selected, frame, boxed, bar));
    states.addState(new int[] {}, rowState(c, ground, frame, boxed, bar));
    GradientDrawable mask = new GradientDrawable();
    mask.setColor(Color.WHITE);
    if (boxed) mask.setCornerRadius(12 * d);
    return new RippleDrawable(ColorStateList.valueOf(SkRoles.alpha(frame, 0.3f)), states, mask);
  }
}
