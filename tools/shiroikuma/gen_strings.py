#!/usr/bin/env python3
"""shiroikuma fork: every user-visible string the fork owns, with its Japanese translation.

Run from the repository root:  python3 tools/shiroikuma/gen_strings.py

Writes src/main/res/values/sk_strings.xml and src/main/res/values-ja/sk_strings.xml (and the two
sk_arrays.xml). The fork's strings live in files of their own, NOT in strings.xml, so that
upstream's edits and scripts/rebrand.sh never touch them, and values/strings.xml differs from
upstream by app_name alone. A string added here without its Japanese fails the run.
"""
import os

# key: (English, Japanese). Keep placeholders identical in both.
S = {
    # --- the UI page
    "shiroikuma_ui_category": ("白い熊 ArcaneChat UI", "白い熊 ArcaneChat UI"),
    "sk_app_settings": ("App settings", "アプリの設定"),
    "sk_app_settings_summary": ("Opens the settings of ArcaneChat itself.", "ArcaneChat 本体の設定を開きます。"),
    "sk_section_maintenance": ("Maintenance", "メンテナンス"),
    "sk_reset_order": ("Reset section order", "セクションの並び順を元に戻す"),
    "sk_reset_order_summary": ("Puts the sections of this page back in their original order.",
                               "このページのセクションを最初の並び順に戻します。"),
    "sk_reset_colors": ("Reset all colours", "すべての色を元に戻す"),
    "sk_reset_colors_summary": ("Gives every colour on this page back its default. Fonts and other settings stay.",
                                "このページのすべての色を既定に戻します。フォントなど他の設定はそのままです。"),
    "sk_reset_colors_confirm": ("Every colour you have set on this page will go back to its default.",
                                "このページで設定したすべての色が既定に戻ります。"),
    "sk_color_default": ("Default (%1$s)", "既定（%1$s）"),
    "sk_default": ("Default", "既定"),
    "sk_storage_not_granted": ("Storage access was not granted.", "ストレージへのアクセスが許可されませんでした。"),
    "sk_reset": ("Reset", "元に戻す"),
    "sk_font_family": ("Family", "書体"),
    "sk_font_weight": ("Weight", "太さ"),
    "sk_font_size": ("Size (sp)", "大きさ (sp)"),
    "sk_font_grant_access": ("Grant access to all files, then pick the font again.",
                             "すべてのファイルへのアクセスを許可してから、もう一度フォントを選んでください。"),
    "sk_font_unreadable": ("That file could not be read.", "このファイルは読み込めませんでした。"),
    "sk_font_added": ("Added %1$s", "%1$s を追加しました"),
    "pref_colors_category": ("Colours", "色"),
    "pref_fonts_category": ("Fonts", "フォント"),
    "pref_ui_section_chatlist": ("Chat list", "チャット一覧"),
    "pref_ui_section_conversation": ("Conversation", "会話"),
    "pref_ui_section_custom_fonts": ("Custom fonts", "追加したフォント"),
    "pref_ui_section_ticks": ("Delivery ticks", "送信状態の印"),
    "pref_ui_section_eximport": ("Export / Import", "エクスポート／インポート"),
    "pref_chatlist_style": ("Chat list style", "チャット一覧の様式"),
    "pref_font_chat_text": ("Chat text", "メッセージの文字"),
    "pref_font_conv_title": ("Conversation title", "会話の題名"),
    "pref_font_list_title": ("Chat-list title", "チャット一覧の題名"),
    "pref_font_list_preview": ("Chat-list preview", "チャット一覧のプレビュー"),
    "pref_font_list_date": ("Chat-list date & time", "チャット一覧の日時"),
    "pref_font_settings": ("Settings", "設定"),
    "pref_font_add": ("Add custom font…", "フォントを追加…"),
    "pref_font_add_summary": ("Pick a .ttf or .otf file to use in any category.",
                              ".ttf または .otf ファイルを選ぶと、どの項目でも使えます。"),
    "pref_ticks_size": ("Tick size", "印の大きさ"),
    "pref_ticks_size_summary": ("%1$d dp — applies to the chat bubbles and the chat list",
                                "%1$d dp — 吹き出しとチャット一覧の両方に適用されます"),
    "pref_ticks_spin": ("Spin the sending glyph", "送信中の印を回転させる"),
    "pref_ticks_spin_summary": ("Rotate the icon while the message is still leaving your phone.",
                                "メッセージがまだ端末から送り出されている間、印を回します。"),
    "pref_ticks_group_all": ("Distinguish “received by all” in groups", "グループで「全員が受信済み」を区別する"),
    "pref_ticks_group_all_summary": (
        "Show a separate tick once every group member has returned a read receipt. Off by default: it costs one extra query per outgoing group message.",
        "グループの全員から開封確認が返ってきたら、別の印を表示します。既定ではオフです。送信するグループメッセージごとに問い合わせが一回増えます。"),
    "pref_ticks_subgroup_sending": ("Sending", "送信中"),
    "pref_ticks_subgroup_sent": ("Sent", "送信済み"),
    "pref_ticks_subgroup_received": ("Received", "受信済み"),
    "pref_ticks_subgroup_received_all": ("Received by all (groups)", "全員が受信済み（グループ）"),
    "pref_ticks_subgroup_failed": ("Failed", "失敗"),
    "pref_ticks_glyph": ("Glyph", "印の形"),
    "pref_reset_ui": ("Reset all to defaults", "すべて既定に戻す"),
    "pref_reset_ui_summary": ("Restores every colour, font and chat-list style of this page.",
                              "このページの色・フォント・チャット一覧の様式をすべて元に戻します。"),
    "pref_reset_ui_confirm_title": ("Reset 白い熊 UI?", "白い熊 UI を元に戻しますか？"),
    "pref_reset_ui_confirm_message": (
        "This restores every colour, font and chat-list style to its default. Your added font files are kept.",
        "色・フォント・チャット一覧の様式をすべて既定に戻します。追加したフォントファイルは残ります。"),
    # --- export / import
    "eim_row_title": ("Export / Import", "エクスポート／インポート"),
    "eim_row_summary": (
        "Export or import everything — accounts, colours, fonts, protected contacts, app settings — by category.",
        "アカウント・色・フォント・保護した連絡先・アプリの設定を、区分ごとにエクスポートまたはインポートします。"),
    "eim_dialog_title": ("Export / Import — 白い熊 ArcaneChat", "エクスポート／インポート — 白い熊 ArcaneChat"),
    "eim_dialog_desc": ("Save or load every setting as selectable categories.",
                        "すべての設定を、選べる区分ごとに保存または読み込みます。"),
    "eim_dir_caption": ("Export directory (tap to choose)", "エクスポート先のディレクトリ（タップして選択）"),
    "eim_dir_unset": ("Not set — tap to choose a directory", "未設定 — タップしてディレクトリを選択"),
    "eim_last_export": ("Last export: %1$s", "前回のエクスポート：%1$s"),
    "eim_warn_nodir": ("No directory set yet — pick one to enable one-tap export.",
                       "ディレクトリが未設定です。選ぶとワンタップでエクスポートできます。"),
    "eim_warn_none": ("No export in this directory yet.", "このディレクトリにはまだエクスポートがありません。"),
    "eim_select_all": ("Select all", "全選択"),
    "eim_cat_accounts": ("Accounts (profiles · chats · messages)", "アカウント（プロファイル・チャット・メッセージ）"),
    "eim_cat_ui": ("UI customizations (colours · fonts · chat list)", "UI の設定（色・フォント・チャット一覧）"),
    "eim_cat_protected": ("Protected contacts", "保護した連絡先"),
    "eim_cat_app": ("App settings (everything else)", "アプリの設定（その他すべて）"),
    "eim_export": ("Export", "エクスポート"),
    "eim_import": ("Import", "インポート"),
    "eim_export_ok": ("Exported: %1$s", "エクスポートしました：%1$s"),
    "eim_export_fail": ("Export failed: %1$s", "エクスポートに失敗しました：%1$s"),
    "eim_import_fail": ("Import failed: %1$s", "インポートに失敗しました：%1$s"),
    "eim_import_none": ("No 白い熊 ArcaneChat export found in that file.",
                        "このファイルに 白い熊 ArcaneChat のエクスポートは見つかりませんでした。"),
    "eim_none_selected": ("Select at least one category.", "区分を一つ以上選んでください。"),
    "eim_accounts_result": ("%1$d imported · %2$d already present", "%1$d 件をインポート・%2$d 件は既にあります"),
    "eim_accounts_failed": ("\" · %1$d failed\"", "\"・%1$d 件は失敗\""),
    "eim_export_done_title": ("✓ Export complete", "✓ エクスポート完了"),
    "eim_import_done_title": ("✓ Import complete", "✓ インポート完了"),
    "eim_import_done_body": ("Restored:\\n\\n%1$s\\n\\nRestart to apply everything.",
                             "復元した内容：\\n\\n%1$s\\n\\nすべてを反映するには再起動してください。"),
    "eim_restart_now": ("Restart now", "今すぐ再起動"),
    "eim_restart_later": ("Later", "後で"),
    "eim_progress_unit_category": ("区分", "区分"),
    "eim_progress_category": ("区分 %1$d/%2$d — %3$s", "区分 %1$d/%2$d — %3$s"),
    "eim_progress_done": ("区分 %1$d/%1$d — 完了", "区分 %1$d/%1$d — 完了"),
    "eim_progress_unit_account": ("アカウント", "アカウント"),
    "eim_progress_account": ("アカウント %1$d/%2$d — %3$s", "アカウント %1$d/%2$d — %3$s"),
    # --- automation
    "pref_automation_enabled": ("Automation export", "自動エクスポート"),
    "pref_automation_enabled_summary": (
        "Let sister apps (白い熊 自由作業盤 · 白い熊 応用管理) trigger this app\\'s export and back its data up. On by default.",
        "姉妹アプリ（白い熊 自由作業盤・白い熊 応用管理）が、このアプリのエクスポートを実行し、データをバックアップできるようにします。既定ではオンです。"),
    "pref_automation_require_token": ("Use authorization token?", "認証トークンを使いますか？"),
    "pref_automation_require_token_summary": (
        "Off: any sister app may drive the automation. On: a caller must also present the token below. Either way the data door checks the caller\\'s identity and signature.",
        "オフ：姉妹アプリはどれでも自動処理を実行できます。オン：呼び出す側は下のトークンも示す必要があります。どちらの場合も、呼び出し元の身元と署名は確認されます。"),
    "pref_automation_token": ("Automation token", "自動処理のトークン"),
    "pref_automation_token_summary": (
        "Tap to copy the full token · paste it into 自由作業盤「保存復元の設定」",
        "タップするとトークン全体をコピーします・自由作業盤「保存復元の設定」に貼り付けてください"),
    "automation_data_channel_name": ("Automation data", "自動処理のデータ"),
    "automation_data_exporting": ("Backing this app up…", "このアプリをバックアップ中…"),
    "automation_data_importing": ("Restoring this app\\'s data…", "このアプリのデータを復元中…"),
    "pref_automation_token_copied": ("Automation token copied to the clipboard", "トークンをクリップボードにコピーしました"),
    "pref_automation_regenerate": ("Regenerate", "作り直す"),
    "pref_automation_regenerate_title": ("Regenerate the automation token?", "トークンを作り直しますか？"),
    "pref_automation_regenerate_message": (
        "The current token stops working immediately. Every place it has been pasted — 自由作業盤「保存復元の設定 -- [979][01]」 — must be updated with the new one.",
        "現在のトークンはすぐに使えなくなります。貼り付けたすべての場所（自由作業盤「保存復元の設定 -- [979][01]」）を、新しいトークンに書き換える必要があります。"),
    "pref_automation_regenerated": ("New automation token generated", "新しいトークンを作成しました"),
    # --- notifications
    "protected_notification_channel_name": ("Protected contacts", "保護した連絡先"),
}

