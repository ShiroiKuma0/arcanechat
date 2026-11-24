package org.thoughtcrime.securesms.shiroikuma;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatViewInflater;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatRadioButton;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import org.thoughtcrime.securesms.R;

/**
 * shiroikuma fork: named by {@code viewInflaterClass} in {@code ThemeOverlay.Sk}, which lies over
 * the theme of every screen and - through the dialog's parent context - of every dialog. Each
 * widget the black-yellow theme has a rule for is restyled here as it is inflated, so no layout
 * and no call site has to ask:
 *
 * <ul>
 *   <li>every button an outlined pill ({@link SkPills}); a button upstream styled as primary and
 *       a dialog's positive button are the <em>main</em> pill, a deleting one is red;
 *   <li>every switch the outlined switch ({@link SkSwitches});
 *   <li>check boxes and radio buttons selection marks in their own roles;
 *   <li>text fields, tabs, top bars and floating buttons in their own roles.
 * </ul>
 */
@Keep
public class SkViewInflater extends AppCompatViewInflater {
  private static final String ANDROID = "http://schemas.android.com/apk/res/android";

  private static int idOf(@NonNull AttributeSet attrs) {
    try {
      return attrs.getAttributeResourceValue(ANDROID, "id", 0);
    } catch (Throwable t) {
      return 0;
    }
  }

  private static String styleName(@NonNull Context context, @NonNull AttributeSet attrs) {
    try {
      int style = attrs.getStyleAttribute();
      return style == 0 ? "" : context.getResources().getResourceEntryName(style);
    } catch (Throwable t) {
      return "";
    }
  }

  @NonNull
  @Override
  protected AppCompatButton createButton(Context context, AttributeSet attrs) {
    AppCompatButton button = super.createButton(context, attrs);
    final int id = idOf(attrs);
    final boolean dialog =
        id == android.R.id.button1 || id == android.R.id.button2 || id == android.R.id.button3;
    final String style = styleName(context, attrs);
    final boolean main = id == android.R.id.button1 || style.contains("ButtonPrimary");
    // Styled once it is attached: by then its label is what the screen made of it, which decides
    // glyph or pill, and red or not.
    button.addOnAttachStateChangeListener(
        new View.OnAttachStateChangeListener() {
          @Override
          public void onViewAttachedToWindow(@NonNull View v) {
            if (v.getTag(R.id.sk_styled) != null) return;
            TextView b = (TextView) v;
            if (TextUtils.isEmpty(b.getText())) return; // a glyph stays a glyph
            v.setTag(R.id.sk_styled, Boolean.TRUE);
            boolean destructive = SkPills.isDestructiveLabel(b.getContext(), b.getText());
            if (dialog) SkPills.dialogButton(b, main, destructive);
            else if (v.getId() == R.id.message_request_block) {
              SkPills.colored(b, false, SkRoles.get(b.getContext(), SkRole.ICON_BLOCK));
            } else SkPills.button(b, main, destructive);
          }

          @Override
          public void onViewDetachedFromWindow(@NonNull View v) {}
        });
    return button;
  }

  @NonNull
  @Override
  protected AppCompatCheckBox createCheckBox(Context context, AttributeSet attrs) {
    AppCompatCheckBox v = super.createCheckBox(context, attrs);
    SkSwitches.mark(v);
    return v;
  }

  @NonNull
  @Override
  protected AppCompatRadioButton createRadioButton(Context context, AttributeSet attrs) {
    AppCompatRadioButton v = super.createRadioButton(context, attrs);
    SkSwitches.mark(v);
    return v;
  }

  @NonNull
  @Override
  protected AppCompatEditText createEditText(Context context, AttributeSet attrs) {
    AppCompatEditText v = super.createEditText(context, attrs);
    try {
      v.setTextColor(SkRoles.get(context, SkRole.FIELD_TEXT));
      v.setHintTextColor(SkRoles.get(context, SkRole.FIELD_HINT));
      v.setHighlightColor(SkRoles.get(context, SkRole.TEXT_SELECTION));
      if (v.getBackground() != null) {
        ViewCompat.setBackgroundTintList(
            v, ColorStateList.valueOf(SkRoles.get(context, SkRole.FIELD_LINE)));
      }
    } catch (Throwable ignore) {
    }
    return v;
  }

