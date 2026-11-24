#!/usr/bin/env python3
"""shiroikuma fork: the one colour-role table of the black-yellow theme.

Run from the repository root:  python3 tools/shiroikuma/gen_roles.py

Writes
  src/main/java/org/thoughtcrime/securesms/shiroikuma/SkRole.java     (the enum)
  src/main/res/values/sk_roles.xml                                     (role colour resources, computed defaults)
  src/main/res/values/sk_theme.xml                                     (ThemeOverlay.Sk: colour attributes -> roles)
  src/main/res/values/sk_role_strings.xml  +  values-ja/               (labels)

A role's default is one of
  "#RRGGBB"      a fixed colour (the two primaries, and colours that carry meaning)
  "=ROLE"        another role
  "~NN"          NN percent of the way from BACKGROUND to ACCENT (opaque)
  "@NN:ROLE"     ROLE at NN percent alpha

The role resources live in the default values/ ONLY: the run-time loader's colour table carries
the default configuration, so a qualified copy (values-night/, values-v30/) would beat it.
"""
import os

# section id, English, Japanese
SECTIONS = [
    ("FOUNDATION", "Foundation", "基本"),
    ("TEXT", "Text", "テキスト"),
    ("BUTTONS", "Buttons", "ボタン"),
    ("BARS", "Bars", "バー"),
    ("DIALOGS", "Dialogs", "ダイアログ"),
    ("SWITCHES", "Switches", "スイッチ"),
    ("LISTS", "Lists", "リスト"),
    ("MENUS", "Menus", "メニュー"),
    ("MARKS", "Check boxes and radio buttons", "チェックボックスとラジオボタン"),
    ("FIELDS", "Text fields", "入力欄"),
    ("CHATLIST", "Chat list", "チャット一覧"),
    ("CONVERSATION", "Conversation", "会話"),
    ("COMPOSE", "Message bar", "メッセージ入力バー"),
    ("TICKS", "Delivery ticks", "送信状態の印"),
    ("CALLS", "Call screen", "通話画面"),
    ("PAGE", "Settings pages", "設定ページ"),
]

# key, section, default, English, Japanese, storage key (None = sk_role_<key>), theme attrs, upstream colour names
R = []


def role(key, section, default, en, ja, storage=None, attrs=(), names=()):
    R.append(dict(key=key, section=section, default=default, en=en, ja=ja,
                  storage=storage or "sk_role_" + key.lower(), attrs=list(attrs), names=list(names)))


# ---- Foundation
role("ACCENT", "FOUNDATION", "#FFFF00", "Accent", "アクセント",
     attrs=["colorAccent"], names=["def_accent", "def_primary", "def_primary_lite", "acc_yellow", "shiroikuma_yellow"])
role("BACKGROUND", "FOUNDATION", "#000000", "Background", "背景",
     attrs=["android:windowBackground", "android:colorBackground"], names=["gray95"])
role("TEXT", "FOUNDATION", "=ACCENT", "Text", "文字",
     attrs=["android:textColor", "android:textColorPrimary", "android:textColorPrimaryInverse",
            "android:textColorTertiary"], names=["def_text", "gray5"])
role("NOTE", "FOUNDATION", "#C8C800", "Explanations and left rules", "説明文と左の罫線")
role("DIVIDER", "FOUNDATION", "~70", "Dividers", "区切り線")
role("SELECTION", "FOUNDATION", "@25:ACCENT", "Selected and pressed rows", "選択中・押下中の行",
     attrs=["colorControlHighlight"], names=["def_primary_alpha33", "primary_alpha_focus", "touch_highlight"])
role("DELETE", "FOUNDATION", "#FF0000", "Delete and warning", "削除と警告", attrs=["colorError"])

# ---- Text
role("TEXT_SECONDARY", "TEXT", "=TEXT", "Secondary text", "補助テキスト",
     attrs=["android:textColorSecondary", "android:textColorSecondaryInverse"], names=["gray50", "gray10"])
role("TEXT_LINK", "TEXT", "=ACCENT", "Links", "リンク", attrs=["android:textColorLink"])
role("ICON", "TEXT", "=ACCENT", "Plain icons", "通常のアイコン",
     attrs=["icon_tint", "close_icon_color", "quote_dismiss_button_tint", "pref_icon_tint"])