# name: (English items, Japanese items); the values arrays stay in arrays.xml
A = {
    "chatlist_style_entries": (
        ["Cards", "Filled card", "Accent bar", "Dividers", "Inset divider", "Plain"],
        ["カード", "塗りカード", "アクセントの帯", "区切り線", "字下げした区切り線", "なし"]),
    "sk_font_weights": (
        ["Default", "Thin", "Light", "Normal", "Medium", "Semi-Bold", "Bold", "Black"],
        ["既定", "極細", "細字", "標準", "中字", "中太", "太字", "極太"]),
    "tick_glyph_entries": (
        ["Single tick", "Double tick", "Triple tick", "Clock", "Dot", "Tick in a circle", "Up arrow",
         "Exclamation mark"],
        ["チェック一つ", "チェック二つ", "チェック三つ", "時計", "点", "丸の中のチェック", "上向き矢印", "感嘆符"]),
}


def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;")


def main():
    res = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src/main/res")
    head = "GENERATED by tools/shiroikuma/gen_strings.py - edit the table there, never this file."
    for d, i in (("values", 0), ("values-ja", 1)):
        L = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- %s -->" % head, "<resources>"]
        for k, v in S.items():
            if not v[i]:
                raise SystemExit("missing translation: " + k)
            L.append('    <string name="%s">%s</string>' % (k, esc(v[i])))
        L += ["</resources>", ""]
        open(os.path.join(res, d, "sk_strings.xml"), "w").write("\n".join(L))
        L = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- %s -->" % head, "<resources>"]
        for k, v in A.items():
            assert len(v[0]) == len(v[1]), k
            L.append('    <string-array name="%s">' % k)
            L += ["        <item>%s</item>" % esc(x) for x in v[i]]
            L.append("    </string-array>")
        L += ["</resources>", ""]
        open(os.path.join(res, d, "sk_arrays.xml"), "w").write("\n".join(L))
    print("%d strings, %d arrays" % (len(S), len(A)))


if __name__ == "__main__":
    main()
