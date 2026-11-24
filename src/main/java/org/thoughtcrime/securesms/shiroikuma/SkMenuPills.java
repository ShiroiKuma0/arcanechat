package org.thoughtcrime.securesms.shiroikuma;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuItemImpl;
import java.util.Locale;

/**
 * shiroikuma fork: what the menu rows of {@link SkPillMenuItemView} need to know about an item
 * and cannot ask it - which icon it gets when its menu file brings none, which colour role that
 * icon wears, whether the row opens something (a chevron) and whether it deletes (red).
 *
 * <p>Everything is told from the NAME of the item's id ({@code menu_delete_chat}, {@code
 * menu_mute_notifications}), so an item upstream adds later is covered without touching this
 * table, and nothing here refers to an upstream id that a rebase could remove.
 */
@SuppressLint("RestrictedApi")
public final class SkMenuPills {
  private SkMenuPills() {}

  static final int TOGGLE_HEIGHT_DP = 36; // a toggle row: the switch and little more
  static final int PILL_MARGIN_DP = 3; // above and below a pill
  static final int SIDE_MARGIN_DP = 8; // from the pill to the edge of the menu

  /** One line of the table: a fragment of an id name, and what a row carrying it is. */
  private static final class Kind {
    final String word;
    final SkIcons.Icon icon;
    final SkIcons.Does does;
    final boolean opens;

    Kind(String word, SkIcons.Icon icon, SkIcons.Does does, boolean opens) {
      this.word = word;
      this.icon = icon;
      this.does = does;
      this.opens = opens;
    }
  }

  private static Kind k(String word, SkIcons.Icon icon, SkIcons.Does does, boolean opens) {
    return new Kind(word, icon, does, opens);
  }

  private static final SkIcons.Does VIEW = SkIcons.Does.VIEW;
  private static final SkIcons.Does CONNECT = SkIcons.Does.CONNECT;
  private static final SkIcons.Does MESSAGE = SkIcons.Does.MESSAGE;
  private static final SkIcons.Does CONTACT = SkIcons.Does.CONTACT;
  private static final SkIcons.Does SHARE = SkIcons.Does.SHARE;
  private static final SkIcons.Does BLOCK = SkIcons.Does.BLOCK;
  private static final SkIcons.Does DELETE = SkIcons.Does.DELETE;

