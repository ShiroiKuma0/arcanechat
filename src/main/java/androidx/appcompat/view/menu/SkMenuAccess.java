package androidx.appcompat.view.menu;

import androidx.annotation.Nullable;

/**
 * shiroikuma fork: one accessor in AppCompat's menu package - the menu a row's item belongs to is
 * package-private there. Used only by {@code shiroikuma/SkPillMenuItemView}.
 */
public final class SkMenuAccess {
  private SkMenuAccess() {}

  @Nullable
  public static MenuBuilder menuOf(@Nullable MenuItemImpl item) {
    try {
      return item != null ? item.mMenu : null;
    } catch (Throwable t) {
      return null;
    }
  }
}