# ---- Buttons
role("BUTTON_FRAME", "BUTTONS", "=ACCENT", "Button frame", "ボタンの枠")
role("BUTTON_TEXT", "BUTTONS", "=TEXT", "Button label", "ボタンの文字")
role("BUTTON_GROUND", "BUTTONS", "=BACKGROUND", "Button ground", "ボタンの地", attrs=["colorButtonNormal"])
role("FAB_RING", "BUTTONS", "=ACCENT", "New-chat button ring", "新規チャットボタンの輪",
     storage="pref_color_fab", attrs=["fab_color"])
role("FAB_GLYPH", "BUTTONS", "=FAB_RING", "New-chat button glyph", "新規チャットボタンの記号")
role("FAB_GROUND", "BUTTONS", "=BACKGROUND", "New-chat button ground", "新規チャットボタンの地")

# ---- Bars
role("TOPBAR_GROUND", "BARS", "=BACKGROUND", "Top bar ground", "上部バーの地",
     attrs=["colorPrimary", "search_toolbar_background", "search_background", "profile_toolbar_background"])
role("TOPBAR_TITLE", "BARS", "=TEXT", "Top bar title", "上部バーの題名",
     attrs=["profile_toolbar_foreground", "profile_document_foreground"])
role("TOPBAR_ICON", "BARS", "=ACCENT", "Top bar icons", "上部バーのアイコン",
     attrs=["colorControlNormal", "menu_icon_tint"])
role("STATUSBAR", "BARS", "=BACKGROUND", "Status bar", "ステータスバー",
     attrs=["colorPrimaryDark", "android:statusBarColor"])
role("NAVBAR", "BARS", "=BACKGROUND", "Navigation bar", "ナビゲーションバー", attrs=["android:navigationBarColor"])
role("ACTIONMODE_GROUND", "BARS", "=BACKGROUND", "Selection bar ground", "選択モードのバーの地",
     attrs=["actionModeBackground"], names=["action_mode_status_bar"])
role("TAB_SELECTED", "BARS", "=ACCENT", "Selected tab", "選択中のタブ")
role("TAB_NORMAL", "BARS", "=TEXT_SECONDARY", "Other tabs", "その他のタブ")

# ---- Dialogs
role("DIALOG_GROUND", "DIALOGS", "=BACKGROUND", "Dialog ground", "ダイアログの地",
     attrs=["dialog_background_color", "colorBackgroundFloating"])
role("DIALOG_FRAME", "DIALOGS", "=ACCENT", "Dialog frame", "ダイアログの枠")
role("DIALOG_TITLE", "DIALOGS", "=TEXT", "Dialog title", "ダイアログの題名")
role("DIALOG_TEXT", "DIALOGS", "=TEXT", "Dialog text", "ダイアログの本文", attrs=["textColorAlertDialogListItem"])
role("DIALOG_BUTTON_FRAME", "DIALOGS", "=BUTTON_FRAME", "Dialog button frame", "ダイアログのボタンの枠")
role("DIALOG_BUTTON_TEXT", "DIALOGS", "=BUTTON_TEXT", "Dialog button label", "ダイアログのボタンの文字")

# ---- Switches
role("SWITCH_ON_FRAME", "SWITCHES", "=ACCENT", "On: outline", "オン：輪郭")
role("SWITCH_ON_DOT", "SWITCHES", "=SWITCH_ON_FRAME", "On: dot", "オン：点")
role("SWITCH_OFF_FRAME", "SWITCHES", "~55", "Off: outline", "オフ：輪郭", attrs=["colorSwitchThumbNormal"])
role("SWITCH_OFF_DOT", "SWITCHES", "=SWITCH_OFF_FRAME", "Off: dot", "オフ：点")

# ---- Lists
role("LIST_PRIMARY", "LISTS", "=TEXT", "Row title", "行の題名")
role("LIST_SECONDARY", "LISTS", "=TEXT_SECONDARY", "Row detail", "行の詳細")
role("LIST_DIVIDER", "LISTS", "=DIVIDER", "Row divider", "行の区切り線", attrs=["contact_list_divider_color"])