  /** First match wins - the deleting words first, so "clear_chat" is never taken for less. */
  private static final Kind[] KINDS = {
    k("delete", SkIcons.Icon.DELETE, DELETE, false),
    k("clear_chat", SkIcons.Icon.DELETE, DELETE, false),
    k("remove", SkIcons.Icon.DELETE, DELETE, false),
    k("unblock", SkIcons.Icon.CHECK, CONNECT, false),
    k("block", SkIcons.Icon.BLOCK, BLOCK, false),
    k("mute", SkIcons.Icon.MUTE, BLOCK, true),
    k("leave", SkIcons.Icon.CLOSE, BLOCK, false),
    k("withdraw", SkIcons.Icon.STOP, BLOCK, false),
    k("ephemeral", SkIcons.Icon.CLOCK, BLOCK, true),
    k("encr", SkIcons.Icon.LOCK, VIEW, true),
    k("video_call", SkIcons.Icon.VIDEO, CONNECT, false),
    k("call", SkIcons.Icon.PHONE, CONNECT, false),
    k("proxy", SkIcons.Icon.PRIVACY, CONNECT, true),
    k("resend", SkIcons.Icon.RESET, CONNECT, false),
    k("reset", SkIcons.Icon.RESET, CONNECT, false),
    k("load_remote", SkIcons.Icon.IMPORT, CONNECT, false),
    k("load_from_image", SkIcons.Icon.IMAGE, CONNECT, true),
    k("qr", SkIcons.Icon.QR, CONNECT, true),
    k("register", SkIcons.Icon.CHECK, CONNECT, false),
    k("create", SkIcons.Icon.CHECK, CONNECT, false),
    k("apply", SkIcons.Icon.CHECK, CONNECT, false),
    k("reply", SkIcons.Icon.REPLY, MESSAGE, false),
    k("forward", SkIcons.Icon.FORWARD, MESSAGE, true),
    k("show_in_chat", SkIcons.Icon.BUBBLE, MESSAGE, true),
    k("attachment", SkIcons.Icon.ATTACH, MESSAGE, true),
    k("edit", SkIcons.Icon.EDIT, MESSAGE, true),
    k("set_tag", SkIcons.Icon.EDIT, MESSAGE, true),
    k("mark", SkIcons.Icon.CHECK, MESSAGE, false),
    k("unread", SkIcons.Icon.EYE, MESSAGE, false),
    k("select_all", SkIcons.Icon.SELECT, MESSAGE, false),
    k("unpin", SkIcons.Icon.PIN, MESSAGE, false),
    k("pin", SkIcons.Icon.PIN, MESSAGE, false),
    k("report_issue", SkIcons.Icon.WARNING, MESSAGE, true),
    k("switch_account", SkIcons.Icon.PERSON, CONTACT, true),
    k("team_profile", SkIcons.Icon.GROUP, CONTACT, true),
    k("view_profile", SkIcons.Icon.PERSON, CONTACT, true),
    k("contact", SkIcons.Icon.PERSON, CONTACT, true),
    k("invite", SkIcons.Icon.PERSON, CONTACT, true),
    k("add_members", SkIcons.Icon.GROUP, CONTACT, false),
    k("share", SkIcons.Icon.SHARE, SHARE, true),
    k("export", SkIcons.Icon.EXPORT, SHARE, true),
    k("copy", SkIcons.Icon.COPY, SHARE, false),
    k("paste", SkIcons.Icon.COPY, SHARE, false),
    k("clone", SkIcons.Icon.COPY, SHARE, true),
    k("save", SkIcons.Icon.SAVE, SHARE, false),
    k("home_screen", SkIcons.Icon.HOME, SHARE, false),
    k("archive", SkIcons.Icon.ARCHIVE, SHARE, false),
    k("move_to_top", SkIcons.Icon.UP, SHARE, false),
    k("search_up", SkIcons.Icon.UP, VIEW, false),
    k("search_down", SkIcons.Icon.DOWN, VIEW, false),
    k("scroll_up", SkIcons.Icon.UP, VIEW, false),
    k("scroll_down", SkIcons.Icon.DOWN, VIEW, false),
    k("zoom_in", SkIcons.Icon.ADD, VIEW, false),
    k("zoom_out", SkIcons.Icon.SIZE, VIEW, false),
    k("search", SkIcons.Icon.SEARCH, VIEW, false),
    k("all_media", SkIcons.Icon.IMAGE, VIEW, true),
    k("overview", SkIcons.Icon.GRID, VIEW, true),
    k("map", SkIcons.Icon.GLOBE, VIEW, true),
    k("details", SkIcons.Icon.INFO, VIEW, true),
    k("log", SkIcons.Icon.LIST, VIEW, true),
    k("troubleshooting", SkIcons.Icon.HELP, VIEW, true),
    k("help", SkIcons.Icon.HELP, VIEW, true),
    k("learn_more", SkIcons.Icon.BOOK, VIEW, true),
    k("privacy", SkIcons.Icon.PRIVACY, VIEW, true),
    k("contribute", SkIcons.Icon.STAR, VIEW, true),
    k("source_code", SkIcons.Icon.CODE, VIEW, true),
    k("sound", SkIcons.Icon.BELL, VIEW, true),
    k("vibrate", SkIcons.Icon.DEVICE, VIEW, true),
    k("shiroikuma_ui", SkIcons.Icon.PALETTE, VIEW, true),
    k("settings", SkIcons.Icon.GEAR, VIEW, true),
    k("link", SkIcons.Icon.LINK, SHARE, false),
  };

  private static final Kind OTHER = new Kind("", SkIcons.Icon.FRAME, VIEW, false);

  @NonNull
  private static Kind kindOf(@NonNull Context context, @NonNull MenuItem item) {
    try {
      int id = item.getItemId();
      if (id != 0 && id != View.NO_ID) {
        String n = context.getResources().getResourceEntryName(id).toLowerCase(Locale.ROOT);
        for (Kind k : KINDS) if (n.contains(k.word)) return k;
      }
    } catch (Throwable ignore) {
    }
    return OTHER;
  }

  /** The role an icon wears, from a name such as {@code menu_delete_chat} or a preference key. */
  @NonNull
  public static SkIcons.Does doesForName(@Nullable String name) {
    if (name != null) {
      String n = name.toLowerCase(Locale.ROOT);
      for (Kind k : KINDS) if (n.contains(k.word)) return k.does;
    }
    return VIEW;
  }