  @NonNull
  @Override
  protected AppCompatTextView createTextView(Context context, AttributeSet attrs) {
    AppCompatTextView v = super.createTextView(context, attrs);
    try {
      int id = idOf(attrs);
      if (id == android.R.id.message) v.setTextColor(SkRoles.get(context, SkRole.DIALOG_TEXT));
    } catch (Throwable ignore) {
    }
    return v;
  }

  @Nullable
  @Override
  protected View createView(Context context, String name, AttributeSet attrs) {
    try {
      switch (name) {
        case "androidx.appcompat.widget.SwitchCompat":
          {
            SwitchCompat sw = new SwitchCompat(context, attrs);
            SkSwitches.outline(sw);
            return sw;
          }
        case "androidx.appcompat.widget.DialogTitle":
          {
            TextView t = new androidx.appcompat.widget.DialogTitle(context, attrs);
            t.setTextColor(SkRoles.get(context, SkRole.DIALOG_TITLE));
            return t;
          }
        case "com.google.android.material.floatingactionbutton.FloatingActionButton":
          {
            FloatingActionButton fab = new FloatingActionButton(context, attrs);
            floating(fab);
            return fab;
          }
        case "com.google.android.material.tabs.TabLayout":
          {
            TabLayout tabs = new TabLayout(context, attrs);
            int on = SkRoles.get(context, SkRole.TAB_SELECTED);
            tabs.setTabTextColors(SkRoles.get(context, SkRole.TAB_NORMAL), on);
            tabs.setSelectedTabIndicatorColor(on);
            tabs.setBackgroundColor(SkRoles.get(context, SkRole.TOPBAR_GROUND));
            return tabs;
          }
        case "androidx.appcompat.widget.Toolbar":
          {
            Toolbar bar = new Toolbar(context, attrs);
            toolbar(bar);
            return bar;
          }
        default:
      }
    } catch (Throwable ignore) {
    }
    return super.createView(context, name, attrs);
  }

  /** A floating button: ground inside, a ring, the glyph in its own colour. Never a slab. */
  public static void floating(@NonNull FloatingActionButton fab) {
    floating(
        fab,
        SkRoles.get(fab.getContext(), SkRole.FAB_RING),
        SkRoles.get(fab.getContext(), SkRole.FAB_GLYPH),
        SkRoles.get(fab.getContext(), SkRole.FAB_GROUND));
  }

  public static void floating(@NonNull FloatingActionButton fab, int ring, int glyph, int ground) {
    try {
      Context context = fab.getContext();
      fab.setBackgroundTintList(ColorStateList.valueOf(ground));
      fab.setImageTintList(ColorStateList.valueOf(glyph));
      fab.setRippleColor(SkRoles.alpha(ring, 0.3f));
      fab.setCompatElevation(0);
      if (android.os.Build.VERSION.SDK_INT >= 23) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setColor(0);
        g.setStroke(SkPills.dp(context, SkPills.MAIN_DP), ring);
        fab.setForeground(g);
      }
    } catch (Throwable ignore) {
    }
  }

  /** The top bar's own roles: ground, title, subtitle, navigation and overflow glyphs. */
  public static void toolbar(@NonNull Toolbar bar) {
    try {
      final Context context = bar.getContext();
      final int title = SkRoles.get(context, SkRole.TOPBAR_TITLE);
      final int icon = SkRoles.get(context, SkRole.TOPBAR_ICON);
      bar.setTitleTextColor(title);
      bar.setSubtitleTextColor(title);
      bar.addOnAttachStateChangeListener(
          new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
              try {
                Toolbar t = (Toolbar) v;
                Drawable nav = t.getNavigationIcon();
                if (nav != null) {
                  nav = nav.mutate();
                  nav.setTint(icon);
                  t.setNavigationIcon(nav);
                }
                Drawable more = t.getOverflowIcon();
                if (more != null) {
                  more = more.mutate();
                  more.setTint(icon);
                  t.setOverflowIcon(more);
                }
              } catch (Throwable ignore) {
              }
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {}
          });
    } catch (Throwable ignore) {
    }
  }
}