# ---- Menus
role("MENU_GROUND", "MENUS", "=BACKGROUND", "Menu ground", "メニューの地")
role("MENU_FRAME", "MENUS", "=ACCENT", "Menu frame", "メニューの枠")
role("MENU_ROW_FRAME", "MENUS", "=ACCENT", "Menu row frame", "メニュー行の枠")
role("MENU_LABEL", "MENUS", "=TEXT", "Menu label", "メニューの文字")
role("MENU_DIVIDER", "MENUS", "=DIVIDER", "Menu divider", "メニューの区切り線")
role("ICON_CONNECT", "MENUS", "#00A550", "Icon: call, connect, synchronise", "アイコン：通話・接続・同期")
role("ICON_MESSAGE", "MENUS", "#0066FF", "Icon: message, reply", "アイコン：メッセージ・返信")
role("ICON_CONTACT", "MENUS", "#00B0FF", "Icon: contact, person", "アイコン：連絡先・人")
role("ICON_SHARE", "MENUS", "#FF8000", "Icon: copy, share, export", "アイコン：コピー・共有・エクスポート")
role("ICON_VIEW", "MENUS", "=ACCENT", "Icon: view, open, settings", "アイコン：表示・開く・設定")
role("ICON_BLOCK", "MENUS", "#FF3D7F", "Icon: block, mute, leave", "アイコン：ブロック・ミュート・退出")
role("ICON_DELETE", "MENUS", "=DELETE", "Icon: delete, remove", "アイコン：削除")

# ---- Marks
role("CHECK_ON", "MARKS", "=ACCENT", "Ticked mark", "チェックあり", attrs=["colorControlActivated"])
role("CHECK_OFF", "MARKS", "=TEXT", "Empty mark", "チェックなし")
role("MARK_LABEL", "MARKS", "=TEXT", "Label beside a mark", "印の横の文字")

# ---- Fields
role("FIELD_TEXT", "FIELDS", "=TEXT", "Typed text", "入力した文字", attrs=["android:editTextColor", "editTextColor"])
role("FIELD_HINT", "FIELDS", "~55", "Hint", "ヒント", attrs=["android:textColorHint"])
role("FIELD_LINE", "FIELDS", "=ACCENT", "Underline and cursor", "下線とカーソル")
role("TEXT_SELECTION", "FIELDS", "@40:ACCENT", "Selected text", "選択した文字", attrs=["android:textColorHighlight"])

# ---- Chat list
role("LIST_TITLE", "CHATLIST", "=TEXT", "Chat name", "チャット名", storage="pref_color_list_title",
     attrs=["conversation_list_item_contact_color", "conversation_list_item_unread_color"])
role("LIST_PREVIEW", "CHATLIST", "=TEXT", "Message preview", "メッセージのプレビュー", storage="pref_color_list_preview",
     attrs=["conversation_list_item_subject_color"])
role("LIST_DATE", "CHATLIST", "=TEXT", "Date and time", "日時", storage="pref_color_list_date",
     attrs=["conversation_list_item_date_color"])
role("UNREAD_BADGE", "CHATLIST", "=ACCENT", "Unread badge", "未読バッジ",
     attrs=["conversation_list_item_unreadcount_color"])
role("CARD_FRAME", "CHATLIST", "=ACCENT", "Row frame, bar and line", "行の枠・帯・線")
role("CARD_GROUND", "CHATLIST", "=BACKGROUND", "Row ground", "行の地")
role("CARD_FILL", "CHATLIST", "@12:ACCENT", "Filled card ground", "塗りカードの地", names=["card_fill_tint"])
role("PINNED_GROUND", "CHATLIST", "~13", "Pinned row ground", "固定した行の地", names=["pinned_bg", "pinned_bg_dark"])

# ---- Conversation
role("CONV_BACKGROUND", "CONVERSATION", "=BACKGROUND", "Background", "背景", storage="pref_color_conversation_bg",
     attrs=["conversation_background"])
role("MSG_TEXT_IN", "CONVERSATION", "=TEXT", "Incoming: text", "受信：文字", storage="pref_color_message_text_in",
     attrs=["conversation_item_incoming_text_primary_color"])
role("BUBBLE_FILL_IN", "CONVERSATION", "=BACKGROUND", "Incoming: bubble", "受信：吹き出し",
     storage="pref_color_bubble_fill_in")
role("BUBBLE_BORDER_IN", "CONVERSATION", "=ACCENT", "Incoming: bubble frame", "受信：吹き出しの枠",
     storage="pref_color_bubble_border_in")
role("MSG_META_IN", "CONVERSATION", "=MSG_TEXT_IN", "Incoming: time and sender", "受信：時刻と送信者",
     attrs=["conversation_item_incoming_text_secondary_color"])
