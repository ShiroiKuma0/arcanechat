package org.thoughtcrime.securesms.shiroikuma;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.widget.CompoundButton;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.widget.CompoundButtonCompat;

/**
 * shiroikuma fork: the outlined switch - a 2 dp fully round outline with the ground showing
 * through and a solid dot, right and bright when on, left and an opaque darker yellow when off.
 * Never filled, never a white ring. The four colours are roles.
 */
public final class SkSwitches {
  private SkSwitches() {}

  private static ColorStateList colors(@NonNull Context c, @NonNull SkRole on, @NonNull SkRole off) {
    int a = SkRoles.get(c, on), b = SkRoles.get(c, off);
    return new ColorStateList(
        new int[][] {
          {-android.R.attr.state_enabled, android.R.attr.state_checked},
          {-android.R.attr.state_enabled},
          {android.R.attr.state_checked},
          {}
        },
        new int[] {SkRoles.disabled(a), SkRoles.disabled(b), a, b});
  }

  public static void outline(@NonNull SwitchCompat v) {
    try {
      Context context = v.getContext();
      float d = context.getResources().getDisplayMetrics().density;
      int h = Math.round(28 * d);
      GradientDrawable track = new GradientDrawable();
      track.setShape(GradientDrawable.RECTANGLE);
      track.setCornerRadius(h / 2f);
      track.setColor(0);
      track.setStroke(Math.max(1, Math.round(2 * d)), 0xFFFFFFFF);
      track.setSize(Math.round(52 * d), h);
      int dotSize = Math.round(h * 0.46f);
      int inset = (h - dotSize) / 2;
      GradientDrawable dot = new GradientDrawable();
      dot.setShape(GradientDrawable.OVAL);
      dot.setColor(0xFFFFFFFF);
      dot.setSize(dotSize, dotSize);
      // The dot sits in a box of half the track's width and all of its height. A LayerDrawable
      // with layer insets, NOT an InsetDrawable: SwitchCompat takes an InsetDrawable's insets for
      // the thumb's optical insets and shrinks the TRACK by them on every side.
      int boxW = Math.round(26 * d);
      int insetH = Math.max(0, (boxW - dotSize) / 2);
      LayerDrawable thumb = new LayerDrawable(new android.graphics.drawable.Drawable[] {dot});
      thumb.setLayerInset(0, insetH, inset, insetH, inset);
      v.setThumbDrawable(thumb);
      v.setTrackDrawable(track);
      v.setThumbTintList(colors(context, SkRole.SWITCH_ON_DOT, SkRole.SWITCH_OFF_DOT));
      v.setTrackTintList(colors(context, SkRole.SWITCH_ON_FRAME, SkRole.SWITCH_OFF_FRAME));
      v.setSwitchMinWidth(Math.round(52 * d));
      v.setShowText(false);
      v.setSplitTrack(false);
      v.setMinHeight(0);
      v.setMinimumHeight(0);
    } catch (Throwable ignore) {
    }
  }

  /** A switch drawn as a drawable, for a menu row or a hand-built row. */
  @NonNull
  public static SkSwitchDrawable drawable(@NonNull Context c, boolean small) {
    float d = c.getResources().getDisplayMetrics().density;
    return new SkSwitchDrawable(
        SkRoles.get(c, SkRole.SWITCH_ON_FRAME),
        SkRoles.get(c, SkRole.SWITCH_ON_DOT),
        SkRoles.get(c, SkRole.SWITCH_OFF_FRAME),
        SkRoles.get(c, SkRole.SWITCH_OFF_DOT),
        Math.round((small ? 44 : 52) * d),
        Math.round((small ? 24 : 28) * d),
        Math.max(1, Math.round(2 * d)));
  }

  /** A check box or radio button stays a selection mark, in its own roles. */
  public static void mark(@NonNull CompoundButton v) {
    try {
      Context c = v.getContext();
      CompoundButtonCompat.setButtonTintList(v, colors(c, SkRole.CHECK_ON, SkRole.CHECK_OFF));
      v.setTextColor(SkPills.states(SkRoles.get(c, SkRole.MARK_LABEL)));
    } catch (Throwable ignore) {
    }
  }
}
