package org.thoughtcrime.securesms.shiroikuma;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AbsListView;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.view.menu.ListMenuItemView;
import androidx.appcompat.view.menu.MenuItemImpl;
import androidx.appcompat.view.menu.SkMenuAccess;

/**
 * shiroikuma fork: the row of every popup menu - popup and long-press menus, submenus and the
 * toolbar overflow - drawn by the black-yellow theme's menu rule, so a menu shows at a glance
 * which lines can be pressed:
 *
 * <ul>
 *   <li>an action row is an outlined pill spanning the menu, its icon inside at the left and a
 *       chevron at the right when it opens something;
 *   <li>the icon is coloured by what the row does; a destructive row is red throughout, a
 *       disabled one dimmed to 38 percent;
 *   <li>a toggle row stays plain and gets the outlined switch, never a checkbox;
 *   <li>a radio choice and the disabled title line a menu opens with stay plain.
 * </ul>
 *
 * It is swapped in through the app's own copies of AppCompat's {@code
 * abc_popup_menu_item_layout} and {@code abc_cascading_menu_item_layout} - an app layout
 * overrides the library layout of the same name - so no call site asks for it.
 */
@SuppressLint("RestrictedApi")
public class SkPillMenuItemView extends ListMenuItemView {
  private boolean ready;
  private LinearLayout content;
  private TextView title;
  private ImageView arrow;
  private int padStart, padTop, padEnd, padBottom;
  private int contentHeight;

  public SkPillMenuItemView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public SkPillMenuItemView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    try {
      content = findViewById(androidx.appcompat.R.id.content);
      title = findViewById(androidx.appcompat.R.id.title);
      arrow = findViewById(androidx.appcompat.R.id.submenuarrow);
      View divider = findViewById(androidx.appcompat.R.id.group_divider);
      ready = content != null && title != null;
      if (ready) {
        padStart = content.getPaddingStart();
        padTop = content.getPaddingTop();
        padEnd = content.getPaddingEnd();
        padBottom = content.getPaddingBottom();
        contentHeight = content.getLayoutParams().height;
        if (divider != null) {
          // The stock divider is all but invisible on black: a clear line, as wide as the pills.
          divider.setBackgroundColor(SkRoles.get(getContext(), SkRole.MENU_DIVIDER));
          ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) divider.getLayoutParams();
          lp.leftMargin = dp(SkMenuPills.SIDE_MARGIN_DP);
          lp.rightMargin = dp(SkMenuPills.SIDE_MARGIN_DP);
          divider.setLayoutParams(lp);
        }
      }
    } catch (Throwable t) {
      ready = false;
    }
  }

  @Override
  public void initialize(MenuItemImpl itemData, int menuType) {
    boolean icons = true;
    if (ready) {
      try {
        icons = SkMenuPills.iconColumn(SkMenuAccess.menuOf(itemData));
        setForceShowIcon(icons);
      } catch (Throwable ignore) {
      }
    }
    super.initialize(itemData, menuType);
    if (!ready) return;
    try {
      if (icons && itemData.getIcon() == null && !SkMenuPills.isHeader(itemData)) {
        // A drawn icon takes no tint, so it is made in the colour of what the row does.
        int color =
            SkMenuPills.isDestructive(getContext(), itemData)
                ? SkRoles.get(getContext(), SkRole.DELETE)
                : SkMenuPills.iconColor(getContext(), itemData);
        if (!itemData.isEnabled()) color = SkRoles.disabled(color);
        setIcon(SkMenuPills.iconFor(getContext(), itemData, color));
      }
      style(itemData);
    } catch (Throwable ignore) {
    }
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    // The pill carries the ripple; the list's rectangular press highlight would paint over it.
    ViewParent parent = getParent();
    if (ready && parent instanceof AbsListView) {
      AbsListView list = (AbsListView) parent;
      if (!(list.getSelector() instanceof ColorDrawable)) {
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
      }
    }
  }

  private void style(@NonNull MenuItemImpl item) {
    Context context = getContext();
    boolean toggle = SkMenuPills.isToggle(item);
    boolean pill = SkMenuPills.isPill(item);
    boolean destructive = pill && SkMenuPills.isDestructive(context, item);

    // Frame, label and icon are three roles. The frame and the label are the menu's own; the
    // icon wears the colour of what the row does. A deleting row lends its red to all three.
    int frame = destructive ? SkRoles.get(context, SkRole.DELETE) : SkRoles.get(context, SkRole.MENU_ROW_FRAME);
    int text = destructive ? frame : SkRoles.get(context, SkRole.MENU_LABEL);
    int icon = destructive ? frame : SkMenuPills.iconColor(context, item);
    if (!item.isEnabled()) {
      frame = SkRoles.disabled(frame);
      text = SkRoles.disabled(text);
      icon = SkRoles.disabled(icon);
    }

    ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) content.getLayoutParams();
    int h = dp(SkMenuPills.SIDE_MARGIN_DP);
    int v = pill ? dp(SkMenuPills.PILL_MARGIN_DP) : 0;
    // A toggle row has nothing to frame, so it is only as tall as its switch needs.
    int height = toggle ? dp(SkMenuPills.TOGGLE_HEIGHT_DP) : contentHeight;
    if (lp.leftMargin != h || lp.topMargin != v || lp.rightMargin != h || lp.bottomMargin != v || lp.height != height) {
      lp.height = height;
      lp.setMargins(h, v, h, v);
      content.setLayoutParams(lp);
    }
    content.setBackground(pill ? SkPills.frame(context, SkPills.REGULAR_DP, frame, Color.TRANSPARENT) : null);
    // The switch of a toggle row ends where the pills end.
    content.setPaddingRelative(padStart, padTop, toggle ? 0 : padEnd, padBottom);
    title.setTextColor(text);

    if (arrow != null) {
      boolean opens = pill && SkMenuPills.opens(context, item);
      if (opens) arrow.setImageDrawable(SkIcons.drawable(context, SkIcons.Icon.CHEVRON, text, 20f));
      arrow.setVisibility(opens ? View.VISIBLE : View.GONE);
    }

    for (int i = 0; i < content.getChildCount(); i++) {
      View child = content.getChildAt(i);
      if (child instanceof CheckBox) {
        if (child.getTag() == null) {
          CheckBox cb = (CheckBox) child;
          cb.setButtonDrawable(SkSwitches.drawable(context, true));
          cb.setButtonTintList(null);
          cb.setBackground(null);
          // A long label stops short of the switch.
          ViewGroup.MarginLayoutParams clp = (ViewGroup.MarginLayoutParams) cb.getLayoutParams();
          clp.setMarginStart(dp(8));
          cb.setLayoutParams(clp);
          cb.setTag(Boolean.TRUE);
        }
      } else if (child.getId() == androidx.appcompat.R.id.icon) {
        // Clear of the round end of the pill, and not squeezed in a low row.
        ViewGroup.MarginLayoutParams ilp = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
        if (ilp.topMargin != dp(2)) {
          ilp.setMarginStart(dp(14));
          ilp.setMarginEnd(-dp(6));
          ilp.topMargin = dp(2);
          ilp.bottomMargin = dp(2);
          child.setLayoutParams(ilp);
        }
        if (child instanceof ImageView) {
          ImageView image = (ImageView) child;
          Drawable d = image.getDrawable();
          if (d != null) image.setImageTintList(ColorStateList.valueOf(icon));
        }
      }
    }
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }
}