role("MSG_TEXT_OUT", "CONVERSATION", "=TEXT", "Outgoing: text", "送信：文字", storage="pref_color_message_text_out",
     attrs=["conversation_item_outgoing_text_primary_color"])
role("BUBBLE_FILL_OUT", "CONVERSATION", "=BACKGROUND", "Outgoing: bubble", "送信：吹き出し",
     storage="pref_color_bubble_fill_out")
role("BUBBLE_BORDER_OUT", "CONVERSATION", "=ACCENT", "Outgoing: bubble frame", "送信：吹き出しの枠",
     storage="pref_color_bubble_border_out")
role("MSG_META_OUT", "CONVERSATION", "=MSG_TEXT_OUT", "Outgoing: time", "送信：時刻",
     attrs=["conversation_item_outgoing_text_secondary_color"])
role("QUOTE_TEXT", "CONVERSATION", "=TEXT", "Quoted text", "引用の文字", attrs=["conversation_item_quote_text_color"])
role("DATE_HEADER_TEXT", "CONVERSATION", "=TEXT", "Date header: text", "日付の見出し：文字",
     attrs=["conversation_item_sticky_date_text_color", "conversation_item_date_line_color"])
role("DATE_HEADER_FRAME", "CONVERSATION", "=ACCENT", "Date header and jump button: frame", "日付の見出しと移動ボタン：枠")
role("SYSTEM_MSG_TEXT", "CONVERSATION", "=TEXT", "System message: text", "システムメッセージ：文字",
     attrs=["conversation_item_update_text_color"])
role("SYSTEM_MSG_GROUND", "CONVERSATION", "=BACKGROUND", "System message: ground", "システムメッセージ：地",
     attrs=["conversation_item_update_bg_color"])
role("REACTION_GROUND", "CONVERSATION", "=BACKGROUND", "Reaction: ground", "リアクション：地",
     names=["reaction_pill_background", "reaction_pill_background_selected"])
role("REACTION_FRAME", "CONVERSATION", "=ACCENT", "Reaction: frame", "リアクション：枠",
     names=["reaction_pill_border", "reaction_pill_border_selected"])
role("REACTION_TEXT", "CONVERSATION", "=TEXT", "Reaction: count", "リアクション：数",
     names=["reaction_pill_text_color", "reaction_pill_text_color_selected"])
role("PINNED_BANNER_GROUND", "CONVERSATION", "=BACKGROUND", "Pinned message bar: ground", "固定メッセージのバー：地")
role("PINNED_BANNER_TEXT", "CONVERSATION", "=TEXT", "Pinned message bar: text", "固定メッセージのバー：文字")
role("PINNED_BANNER_ICON", "CONVERSATION", "=ACCENT", "Pinned message bar: pin", "固定メッセージのバー：ピン",
     names=["pinned_icon"])
role("AUDIO", "CONVERSATION", "=ACCENT", "Voice message controls", "音声メッセージの操作部", names=["audio_icon"])
role("DOC_ICON", "CONVERSATION", "=ACCENT", "File chip", "ファイルの印", names=["document_icon"])

# ---- Message bar
role("COMPOSE_GROUND", "COMPOSE", "=BACKGROUND", "Ground", "地",
     attrs=["input_panel_bg_color", "emoji_drawer_background", "attachment_type_selector_background"])
role("COMPOSE_FRAME", "COMPOSE", "=ACCENT", "Frame", "枠")
role("COMPOSE_TEXT", "COMPOSE", "=TEXT", "Typed text", "入力した文字", attrs=["emoji_text_color"])
role("COMPOSE_ICON", "COMPOSE", "=ACCENT", "Icons", "アイコン")
role("SEND_RING", "COMPOSE", "=ACCENT", "Send button ring", "送信ボタンの輪")
role("SEND_GLYPH", "COMPOSE", "=SEND_RING", "Send button glyph", "送信ボタンの記号")

# ---- Delivery ticks
role("TICK_SENDING", "TICKS", "#0000FF", "Sending", "送信中", storage="pref_ticks_color_sending")
role("TICK_SENT", "TICKS", "=ACCENT", "Sent", "送信済み", storage="pref_ticks_color_sent")
role("TICK_RECEIVED", "TICKS", "=ACCENT", "Received", "受信済み", storage="pref_ticks_color_received")
role("TICK_RECEIVED_ALL", "TICKS", "=ACCENT", "Received by all", "全員が受信済み",
     storage="pref_ticks_color_received_all")
