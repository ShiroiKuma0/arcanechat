package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.AccessibilityUtil;
import org.thoughtcrime.securesms.util.Prefs;
import org.thoughtcrime.securesms.util.ViewUtil;

public class DeliveryStatusView {

  private final ImageView deliveryIndicator;
  private final Context context;
  private static RotateAnimation prepareAnimation;
  private static RotateAnimation sendingAnimation;
  private boolean animated;

  public DeliveryStatusView(ImageView deliveryIndicator) {
    this.deliveryIndicator = deliveryIndicator;
    this.context = deliveryIndicator.getContext();
  }

  /**
   * shiroikuma fork (Step 11): maps a configured glyph value to its vector drawable. {@code "none"}
   * (or anything unknown) returns 0, which hides the indicator entirely.
   */
  public static int glyphRes(String glyph) {
    if (glyph == null) return 0;
    switch (glyph) {
      case "tick1":
        return R.drawable.shiroikuma_tick_1;
      case "tick2":
        return R.drawable.shiroikuma_tick_2;
      case "tick3":
        return R.drawable.shiroikuma_tick_3;
      case "clock":
        return R.drawable.shiroikuma_tick_clock;
      case "dot":
        return R.drawable.shiroikuma_tick_dot;
      case "circle_check":
        return R.drawable.shiroikuma_tick_circle_check;
      case "arrow_up":
        return R.drawable.shiroikuma_tick_arrow_up;
      case "bang":
        return R.drawable.shiroikuma_tick_bang;
      case "none":
      default:
        return 0;
    }
  }

  /**
   * shiroikuma fork (Step 11): renders a glyph exactly as the chat footer would - tinted with the
   * state's colour and scaled to a given height, aspect preserved - as a plain {@link Drawable}.
   * Used for the previews on the 白い熊 UI page and in the glyph picker, so what you see there is
   * what lands next to the message.
   *
   * @return null when the glyph is {@code none} (nothing is drawn for that state).
   */
  @Nullable
  public static Drawable glyphPreview(Context context, String glyph, int color, int heightDp) {
    int res = glyphRes(glyph);
    if (res == 0) return null;
    Drawable src = ContextCompat.getDrawable(context, res);
    if (src == null) return null;
    src = src.mutate();
    src.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));

    int h = Math.max(1, ViewUtil.dpToPx(context, heightDp));
    int iw = src.getIntrinsicWidth();
    int ih = src.getIntrinsicHeight();
    int w = ih > 0 ? Math.max(1, h * iw / ih) : h;

    Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    src.setBounds(0, 0, w, h);
    src.draw(new Canvas(bitmap));
    return new BitmapDrawable(context.getResources(), bitmap);
  }

  /**
   * shiroikuma fork (Step 11): applies the configured glyph, colour and size for one delivery
   * state. Height is the configured size in dp and width is wrap_content with adjustViewBounds, so
   * the wider double/triple ticks keep their aspect instead of being squashed.
   *
   * @return false when the state is configured to {@code none} and the indicator was hidden.
   */
  private boolean applyStyle(String state) {
    int res = glyphRes(Prefs.getTickGlyph(context, state));
    if (res == 0) {
      setNone();
      return false;
    }
    deliveryIndicator.setVisibility(View.VISIBLE);
    deliveryIndicator.setImageResource(res);
    deliveryIndicator.setColorFilter(Prefs.getTickColor(context, state));
    deliveryIndicator.setAdjustViewBounds(true);
    deliveryIndicator.setScaleType(ImageView.ScaleType.FIT_CENTER);

    int px = ViewUtil.dpToPx(context, Prefs.getTickSize(context));
    ViewGroup.LayoutParams params = deliveryIndicator.getLayoutParams();
    if (params != null
        && (params.height != px || params.width != ViewGroup.LayoutParams.WRAP_CONTENT)) {
      params.height = px;
      params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
      deliveryIndicator.setLayoutParams(params);
    }
    return true;
  }

  private void animatePrepare() {
    if (AccessibilityUtil.areAnimationsDisabled(context)) return;
    if (!Prefs.isTickSpin(context)) return;

    if (prepareAnimation == null) {
      prepareAnimation =
          new RotateAnimation(
              360f, 0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
      prepareAnimation.setInterpolator(new LinearInterpolator());
      prepareAnimation.setDuration(2500);
      prepareAnimation.setRepeatCount(Animation.INFINITE);
    }

    deliveryIndicator.startAnimation(prepareAnimation);
    animated = true;
  }

  private void animateSending() {
    if (AccessibilityUtil.areAnimationsDisabled(context)) return;
    if (!Prefs.isTickSpin(context)) return;

    if (sendingAnimation == null) {
      sendingAnimation =
          new RotateAnimation(
              0, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
      sendingAnimation.setInterpolator(new LinearInterpolator());
      sendingAnimation.setDuration(1500);
      sendingAnimation.setRepeatCount(Animation.INFINITE);
    }

    deliveryIndicator.startAnimation(sendingAnimation);
    animated = true;
  }

  private void clearAnimation() {
    if (animated) {
      deliveryIndicator.clearAnimation();
      animated = false;
    }
  }

  public void setNone() {
    deliveryIndicator.setVisibility(View.GONE);
    deliveryIndicator.clearAnimation();
    animated = false;
  }

  public void setDownloading() {
    if (!applyStyle(Prefs.TICK_SENDING)) return;
    deliveryIndicator.setContentDescription(context.getString(R.string.one_moment));
    animatePrepare();
  }

  public void setPreparing() {
    if (!applyStyle(Prefs.TICK_SENDING)) return;
    deliveryIndicator.setContentDescription(
        context.getString(R.string.a11y_delivery_status_sending));
    animatePrepare();
  }

  public void setPending() {
    if (!applyStyle(Prefs.TICK_SENDING)) return;
    deliveryIndicator.setContentDescription(
        context.getString(R.string.a11y_delivery_status_sending));
    animateSending();
  }

  public void setSent() {
    if (!applyStyle(Prefs.TICK_SENT)) return;
    deliveryIndicator.setContentDescription(
        context.getString(R.string.a11y_delivery_status_delivered));
    clearAnimation();
  }

  public void setRead() {
    if (!applyStyle(Prefs.TICK_RECEIVED)) return;
    deliveryIndicator.setContentDescription(context.getString(R.string.a11y_delivery_status_read));
    clearAnimation();
  }

  /** shiroikuma fork (Step 11): every member of a group returned a read receipt. */
  public void setReadByAll() {
    if (!applyStyle(Prefs.TICK_RECEIVED_ALL)) return;
    deliveryIndicator.setContentDescription(context.getString(R.string.a11y_delivery_status_read));
    clearAnimation();
  }

  public void setFailed() {
    if (!applyStyle(Prefs.TICK_FAILED)) return;
    deliveryIndicator.setContentDescription(
        context.getString(R.string.a11y_delivery_status_invalid));
    clearAnimation();
  }

  public void setTint(Integer color) {
    if (color != null) {
      deliveryIndicator.setColorFilter(color);
    } else {
      resetTint();
    }
  }

  public void resetTint() {
    deliveryIndicator.setColorFilter(null);
  }

  public String getDescription() {
    if (deliveryIndicator.getVisibility() == View.VISIBLE
        && deliveryIndicator.getContentDescription() != null) {
      return deliveryIndicator.getContentDescription().toString();
    }
    return "";
  }
}
