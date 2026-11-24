package org.thoughtcrime.securesms.shiroikuma;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * shiroikuma fork: the outlined switch of the black-yellow theme as a drawable - a round outline
 * with a solid dot, right when on and left when off, each state with an outline and a dot colour
 * of its own. Never filled. The button of a checkable menu row and the marker of a hand-built
 * toggle row.
 */
public final class SkSwitchDrawable extends Drawable {
  private final int onFrame, onDot, offFrame, offDot, width, height;
  private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  private boolean checked;
  private boolean enabled = true;

  public SkSwitchDrawable(
      @ColorInt int onFrame,
      @ColorInt int onDot,
      @ColorInt int offFrame,
      @ColorInt int offDot,
      int width,
      int height,
      int strokeWidth) {
    this.onFrame = onFrame;
    this.onDot = onDot;
    this.offFrame = offFrame;
    this.offDot = offDot;
    this.width = width;
    this.height = height;
    stroke.setStyle(Paint.Style.STROKE);
    stroke.setStrokeWidth(strokeWidth);
    fill.setStyle(Paint.Style.FILL);
  }

  /** For a plain view that has no checked state of its own. */
  @NonNull
  public SkSwitchDrawable checked(boolean value) {
    checked = value;
    invalidateSelf();
    return this;
  }

  @Override
  public void draw(@NonNull Canvas canvas) {
    Rect b = getBounds();
    int frame = checked ? onFrame : offFrame;
    int dot = checked ? onDot : offDot;
    if (!enabled) {
      frame = SkRoles.disabled(frame);
      dot = SkRoles.disabled(dot);
    }
    float inset = stroke.getStrokeWidth() / 2f;
    rect.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
    stroke.setColor(frame);
    canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, stroke);
    fill.setColor(dot);
    float cx = checked ? b.right - b.height() / 2f : b.left + b.height() / 2f;
    canvas.drawCircle(cx, b.exactCenterY(), b.height() * 0.23f, fill);
  }

  @Override
  public boolean isStateful() {
    return true;
  }

  @Override
  protected boolean onStateChange(@NonNull int[] state) {
    boolean c = false, e = false;
    for (int s : state) {
      if (s == android.R.attr.state_checked) c = true;
      if (s == android.R.attr.state_enabled) e = true;
    }
    if (c == checked && e == enabled) return false;
    checked = c;
    enabled = e;
    invalidateSelf();
    return true;
  }

  @Override
  public int getIntrinsicWidth() {
    return width;
  }

  @Override
  public int getIntrinsicHeight() {
    return height;
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