role("TICK_FAILED", "TICKS", "#FF0000", "Failed", "失敗", storage="pref_ticks_color_failed")

# ---- Call screen
role("CALL_RING", "CALLS", "=ACCENT", "Control ring", "操作ボタンの輪")
role("CALL_GLYPH", "CALLS", "=CALL_RING", "Control glyph", "操作ボタンの記号")
role("CALL_GROUND", "CALLS", "=BACKGROUND", "Control ground", "操作ボタンの地")
role("CALL_ANSWER", "CALLS", "#00A550", "Answer", "応答", names=["calls_green"])
role("CALL_DECLINE", "CALLS", "#FF0000", "Decline and hang up", "拒否と終了", names=["calls_red"])

# ---- Settings pages
role("HEADING", "PAGE", "=ACCENT", "Section heading", "セクションの見出し")
role("ROW_LABEL", "PAGE", "=TEXT", "Row label", "行の題名")
role("ROW_VALUE", "PAGE", "=NOTE", "Row value", "行の値")
role("CHIP_FRAME", "PAGE", "=ACCENT", "Index chip frame", "索引チップの枠")
role("CHIP_LABEL", "PAGE", "=TEXT", "Index chip label", "索引チップの文字")
role("SECTION_BASE", "PAGE", "=ACCENT", "Section icon: the app and its settings", "セクションの印：アプリと設定")
role("SECTION_CONTROLS", "PAGE", "#00A550", "Section icon: controls", "セクションの印：操作部")
role("SECTION_WORDS", "PAGE", "#00B0FF", "Section icon: words", "セクションの印：文字")
role("SECTION_FRAME", "PAGE", "#0066FF", "Section icon: the frame and its boxes", "セクションの印：枠組み")
role("SECTION_REACH", "PAGE", "#FF8000", "Section icon: asks, offers, hands over", "セクションの印：やり取り")
role("SECTION_WARNING", "PAGE", "#FF0000", "Section icon: warning", "セクションの印：警告")
role("SECTION_FLASH", "PAGE", "#FF3D7F", "Section icon: a brief flash", "セクションの印：一時表示")

BY = {r["key"]: r for r in R}


def resolve(key):
    d = BY[key]["default"]
    if d.startswith("#"):
        return 0xFF000000 | int(d[1:], 16)
    if d.startswith("="):
        return resolve(d[1:])
    if d.startswith("~"):
        t = int(d[1:]) / 100.0
        a, b = resolve("BACKGROUND"), resolve("ACCENT")
        out = 0xFF000000
        for sh in (16, 8, 0):
            ca, cb = (a >> sh) & 255, (b >> sh) & 255
            out |= int(round(ca + (cb - ca) * t)) << sh
        return out
    if d.startswith("@"):
        pct, ref = d[1:].split(":")
        return (int(round(255 * int(pct) / 100.0)) << 24) | (resolve(ref) & 0xFFFFFF)
    raise ValueError(d)


def spec(d):
    if d.startswith("#"):
        return "FIXED", "0xFF%s" % d[1:].upper(), "null"
    if d.startswith("="):
        return "FROM", "0", '"%s"' % d[1:]
    if d.startswith("~"):
        return "BLEND", d[1:], "null"
    pct, ref = d[1:].split(":")
    return "ALPHA", pct, '"%s"' % ref


def esc(s):
    return s.replace("&", "&amp;").replace("'", "\\'")