  @NonNull
  public static SkIcons.Icon iconForName(@Nullable String name) {
    if (name != null) {
      String n = name.toLowerCase(Locale.ROOT);
      for (Kind k : KINDS) if (n.contains(k.word)) return k.icon;
    }
    return SkIcons.Icon.FRAME;
  }

  @NonNull
  static Drawable iconFor(@NonNull Context context, @NonNull MenuItem item, @ColorInt int color) {
    return SkIcons.drawable(context, kindOf(context, item).icon, color, 24f);
  }

  @ColorInt
  static int iconColor(@NonNull Context context, @NonNull MenuItem item) {
    return SkIcons.color(context, kindOf(context, item).does);
  }

  static boolean opens(@NonNull Context context, @NonNull MenuItem item) {
    if (item.hasSubMenu()) return true;
    CharSequence title = item.getTitle();
    if (title != null) {
      String t = title.toString().trim();
      if (t.endsWith("…") || t.endsWith("...")) return true;
    }
    return kindOf(context, item).opens;
  }

  static boolean isDestructive(@NonNull Context context, @NonNull MenuItem item) {
    return kindOf(context, item).does == DELETE;
  }

  /** The disabled title line a menu opens with. */
  static boolean isHeader(@NonNull MenuItem item) {
    return !item.isEnabled() && item.getItemId() == 0 && !item.hasSubMenu();
  }

  /** A row with a switch: checkable, and not one of an exclusive (radio) group. */
  static boolean isToggle(@NonNull MenuItemImpl item) {
    return item.isCheckable() && !item.isExclusiveCheckable();
  }

  /** An action row: everything that is not a toggle, a radio choice or a header. */
  static boolean isPill(@NonNull MenuItemImpl item) {
    return !item.isCheckable() && !isHeader(item);
  }

  /**
   * Whether the rows of this menu keep an icon column. A list of pure choices (every row a radio
   * choice) stays iconless; every other menu keeps the column on every row, so the labels share
   * one left edge and a row whose menu file brings no icon gets a drawn one.
   */
  static boolean iconColumn(@Nullable MenuBuilder menu) {
    if (menu == null) return true;
    try {
      for (MenuItemImpl item : menu.getVisibleItems()) {
        if (!item.isExclusiveCheckable()) return true;
      }
      return false;
    } catch (Throwable t) {
      return true;
    }
  }

  /**
   * The icons of a menu that sit in the top bar (action buttons, the selection bar) wear the top
   * bar's icon role; a row re-tints its own icon by what the row does. Called for every options
   * menu and every selection-bar menu from {@code BaseActionBarActivity}.
   */
  public static void decorate(@NonNull Context context, @Nullable Menu menu) {
    if (menu == null) return;
    try {
      int color = SkRoles.get(context, SkRole.TOPBAR_ICON);
      for (int i = 0; i < menu.size(); i++) {
        MenuItem item = menu.getItem(i);
        Drawable icon = item.getIcon();
        if (icon != null) {
          icon = icon.mutate();
          icon.setTint(color);
          item.setIcon(icon);
        }
        if (item.hasSubMenu()) decorate(context, item.getSubMenu());
      }
    } catch (Throwable ignore) {
    }
  }

  /**
   * How far up a popup must be shifted so that it does not scroll while the screen has room: a
   * stock popup is given only the larger of the room above and below its anchor. Null when it
   * fits below the anchor as it is.
   */
  @Nullable
  static Integer verticalOffset(@NonNull Context context, @NonNull View anchor, @NonNull Menu menu) {
    try {
      float d = context.getResources().getDisplayMetrics().density;
      int rows = 0, toggles = 0;
      for (int i = 0; i < menu.size(); i++) {
        MenuItem item = menu.getItem(i);
        if (!item.isVisible()) continue;
        if (item.isCheckable()) toggles++;
        else rows++;
      }
      int height = Math.round((rows * (48 + 2 * PILL_MARGIN_DP) + toggles * TOGGLE_HEIGHT_DP + 24) * d);
      Rect frame = new Rect();
      anchor.getWindowVisibleDisplayFrame(frame);
      int[] at = new int[2];
      anchor.getLocationOnScreen(at);
      int below = frame.bottom - (at[1] + anchor.getHeight());
      if (height <= below) return null;
      int room = frame.height();
      int shift = Math.min(height, room) - below;
      return -(anchor.getHeight() + shift);
    } catch (Throwable t) {
      return null;
    }
  }
}
