package org.thoughtcrime.securesms.shiroikuma;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuPopupHelper;
import androidx.appcompat.widget.PopupMenu;

/**
 * shiroikuma fork: the popup menu of this app. It is AppCompat's PopupMenu - its rows are drawn
 * by {@link SkPillMenuItemView} like those of every other menu - with one addition: a menu taller
 * than the room below its anchor is shown shifted up, so it uses the height of the screen
 * instead of scrolling.
 */
@SuppressLint("RestrictedApi")
public class SkPopupMenu extends PopupMenu {
  private final Context context;
  private final View anchor;
  private MenuPopupHelper tall;

  public SkPopupMenu(@NonNull Context context, @NonNull View anchor) {
    super(context, anchor);
    this.context = context;
    this.anchor = anchor;
  }

  @Override
  public void show() {
    Integer offset = SkMenuPills.verticalOffset(context, anchor, getMenu());
    if (offset == null || !(getMenu() instanceof MenuBuilder)) {
      super.show();
      return;
    }
    try {
      tall = new MenuPopupHelper(context, (MenuBuilder) getMenu(), anchor);
      tall.show(0, offset);
    } catch (Throwable t) {
      tall = null;
      super.show();
    }
  }

  @Override
  public void dismiss() {
    try {
      if (tall != null) tall.dismiss();
    } catch (Throwable ignore) {
    }
    tall = null;
    super.dismiss();
  }
}