def main():
    root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
    java = os.path.join(root, "src/main/java/org/thoughtcrime/securesms/shiroikuma")
    res = os.path.join(root, "src/main/res")
    os.makedirs(java, exist_ok=True)
    head = "GENERATED by tools/shiroikuma/gen_roles.py - edit the table there, never this file."

    # --- the enum
    L = ["// " + head, "package org.thoughtcrime.securesms.shiroikuma;", "",
         "import androidx.annotation.ColorRes;", "import androidx.annotation.StringRes;",
         "import org.thoughtcrime.securesms.R;", "",
         "/** The colour roles of the black-yellow theme. The two primaries are written here, once. */",
         "public enum SkRole {"]
    for i, r in enumerate(R):
        kind, arg, ref = spec(r["default"])
        names = ", ".join('"%s"' % n for n in r["names"])
        L.append('  %s("%s", Section.%s, R.string.sk_role_%s, R.color.sk_role_%s, Kind.%s, %s, %s, new String[] {%s})%s'
                 % (r["key"], r["storage"], r["section"], r["key"].lower(), r["key"].lower(), kind, arg, ref, names,
                    ";" if i == len(R) - 1 else ","))
    L += ["",
          "  /** Where a role's swatch row sits on the UI page. */",
          "  public enum Section {"]
    for i, (sid, _, _) in enumerate(SECTIONS):
        L.append("    %s(R.string.sk_section_%s)%s" % (sid, sid.lower(), ";" if i == len(SECTIONS) - 1 else ","))
    L += ["", "    @StringRes public final int label;", "",
          "    Section(@StringRes int label) {", "      this.label = label;", "    }", "  }", "",
          "  /** How a role's default is found. */",
          "  public enum Kind {", "    FIXED,", "    FROM,", "    BLEND,", "    ALPHA", "  }", "",
          "  /** The key the value is stored under in the default preferences. */",
          "  public final String storageKey;", "",
          "  public final Section section;", "  @StringRes public final int label;",
          "  @ColorRes public final int colorRes;", "  public final Kind kind;",
          "  /** FIXED: the colour. BLEND: percent from background to accent. ALPHA: percent alpha. */",
          "  public final int arg;", "",
          "  /** FROM and ALPHA: the name of the role the default comes from. */",
          "  public final String from;", "",
          "  /** Upstream colour resources that show this role. */",
          "  public final String[] upstreamNames;", "",
          "  SkRole(String storageKey, Section section, @StringRes int label, @ColorRes int colorRes, Kind kind,",
          "      int arg, String from, String[] upstreamNames) {",
          "    this.storageKey = storageKey;", "    this.section = section;", "    this.label = label;",
          "    this.colorRes = colorRes;", "    this.kind = kind;", "    this.arg = arg;", "    this.from = from;",
          "    this.upstreamNames = upstreamNames;", "  }", "}", ""]
    open(os.path.join(java, "SkRole.java"), "w").write("\n".join(L))

    # --- role colour resources
    L = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- %s" % head,
         "     Role colour resources with each role's computed default. Default values/ ONLY. -->", "<resources>"]
    for r in R:
        L.append('    <color name="sk_role_%s">#%08X</color>' % (r["key"].lower(), resolve(r["key"])))
    L += ['    <!-- Not a role: the identity tint of a message bubble (PorterDuff MULTIPLY). -->',
          '    <color name="sk_identity_white">#FFFFFFFF</color>', "</resources>", ""]
    open(os.path.join(res, "values/sk_roles.xml"), "w").write("\n".join(L))

    # --- theme overlay
    L = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- %s" % head,
         "     Laid over the dark theme of every screen: each colour attribute names a role, and the",
         "     run-time loader (shiroikuma/SkPalette) feeds the stored value. -->", "<resources>",
         "    <!-- The colours alone: for a screen whose own theme brings its view inflater (the",
         "         Material 3 call screen). -->",
         '    <style name="ThemeOverlay.Sk.Colors" parent="">']
    for r in R:
        for a in r["attrs"]:
            L.append('        <item name="%s">@color/sk_role_%s</item>' % (a, r["key"].lower()))
    L += ["    </style>",
          "    <!-- Every other screen: the colours, and the inflater that restyles buttons, switches",
          "         and marks as they are inflated. -->",
          '    <style name="ThemeOverlay.Sk" parent="ThemeOverlay.Sk.Colors">',
          '        <item name="viewInflaterClass">org.thoughtcrime.securesms.shiroikuma.SkViewInflater</item>',
          "    </style>", "</resources>", ""]
    open(os.path.join(res, "values/sk_theme.xml"), "w").write("\n".join(L))

    # --- labels
    for lang, idx, sidx in (("values", "en", 1), ("values-ja", "ja", 2)):
        L = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- %s -->" % head, "<resources>"]
        for s in SECTIONS:
            L.append('    <string name="sk_section_%s">%s</string>' % (s[0].lower(), esc(s[sidx])))
        for r in R:
            L.append('    <string name="sk_role_%s">%s</string>' % (r["key"].lower(), esc(r[idx])))
        L += ["</resources>", ""]
        open(os.path.join(res, lang, "sk_role_strings.xml"), "w").write("\n".join(L))
    print("%d roles in %d sections" % (len(R), len(SECTIONS)))


if __name__ == "__main__":
    main()
