---
name: arcanechat-fork
description: Build and maintain the user's customized fork of ArcaneChat for Android (package shiroikuma.arcanechat, installable side-by-side with any official ArcaneChat). ArcaneChat is a Delta Chat-based messenger with a native Rust core (deltachat-core-rust) pulled in as a git submodule. Use this skill any time the user mentions ArcaneChat, arcane-chat, ArcaneChat/android, shiroikuma.arcanechat, their ArcaneChat fork, asks to pull/sync a new ArcaneChat version from upstream, asks to rebuild ArcaneChat, asks to apply a change to ArcaneChat, or references their Huawei Mate XT + ArcaneChat build pipeline. This fork follows the same model as the user's FairEmail and SimpleX forks: a `custom` branch carrying their changes, rebased onto each upstream release tag. Default to assuming this skill applies when in doubt during a session about ArcaneChat for Android. Follows the shell-formatting conventions in `shell-block-formatting`.
---

# ArcaneChat — customized fork build skill

The user maintains a customized build of [ArcaneChat/android](https://github.com/ArcaneChat/android) and runs it on a Huawei Mate XT alongside (potentially) the official ArcaneChat. ArcaneChat is built on the Delta Chat Android codebase — it still carries the historical Signal namespace `org.thoughtcrime.securesms` internally (this does **not** conflict between installed apps and is never changed). The app links a native Rust core (`deltachat-core-rust`) compiled per-ABI.

The fork model is identical to the user's FairEmail and SimpleX forks: fork on GitHub, a `main` branch mirroring upstream, a `custom` branch carrying the user's changes, rebased onto each new upstream release tag. The user builds locally and sideloads; builds are signed with a stable per-fork keystore so reinstalls land in place.

## Project identity

| Item | Value |
|------|-------|
| Upstream repo | `ArcaneChat/android` (remote `upstream`, HTTPS, read-only) |
| Fork repo | `git@github.com:ShiroiKuma0/arcanechat.git` (remote `origin`, SSH) |
| Local working tree | `~/git/shiroikuma-arcanechat` |
| Native core submodule | `jni/deltachat-core-rust` → `https://github.com/ArcaneChat/core` |
| Custom applicationId | `shiroikuma.arcanechat` (set on the **foss** flavor) |
| Custom app label | `白い熊 ArcaneChat` (`app_name` in `src/main/res/values/strings.xml`) |
| Java/Kotlin namespace (unchanged) | `org.thoughtcrime.securesms` |
| Build flavor | **foss** (no Google Play Services / Firebase) |
| Target ABI | `arm64-v8a` only (Mate XT) |
| Custom signing keystore | `~/.android-keystores/arcanechat-custom.jks` (PKCS12, alias `arcanechat-custom`). The passphrase is **not** committed; it lives in `~/.gradle/gradle.properties` as `DC_RELEASE_STORE_PASSWORD` and `DC_RELEASE_KEY_PASSWORD` (see one-time setup). |
| Output APK directory (build) | `build/outputs/apk/foss/release/` |
| Output APK directory (archive) | `~/tmp/` and on-device `/sdcard/tmp/` |
| Build host | Tuxedo OS |
| Build JDK | OpenJDK 21 at `/usr/lib/jvm/java-21-openjdk-amd64` (already the global `JAVA_HOME`) |
| Android SDK | `/home/shiroikuma/android-sdk` (`ANDROID_HOME` / `ANDROID_SDK_ROOT`) |
| Android NDK | `27.0.12077973` (the `ndkVersion` in `build.gradle`), at `$ANDROID_HOME/ndk/27.0.12077973` |
| Rust | rustup; the android target must be on the toolchain pinned by `scripts/rust-toolchain` (was `1.91.1`) |
| App version scheme | `versionName <upstream>+<N>`, base `versionCode <upstreamCode> + N` (final per-ABI is `×10+abi`); currently `2.49.0+4` / base `30000746`; committed bump per feature (see Versioning) |
| APK filename grammar | `shiroikuma-arcanechat_<versionName>_arm64-v8a.apk` (e.g. `shiroikuma-arcanechat_2.49.0+1_arm64-v8a.apk`); version-based, no timestamp |

Apply the `shell-block-formatting` conventions: every command in chat blocks gets a cyan `>>>` echo prefix, stderr is recolored red via the `r()` helper, and expensive steps (native build, Gradle) sit behind a `read -p` pause gate. Use the **ANSI-C-quoted** sed in the helper — `r() { "$@" 2> >(sed $'s/.*/\033[1;31m&\033[0m/' >&2); }` — never the plain-single-quoted form, which GNU sed mangles into tripled lines and raw `33[`.

## Branch / remote model

| Branch | Purpose | Update mode |
|--------|---------|-------------|
| `main` | Mirrors `ArcaneChat/android`. Never carries our changes. | Fast-forward only |
| `custom` | Carries all customizations below. | Rebased onto each upstream release tag |

`origin` = the user's fork (SSH, push here). `upstream` = `ArcaneChat/android` (HTTPS, fetch only). The submodule is re-synced (`git submodule update --init --recursive`) after every checkout/rebase.

## Customizations on `custom`

These are the only committed changes. All are small and rebase cleanly in normal cases.

1. **foss applicationId** — inside the `foss { }` product flavor in `build.gradle`, add `applicationId "shiroikuma.arcanechat"`. (The foss flavor otherwise defaults to `chat.delta.lite`.) FileProvider authorities are `${applicationId}.fileprovider` / `.attachments`, so they follow this automatically — no manifest edits needed for side-by-side install.
2. **App label** — `app_name` = `白い熊 ArcaneChat` (was `ArcaneChat`). Set in base `src/main/res/values/strings.xml` **and** in all ~45 localized `values-*/strings.xml` (Delta Chat ships a translated `app_name` per locale; under a non-English system locale the launcher would otherwise show the translated "Delta Chat"/"ArcaneChat", so every locale override must be set too).
3. **google-services client** — `google-services.json`: a client entry with `package_name` = `shiroikuma.arcanechat`. Required because of the build trap described below. Minimal-diff textual insert right after the `  "client": [` line; do **not** reserialize the whole JSON (avoids rebase churn).
4. **In-app language picker** — a `ListPreference` (key `pref_language`) under Settings → Appearance, driving AndroidX per-app locales (`AppCompatDelegate.setApplicationLocales`) as the single source of truth. The pref is `persistent="false"` and mirrors `getApplicationLocales()`; persistence is handled by the manifest `androidx.appcompat.app.AppLocalesMetadataHolderService` (`autoStoreLocales=true`) on API < 33 and by the framework on API 33+. Touches `xml/preferences_appearance.xml` + `xml-v29/preferences_appearance.xml`, `values/arrays.xml` (`pref_language_entries`/`pref_language_values`, BCP-47 tags, `""` = system), `AppearancePreferenceFragment.java`, `Prefs.java` (`LANGUAGE_PREF`), and `AndroidManifest.xml`. The `pref_language` string already ships fully localized in every `values-*` (Delta Chat kept the strings after removing its old picker), so the title localizes for free.
5. **Step 1 yellow-on-black theming** — a hardcoded `#FFFF00`-on-black palette (intended to become user-configurable in "Step 2"; the indirection below is what makes that tractable). All edits are scoped to the **dark** theme (`TextSecure.DarkTheme` / `TextSecure.DarkNoActionBar`, which is what "System default" resolves to); the user is dark-only, so shared drawables that also affect light/named themes are acceptable for now.
   - **Anchors** in `src/main/res/values/colors.xml`: a single `shiroikuma_yellow` = `#FFFF00`, plus `def_primary`/`def_primary_lite`/`def_accent` → yellow, `def_primary_alpha33` = `#55FFFF00`, and a new `def_text` = `#FFFF00`. **Step 2 should hang off `shiroikuma_yellow` / `def_text`.** This drives icons, accents, and the FAB for free.
   - **`themes.xml` (DarkTheme):** `android:textColor` → `def_text`; conversation message text (`conversation_item_incoming/outgoing_text_primary_color`) → `def_text`; **bubble fill colours (`conversation_item_incoming/outgoing_bubble_color`) → `@color/white`** — this is deliberate: `ConversationItem.setBubbleState()` tints the bubble drawable with `PorterDuff.MULTIPLY`, and white makes that an identity op so the drawable renders its own colours; chat-list `conversation_list_item_contact_color` + `_unread_color` + `_subject_color` (preview snippet) → `def_text`; outgoing `conversation_item_outgoing_text_secondary_color` (the timestamp/footer, was `def_outgoing_label`) → `def_text`.
   - **Popup / overflow menus → black + yellow border.** The menu *content* (item text) is themed via `actionBarPopupTheme` = `@style/ThemeOverlay.Shiroikuma.Popup`, but the popup *window background* resolves from the **activity theme**, so `popupMenuStyle` + `android:popupMenuStyle` + `actionOverflowMenuStyle` are all set on `DarkTheme` itself (covers AppCompat overflow, AppCompat PopupMenu, and framework PopupMenu paths). Styles live in `styles.xml` (`ThemeOverlay.Shiroikuma.Popup`, `Widget.Shiroikuma.PopupMenu`, `Widget.Shiroikuma.PopupMenu.Overflow`).
   - **New drawables:** `dialog_yellow_border.xml` (black fill + 2dp yellow stroke + 8dp corners — reused for AlertDialog windowBackground *and* the popup `android:popupBackground`); `input_panel_bg_bordered.xml` (gray95 fill framed by a 2dp yellow border, set as the compose bar background in `conversation_input_panel.xml`). Bubble drawables `message_bubble_background_sent_alone.xml` / `_received_alone.xml` changed to solid `#000000` + 1dp `#FFFF00` stroke.
   - **One Java change:** `ConversationActivity` default conversation wallpaper set to `null` (instead of `R.drawable.background_hd`) so the background falls through to the solid-black window colour.
6. **Step 2 per-surface configurable colours** — Settings → Appearance → **Colours**, seven colour roles each stored as an ARGB int in `Prefs` (defaults reproduce the Step 1 palette): message text, bubble fill, bubble border, conversation background, chat-list title, chat-list preview, and the new-chat FAB.
   - **Why per-surface, not anchor-swap:** a runtime `Resources.getColor` override does **not** propagate to theme attributes / `?attr` / layout `@color` references — Android resolves those in the native asset layer, bypassing the Java override. This was confirmed by a throwaway probe (`ColorOverrideResources` overriding `def_text` → cyan; text stayed yellow). The probe was **not committed**. So every role is applied by setting the colour on the concrete view/drawable in code.
   - **Application sites:** `ConversationItem.setBodyText` (`bodyText.setTextColor`) and `setMessageShape` (mutates the bubble `LayerDrawable`'s inner `GradientDrawable` fill + stroke — works because the `setBubbleState` MULTIPLY tint is white/identity in the dark theme); `ConversationActivity.initializeBackground` (`backgroundView.setBackgroundColor` — see the fix note below); `ConversationListItem.bind` (`fromView`/`subjectView` `setTextColor` — the title override also forces a uniform colour over `FromTextView`'s per-recipient styling); `ConversationListFragment.onCreateView` (`fab.setBackgroundTintList`). Colours apply on next render (list on return/scroll, conversation on reopen) — no recreation wired.
   - **Step 2 follow-up (conversation-background fix):** the original site set the colour on the **decor view** in `ConversationActivity.onCreate` — but `conversation_activity.xml`'s root `RelativeLayout` paints `?attr/input_panel_bg_color` (= `@color/gray95` = `#ff111111` in the dark theme) *in front* of the decor view, and the full-screen `conversation_background` ScaleStableImageView above it gets a `null` drawable (transparent), so the configured colour was never visible — the chat area always showed `gray95` (a warm near-black that reads as brown against the yellow). Fix: apply the colour to that full-screen `backgroundView` itself via `setBackgroundColor` in `initializeBackground` (right where the wallpaper drawable is decided), so it shows as the solid backdrop with no wallpaper and behind a wallpaper image when one is set. The old decor-view `setBackgroundColor` line in `onCreate` is left in as a harmless pre-layout backdrop. **Note:** the same `gray95` still fills the area *behind* the compose bar / any gaps (it's the root background); pointing the root layout's background at the configured colour too would extend it full-screen — not done yet.
   - **Pieces:** `Prefs` keys/getters/setters per role + `COLOR_YELLOW`/`COLOR_BLACK` constants; reusable `components/ColorPickerDialog` (RGB sliders + hex + live preview) with `res/layout/dialog_color_picker.xml`; `AppearancePreferenceFragment` wires each entry (swatch icon via a `GradientDrawable`, hex summary, `getColorFor`/`setColorFor` dispatch by key); entries added to **both** `xml/preferences_appearance.xml` **and** `xml-v29/preferences_appearance.xml`.
   - **Not configurable (still fixed Step 1 yellow):** accent ripples/controls and the popup-menu background, because those are theme-attr/framework-popup driven and unreachable by per-surface code without disproportionate effort. Natural follow-ups if wanted: split incoming/outgoing into separate roles; tackle popup/accent.
7. **Step 3 per-category configurable fonts** — Settings → Appearance → **Fonts**, an independent font (family + weight + size) per text surface, same per-surface model as Step 2 (the runtime-`getColor` finding in #6 applies to typefaces too — there is no global-font asset hook, so each surface is set in code).
   - **Categories** (`Prefs` constants `FONT_CHAT_TEXT` / `FONT_CONV_TITLE` / `FONT_LIST_TITLE` / `FONT_LIST_PREVIEW` / `FONT_SETTINGS`): chat message body, open-conversation toolbar title, chat-list row title, chat-list preview/snippet, and all settings rows. Each stored as three keys `pref_font_<cat>_{family,weight,size}` (defaults `""`/`0`/`0` = inherit).
   - **`FontUtil`** resolves a typeface from a **family** value — `""` = keep the view's current typeface; a system family name (`sans-serif`, `serif`, `monospace`, `cursive`, the `sans-serif-*` variants); or `"file:<absolute path>"` for a picked font — an optional **weight** (real per-weight via `Typeface.create(base, weight, false)` on API 28+, `BOLD`/`NORMAL` fallback below), and an optional **size** in sp (`0` = unchanged). Weight and size are independent of family, so they apply to the default typeface too. `SIZE_MAX` = 96 (slider), `SIZE_HARD_CAP` = 300 (typed, "even bigger if desired"). `apply(TextView, family, weight, size)` takes **no** Context — the file path is absolute. `clearCache()` clears the per-path `Typeface` cache after the picked-files list changes.
   - **Custom fonts are picked as individual `.ttf`/`.otf` files** (FairEmail-style, not a folder, not an import-copy): an "Add custom font…" preference launches the system file picker (`ActivityResultContracts.OpenDocument`, `*/*`), and the file is **referenced in place by its real path — no copy**. The content URI is resolved to a path in `AppearancePreferenceFragment.resolveFontPath` (handles `ExternalStorageProvider` `primary:`→`/storage/emulated/0/…` and `volume:`→`/storage/<vol>/…`); `FontUtil.copyToInternal` (into `filesDir/fonts`) is a **fallback only** when a URI can't be resolved. Reading arbitrary paths needs **All-files access** on API 30+ — `MANAGE_EXTERNAL_STORAGE` (added to the manifest with `tools:ignore="ScopedStorage"`), checked via `Environment.isExternalStorageManager()` and requested via `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`; `READ_EXTERNAL_STORAGE` runtime request on API 23–29. Picked paths are remembered in a deduped newline-list `pref_font_files` (`Prefs.getFontFiles`/`addFontFile`) and offered in **every** category's Family list.
   - **Family list is sorted alphabetically** with **Default pinned first** (`FontUtil.sortedEntries` builds `(value,label)` pairs from builtins + picked files, pins `""`, then `compareToIgnoreCase` on label; `allFamilyValues`/`allFamilyLabels` both derive from it so they stay index-aligned).
   - **Application sites** (each right after that surface's Step 2 colour, where applicable): `ConversationItem.setBodyText` (body); `ConversationTitleView.onFinishInflate` (toolbar `title` — set once, survives both `setTitle` overloads); `ConversationListItem.bind` (`fromView` title + `subjectView` preview — the preview `apply` overrides the fresh/read `BOLD_TYPEFACE`/`LIGHT_TYPEFACE` **only when a font is configured**, since unset family/weight/size is a no-op that keeps the current typeface); **Settings** via a typeface walker on the shared `CorrectedPreferenceFragment` base (`onViewCreated` → recurse all `TextView`s in each preference-`RecyclerView` row, applied to on-screen rows and re-applied on `OnChildAttachStateChangeListener` child-attach because the RecyclerView recycles; **fully no-op unless a Settings font is configured**). This base is the parent of all six settings fragments, so one hook covers the whole settings UI.
   - **Pieces:** `util/FontUtil.java`; reusable `components/FontPickerDialog.java` (family Spinner from `allFamilyValues`/`Labels`, weight Spinner, size SeekBar `0..SIZE_MAX` with an editable number field accepting up to `SIZE_HARD_CAP`, live preview, Reset neutral button) + `res/layout/dialog_font_picker.xml`; `Prefs` (category constants + `getFontFamily`/`getFontWeight`/`getFontSize`/`setFont` + `getFontFiles`/`addFontFile`); `AppearancePreferenceFragment` (`initializeFontPref` per category — summary via `FontUtil.describe`, click opens the dialog; the "Add custom font…" pref + SAF/all-files/read launchers + `onFontPicked`/`resolveFontPath`/`queryDisplayName`); `CorrectedPreferenceFragment` (settings walker). Preference entries added to **both** `xml/preferences_appearance.xml` **and** `xml-v29/preferences_appearance.xml`.
   - **Step 3 follow-up (row height):** the chat-list row in `res/layout/conversation_list_item_view.xml` had a fixed `72dp` height with a `match_parent` text block, so a larger list-title/preview font clipped into the next row. Changed to `wrap_content` + `minHeight="72dp"` + 6dp vertical padding, and the text block to `wrap_content` + `layout_centerVertical` — normal rows look unchanged, larger fonts grow the row. Watch the **conversation-title** toolbar (fixed-height) for clipping at very large sizes — not fixed yet.
8. **Step 4a per-direction message colours** — splits the three Step 2 message-bubble roles (message text, bubble fill, bubble border) into **incoming** and **outgoing** variants, so sent/received messages can be coloured independently (Colours section now has six message rows instead of three; conversation background, chat-list title/preview, FAB stay single). New keys `pref_color_*_in`/`_out`; direction-aware overloads `Prefs.getMessageTextColor(c, outgoing)` / `getBubbleFillColor(c, outgoing)` / `getBubbleBorderColor(c, outgoing)` + matching setters, each **defaulting to the legacy single-value getter** (which still defaults to the Step 1 palette), so existing colours carry to both directions until differentiated — no migration. `ConversationItem` picks the role via `messageRecord.isOutgoing()` (`setBodyText`) and `current.isOutgoing()` (`setMessageShape`). `AppearancePreferenceFragment` dispatches the six keys to the direction-aware accessors; entries in both xml variants.
9. **Step 4b configurable accent via preset theme overlays** — the accent (`colorAccent`/`colorControlActivated`/`colorControlHighlight`) and popup-menu *window* background are theme-attr / static-drawable surfaces resolved in the asset layer, so the per-surface code approach (used for all the colour roles above) **structurally cannot reach them** (same root cause as the #6 finding). Solution: a small set of accent **presets** applied as a runtime theme overlay.
   - **Settings → Appearance → Colours → Accent colour** is a `ListPreference` (key `pref_accent`, `persistent="false"`) choosing yellow (default = the Step 1 look), white, cyan, green, orange, red, magenta, blue. `Prefs.getAccent`/`setAccent` (default `"yellow"`); `arrays.xml` `accent_entries`/`accent_values`; the eight `acc_*` colours in `colors.xml`.
   - **Mechanism:** each preset is a tiny `ThemeOverlay.Shiroikuma.Accent.*` style in `styles.xml` setting only the three control attrs. **It MUST carry `parent=""`** — without it AAPT infers an implicit parent from the dotted name (`ThemeOverlay.Shiroikuma.Accent`) which doesn't exist and resource linking fails (see build trap #7). The popup border drawable (`dialog_yellow_border`, stroke now `?attr/colorAccent`) and popup text (`ThemeOverlay.Shiroikuma.Popup` `android:textColorPrimary` now `?attr/colorAccent`) reference the accent, so popups + dialog borders track it automatically — one pick drives ripples, switches, checkboxes, cursors, overflow-menu border/text, and dialog borders together.
   - **Applied in `DynamicTheme`:** `onCreate` does `activity.getTheme().applyStyle(getAccentOverlay(activity), true)` right after `setTheme`; `getAccentOverlay` maps the pref key → overlay style; a new `currentAccent` field is added to the existing `currentTheme` recreate check in `onResume`, so every screen repaints when the accent changes. `AppearancePreferenceFragment` also calls `requireActivity().recreate()` on change for instant feedback in settings (the picker dialog alone doesn't trigger `onResume`). The FAB keeps its own Step 2 per-surface colour and is unaffected. **Arbitrary (free-ARGB) accent is intentionally not offered** — it isn't cleanly possible in this AppCompat app without runtime resource generation; presets are the robust path.
10. **Step 5–7 selectable chat-list row style** — Settings → Appearance → Colours → **Chat list style** = Cards / Filled card / Accent bar / Dividers / Inset divider / Plain (`Prefs.CHATLIST_STYLE_PREF`, default `"cards"`). Step 5 first made every row a rounded accent-bordered card; Step 6 generalised it into a three-way picker; Step 7 added Filled card, Accent bar, and Inset divider.
   - **The six styles:** **Cards** — rounded card (black fill, 12dp corners, 1.5dp `?attr/colorAccent` border) + 8dp side / 4dp top-bottom margin gap. **Filled card** — same card with a subtle tint fill (`@color/card_fill_tint`) instead of black. **Accent bar** — flush row, black body with a 4dp `?attr/colorAccent` band on the leading edge (accent layer revealed by a left-inset black layer) + faint divider. **Dividers** — original (non-card) background + a full-width `?attr/colorAccent` line. **Inset divider** — same accent line, inset 72dp so it starts after the avatar. **Plain** — original background + the faint `?attr/conversation_list_item_divider` line (pre-Step-5 look). Each boxed/accent style has a normal **and** a pinned drawable (pinned swaps the fill/body to `@color/pinned_bg`): `conversation_list_item_card_background` / `_card_filled_background` / `_accentbar_background` (+ `pinned_…` of each). All are `<ripple>`s that preserve the tap ripple and the `state_selected` multi-select tint. The original `conversation_list_item_background`/`pinned_list_item_background` drawables stay non-card to serve dividers/inset/plain.
   - **Applied in `ConversationListItem.setBgColor`** (which only runs on the chat-list `bind` overload at ~line 110/169 — the `ProfileAdapter` reuse of this view uses other `bind` overloads that don't call it, so it stays scoped to the chat list): a `switch` picks the card/filled/accentbar drawable (else the original themed attr background); sets row margins via the item's `MarginLayoutParams` (gap only for the boxed cards/filled); and sets the `item_divider` view — GONE for cards/filled, accent line for dividers/inset, faint line for accent-bar/plain, with a 72dp `marginStart` for inset. `dpToPx` helper. The layout's static margins/visibility are code-driven; `item_divider` is visible-by-default in XML.
   - **Repaint on change:** the picker is a `persistent="false"` `ListPreference` saved manually (no recreate). `ConversationListFragment.onResume` now forces an immediate `list.getAdapter().notifyDataSetChanged()` so a style change repaints on return rather than waiting for the existing 60-second reload timer. Entries in `arrays.xml` (`chatlist_style_entries`/`values`) + both `preferences_appearance.xml` variants.
11. **Step 8 compose bar + document chips accented** — everything below uses `?attr/colorAccent`, so it defaults to yellow and tracks the accent preset.
   - **Compose bar (`conversation_input_panel.xml` + `microphone_recorder_view.xml`):** the emoji toggle, camera (`quick_camera_toggle`), and mic (`quick_audio_toggle`) icons get `android:tint="?attr/colorAccent"`. A new always-visible **left attach paperclip** (`@+id/attach_button_left`, `ic_attach_white_24dp`, accent tint) was added as the first child of the input row. The right attach/send glyphs (`attach_button`, `send_button`) are also accent-tinted.
   - **Left paperclip wiring (`ConversationActivity`):** new field `attachButtonLeft`, found via `ViewUtil.findById`, given the same `AttachButtonListener` + `AttachButtonLongClickListener` as the right one. `handleAddAttachment()` now anchors the `AttachmentTypeSelector` to whichever attach button is visible — the right one when there's no text, else the left paperclip (the right one becomes the send button once text is entered).
   - **Send button (`send_button_bg.xml`):** changed from a solid `?attr/fab_color` oval to a **black oval with a 1.5dp accent border**; the attach/send glyph inside is accent-tinted. So the right button is black-bg + accent-border + accent-icon in both attach and send states.
   - **Document chip (`document_view.xml`, shared by sent + received document bubbles):** the blue `CircleColorImageView` (which sets its circle as the *background* in its constructor, so an XML background would be clobbered) was swapped for a plain `ImageView` over a new `document_icon_bg.xml` (black oval + accent border) with an accent-tinted glyph; `file_name`/`file_size` text recoloured to `?attr/colorAccent`. (`R.id.document_button` is also used in `attachment_type_selector.xml`, a different layout — unaffected.)
12. **Step 10 consolidated "白い熊 ArcaneChat UI" page** — all the per-surface customizations (colours, fonts, accent preset, chat-list row style) are **moved off** Settings → Appearance into one dedicated page, reached from the conversation-list overflow menu **and** from the top of Settings (mirroring the sister forks `shiroikuma-denwa`/`shiroikuma-messeji`, which expose a single "白い熊 … UI" page). Built with the AndroidX `PreferenceFragment` system (not the sister repos' dynamic Activity — far smaller + rebase-friendly, and reuses the existing `ColorPickerDialog`/`FontPickerDialog`/`Prefs` wiring), styled to the same look: accent **section** headers + indented **subgroups** + deeper **items**.
   - **Host:** `ShiroikumaUiActivity` (`extends PassphraseRequiredActionBarActivity`; reuses `activity_application_preferences.xml`; theme + accent overlay come free via `BaseActionBarActivity.dynamicTheme`) hosting `ShiroikumaUiPreferenceFragment` (`extends CorrectedPreferenceFragment`, so the Step-3 settings-font walker applies here too; **no** `DcContext`/`ApplicationPreferencesActivity` dependency). Page string `shiroikuma_ui_category` = `白い熊 ArcaneChat UI`. Manifest declares the activity (`exported=false`, parent `.ApplicationPreferencesActivity`).
   - **Entry points:** overflow item `menu_shiroikuma_ui` in `text_secure_normal.xml` → handled in `ConversationListActivity.onOptionsItemSelected`; Settings `Preference` `preference_category_shiroikuma_ui` (icon `ic_brightness_6_24dp`, after the profile row) in `preferences.xml` → click listener in `ApplicationPreferencesActivity` (`PREFERENCE_CATEGORY_SHIROIKUMA_UI`). Both `startActivity(ShiroikumaUiActivity)`.
   - **IA (`xml/preferences_shiroikuma_ui.xml`):** `PreferenceCategory` = section; a non-selectable `Preference` with the subheader layout = subgroup (indent 1); items at indent 1 (directly under a section) or indent 2 (under a subgroup). Sections: **General** (accent) · **Chat list** (Layout=row style; Colours=title/preview/**date**/FAB; Fonts=title/preview/**date**) · **Conversation** (Background; Incoming/Outgoing = text/fill/border each; Fonts=conv title/chat text) · **Settings screen** (Fonts=settings) · **Custom fonts** (add font) · **Reset**. Pref **keys are reused verbatim** so the moved wiring + picker-dialog titles carry over.
   - **Deep indents** (no dynamic Activity needed): `preference_shiroikuma_indent1.xml`/`_indent2.xml` are copies of `preference_item.xml` (keep the icon frame so colour swatches render) with root `paddingStart` 24dp / 56dp; `preference_shiroikuma_subheader.xml` is the subgroup header — accent-coloured bold all-caps title with a **text-width underline** via `drawable/shiroikuma_subheader_underline.xml` (a bottom-edge-only stroke: a `?attr/colorAccent` stroked rectangle with negative top/left/right insets so only the bottom line shows).
   - **Moved out of Appearance:** the colour/font/accent/chat-list wiring left `AppearancePreferenceFragment` (now theme + language + background only), and the **Colours** + **Fonts** `PreferenceCategory` blocks were deleted from **both** `xml/preferences_appearance.xml` and `xml-v29/preferences_appearance.xml`.
   - **Step 10 new items:** chat-list **date/time colour** (`Prefs.COLOR_LIST_DATE_PREF`, default `COLOR_YELLOW`) + **font** (`Prefs.FONT_LIST_DATE`), applied in `ConversationListItem.bind` (`dateView.setTextColor` + `FontUtil.apply`) beside the existing title/preview sites; **Reset all to defaults** (`Prefs.resetShiroikumaUi` restores every colour role, accent `"yellow"`, chat-list style `cards`, and per-category font assignment to defaults — the picked font-**files** list is kept; confirm `AlertDialog` → `requireActivity().recreate()`).

13. **Launcher icon — black/yellow Arcane leaf** — recoloured the adaptive **and** legacy launcher icon to the fork's `#FFFF00`-on-`#000000` palette: a yellow-outline leaf with a black interior on a black background, leaf at **65%** of the canvas.
   - **Background:** `res/values/ic_launcher_background.xml` `#7B00C8` → `#000000`.
   - **Foreground (`res/drawable/ic_launcher_foreground.xml`):** the original two white *comma* paths are replaced by the **single outer contour of their union** (one closed path) so the leaf is a clean yellow outline with **no internal seam lines** — `fillColor #000000` + `strokeColor #FFFF00` + `strokeLineJoin/Cap round`. The viewport was changed from the original non-square `228×280` (which **squished** the leaf when mapped into the 108×108 render box) to a square `108×108`; a `<group>` uniformly scales the leaf to 65% and centres it (`scaleX=scaleY=70.2/280 = 0.2507143`, `translateX 25.4186`, `translateY 18.9`), and `strokeWidth = 2.75dp / scale` keeps the outline a constant 2.75dp. The round + monochrome layers reuse this foreground, so they follow automatically.
   - **Tip fix (matters):** emit the closed path with **no duplicate closing vertex** (a zero-length segment) and rotate the `M`/`Z` seam onto the **smooth rightmost edge**, never on a tip — otherwise the `Z` join lands on an acute cusp and renders as a hook/gap at the leaf tips.
   - **Legacy rasters regenerated to match** (PIL, lossless webp): `mipmap-*/ic_launcher.webp` (square, black bg), `_round.webp` (circle-masked, transparent corners), `_foreground.webp` (transparent bg), and `src/main/ic_launcher-playstore.png` (512). `src/debug/` icons are **left untouched** (release-only build). The mipmap `_foreground.webp`s are unused (the adaptive XML references the `@drawable/` vector) but regenerated for coherence.
   - **Tooling:** the union contour was extracted offline — marching-squares (matplotlib) over the rasterised union of the two commas, RDP-simplified — by helper scripts kept in `~/tmp` (`build_contour.py`, `apply_icon_rasters2.py`), **not** committed to the repo. Re-run them to resize (change the leaf-fraction) or reshape.
   - **Themed-icon note:** the fill is literally black, so Android-13+ "Themed icons" render the leaf as a solid tinted silhouette (not an outline); switch the foreground `fillColor` to transparent (`#00000000`) if an outline themed icon is ever wanted — looks identical on the real icon since the bg is black.

The customization commits, in order:
- `Customize for shiroikuma side-by-side install: foss applicationId shiroikuma.arcanechat + distinct launcher label`
- `Add shiroikuma.arcanechat client to google-services.json so foss build passes project-wide google-services validation`
- `Add in-app language picker (Settings > Appearance) and set launcher label to 白い熊 ArcaneChat`
- `Step 1 theming: yellow-on-black palette + app name across all locales`
- `Step 2: per-surface configurable colours (Settings > Appearance > Colours)`
- `Step 3: configurable per-category fonts (family/weight/size) + font picking`
- `Step 3 follow-up: let chat-list rows grow with the configured font size`
- `Step 4a: split message text / bubble fill / bubble border into incoming + outgoing`
- `Step 4b: configurable accent via preset theme overlays (accent + popup + dialogs)`
- `Step 5: chat-list rows as rounded accent-bordered cards`
- `Step 6: selectable chat-list row style (cards / dividers / plain)`
- `Step 7: add Filled card, Accent bar, Inset divider chat-list styles`
- `Step 8: accent the compose bar + document chips; add a left attach button`
- `Step 9: adopt +N build versioning; fix buildconfig/allowBackup/debug warnings`
- `Fix Step 2 conversation background: apply the colour to the full-screen background view (was hidden behind the root layout's gray95)`
- `Step 10: consolidate all UI customizations into a dedicated "白い熊 ArcaneChat UI" page (overflow menu + Settings); add chat-list date colour/font + reset-to-defaults`
- `Launcher icon: black/yellow Arcane leaf (yellow outline, black fill, black bg, 65%); regenerate adaptive vector + legacy rasters`

Versioning **does** bump per feature now (since Step 9) — see "Versioning" below.

## Build traps — read before building

Every one of these caused a real failure during initial setup. They are environment/config issues, not code:

1. **Rust android target lands on the wrong toolchain.** `scripts/ndk-make.sh` does `export RUSTUP_TOOLCHAIN=$(cat scripts/rust-toolchain)` — it builds with the toolchain pinned in **`scripts/rust-toolchain`** (not the submodule's, not `stable`). The `aarch64-linux-android` target must be added to *that* toolchain: `rustup target add aarch64-linux-android --toolchain "$(cat scripts/rust-toolchain)"`. Adding it to `stable` produces `error[E0463]: can't find crate for 'core' ... the aarch64-linux-android target may not be installed`.
2. **`DC_RELEASE_KEY_ALIAS_GPLAY` unknown property.** The `signingConfigs { }` block configures *both* `releaseFdroid` (foss) and `releaseApk` (gplay) at configuration time, gated only on `DC_RELEASE_STORE_FILE`. So even a foss build evaluates the gplay signing config, which reads `DC_RELEASE_KEY_ALIAS_GPLAY`. All four `DC_RELEASE_*` properties must exist in `~/.gradle/gradle.properties` (both the FDROID and GPLAY aliases), or configuration fails with `Could not get unknown property 'DC_RELEASE_KEY_ALIAS_GPLAY'`.
3. **google-services validation fails for foss.** The `gplay` flavor block does `apply plugin: "com.google.gms.google-services"`, but `apply plugin` inside a flavor closure applies it **project-wide**, wiring `process<Variant>GoogleServices` for *every* variant including foss. It validates that `google-services.json` contains a client matching the variant's applicationId. Upstream foss passes because `chat.delta.lite` is in the JSON; our renamed foss fails with `No matching client found for package name 'shiroikuma.arcanechat'`. Fix = customization #3 above. (foss pulls no Firebase/GMS libs — those are `gplayImplementation` only — and the lone manifest gms reference is commented out, so the generated resources are never used at runtime; the entry only satisfies build-time validation.)
4. **Gradle wrapper download timeout.** `gradle/wrapper/gradle-wrapper.properties` ships `networkTimeout=10000` (10 s), too short for the one-time ~130 MB `gradle-8.x` download on a slow link → `SocketTimeoutException`. Bump it to `600000` in the working tree before building (idempotent sed; uncommitted — once the distribution is cached it never re-downloads). Not committed because it would conflict whenever upstream bumps the Gradle version.
5. **Rich console hides the real error.** Always build with `--console=plain` (and `--stacktrace` when diagnosing). The animated console overwrites the failure line, leaving a misleading "no APK" symptom. **Build-warning handling (current state, after Step 9):** the build command carries `--warning-mode none` (silences Gradle's "Deprecated Gradle features … Gradle 9.0" summary) and `-Pandroid.javaCompile.suppressSourceTargetDeprecationWarning=true` (silences AGP's source/target-8 warning). Step 9 additionally fixed the warnings that needed in-repo edits (all on the `custom` branch, so they re-derive on rebase — keep ours): the `buildconfig` deprecation (removed `android.defaults.buildfeatures.buildconfig=true` from `gradle.properties`; added `buildConfig true` to the `buildFeatures` block in `build.gradle`), the `debug` buildType debuggable+minify notice (`minifyEnabled false` on `debug` — we only build release, so no effect), the `allowBackup` manifest-merger warning (dropped the dangling `tools:replace="android:allowBackup"` from `src/main/AndroidManifest.xml`), and javac's obsolete source/target-8 warnings (`tasks.withType(JavaCompile){ options.compilerArgs += ['-Xlint:-options'] }` at the foot of `build.gradle`). The only remaining noise is javac's **mandatory-warning notes** (`ノート:`/`Note:` — deprecation/unchecked from upstream code, plus Glide's "Wrote GeneratedAppGlideModule"): no compiler flag suppresses these (`-nowarn`/`-Xlint:none` leave them), so the build command **filters them from the log** by piping gradle through `grep -vE '^[[:space:]]*(ノート|Note):'`, preserving gradle's exit via `${PIPESTATUS[0]}` (genuine errors aren't note lines, so they still show). These notes only appear on an actual recompile.
6. **Wrong JVM (Java 11) — `spotless` needs 17+.** The build applies `com.diffplug.spotless` which requires JVM 17+; on Java 11 configuration fails with `Could not resolve com.diffplug.spotless:spotless-plugin-gradle ... Dependency requires at least JVM runtime version 17. This build uses a Java 11 JVM`. The build wants JDK 17+ (the user runs **Java 21**). The user also maintains a **Jami** fork whose Android build wants Java 11, so the active default JVM can flip between sessions. Make ArcaneChat immune by pinning `JAVA_HOME` to a 17+ JDK in the build block (`export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64; export PATH="$JAVA_HOME/bin:$PATH"`) and running `./gradlew --stop` to kill a stale Java-11 daemon. **Also check `~/.gradle/gradle.properties` and `./gradle.properties` for an `org.gradle.java.home` line** — if Jami set it globally to Java 11 it overrides `JAVA_HOME`; comment it out or keep it project-local to Jami. Pin JAVA_HOME unconditionally in every build block now that two forks share the machine.
7. **Dotted style name → AAPT infers a nonexistent implicit parent.** A `<style>` whose name contains dots and has no `parent=` gets an *implicit* parent equal to the name up to the last dot (Android's dotted-style convention). The Step 4b accent overlays `ThemeOverlay.Shiroikuma.Accent.Yellow` etc. therefore made AAPT look for a parent `ThemeOverlay.Shiroikuma.Accent`, which doesn't exist → `error: resource style/ThemeOverlay.Shiroikuma.Accent ... not found / failed linking references` at `:processFossReleaseResources`. Fix: give every such overlay-style an explicit **`parent=""`** (correct anyway — an overlay should contribute only its own attrs). This bit a real build; any future dotted overlay style needs `parent=""`.

## One-time environment setup

Only needed on a fresh machine or fresh clone. SSH to GitHub is already configured.

```bash
r() { "$@" 2> >(sed $'s/.*/\033[1;31m&\033[0m/' >&2); }

# --- clone fork recursively, wire upstream, create custom off the current release tag ---
echo -e '\033[1;36m>>> clone fork + submodule\033[0m'
r rm -rf ~/git/shiroikuma-arcanechat
r git clone --recursive git@github.com:ShiroiKuma0/arcanechat.git ~/git/shiroikuma-arcanechat
cd ~/git/shiroikuma-arcanechat
r git remote add upstream https://github.com/ArcaneChat/android.git
r git fetch upstream --tags
# 'custom' already exists on origin after first setup; on a truly fresh fork:
#   r git checkout -b custom <latest-release-tag> ; r git submodule update --init --recursive ; r git push -u origin custom

# --- Rust: rustup + android target on the PINNED toolchain ---
echo -e '\033[1;36m>>> rust toolchain + target\033[0m'
r bash -c 'curl --proto "=https" --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --no-modify-path'
. "$HOME/.cargo/env"
toolchain=$(cat scripts/rust-toolchain)
r rustup toolchain install "$toolchain"
r rustup target add aarch64-linux-android --toolchain "$toolchain"

# --- release keystore + ALL FOUR DC_RELEASE_* props (FDROID + GPLAY aliases) ---
echo -e '\033[1;36m>>> keystore + gradle.properties\033[0m'
STOREPASS="$KEYSTORE_PASS"  # set in the shell first, pick a strong value (not committed)
r mkdir -p ~/.android-keystores
r keytool -genkeypair -v -keystore ~/.android-keystores/arcanechat-custom.jks \
  -alias arcanechat-custom -keyalg RSA -keysize 2048 -validity 10000 \
  -storetype PKCS12 -storepass "$STOREPASS" -keypass "$STOREPASS" \
  -dname "CN=shiroikuma arcanechat, OU=personal, O=shiroikuma, L=, ST=, C=JP"
r bash -c 'mkdir -p ~/.gradle; touch ~/.gradle/gradle.properties
grep -q DC_RELEASE_STORE_FILE ~/.gradle/gradle.properties || printf "\n# ArcaneChat custom (shiroikuma) release signing\nDC_RELEASE_STORE_FILE=%s/.android-keystores/arcanechat-custom.jks\nDC_RELEASE_STORE_PASSWORD=%s\nDC_RELEASE_KEY_ALIAS_FDROID=arcanechat-custom\nDC_RELEASE_KEY_ALIAS_GPLAY=arcanechat-custom\nDC_RELEASE_KEY_PASSWORD=%s\n" "$HOME" "$KEYSTORE_PASS" "$KEYSTORE_PASS"  # set KEYSTORE_PASS in your shell first; pick a strong value (not committed) >> ~/.gradle/gradle.properties'
```

NDK `27.0.12077973` is normally already installed via the SDK's `sdkmanager`; if not: `sdkmanager "ndk;27.0.12077973"`.

## Build + deploy pipeline

The canonical block, run from a clean `custom` checkout. The first native build is slow (compiles the Rust core for arm64); subsequent Kotlin/Java-only rebuilds are fast and `ndk-make.sh` only needs re-running when the core submodule actually moves. `ANDROID_NDK_ROOT` and the cargo env are inlined for the shell, nothing global is touched.

```bash
r() { "$@" 2> >(sed $'s/.*/\033[1;31m&\033[0m/' >&2); }

echo -e '\033[1;36m>>> cd ~/git/shiroikuma-arcanechat\033[0m'
cd ~/git/shiroikuma-arcanechat

echo -e '\033[1;36m>>> env: cargo + NDK\033[0m'
. "$HOME/.cargo/env"
export ANDROID_NDK_ROOT="$ANDROID_HOME/ndk/27.0.12077973"

echo -e '\033[1;36m>>> pin JDK 17+ (spotless needs it; the Jami fork may flip the default to Java 11)\033[0m'
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
r java -version
# if a build fails on spotless/Java 11 anyway, check ~/.gradle/gradle.properties and
# ./gradle.properties for an org.gradle.java.home override (it beats JAVA_HOME), and run
# ./gradlew --stop to kill a stale Java-11 daemon.

echo -e '\033[1;36m>>> ensure android target on the pinned rust toolchain\033[0m'
toolchain=$(cat scripts/rust-toolchain)
r rustup target add aarch64-linux-android --toolchain "$toolchain"

echo -e '\033[1;36m>>> bump gradle wrapper networkTimeout (working tree only, idempotent)\033[0m'
r sed -i 's/^networkTimeout=.*/networkTimeout=600000/' gradle/wrapper/gradle-wrapper.properties

ver=$(grep -oP 'versionName "\K[^"]+' build.gradle | head -1)
apk_name="shiroikuma-arcanechat_${ver}_arm64-v8a.apk"   # version (e.g. 2.49.0+1) carries the build tail; no timestamp
echo -e "\033[1;36m>>> target apk name: $apk_name\033[0m"

read -t 0.5 -n 9999 -s _flush
read -p $'\033[1;33m>>> Build native core (arm64) + assembleFossRelease? First native build is slow. (y/n) \033[0m' ans
if [[ "$ans" =~ ^[Yy]$ ]]; then
  echo -e '\033[1;36m>>> scripts/ndk-make.sh arm64-v8a\033[0m'
  if r scripts/ndk-make.sh arm64-v8a; then
    echo -e '\033[1;36m>>> ./gradlew assembleFossRelease ... (javac ノート/Note notes filtered from the log)\033[0m'
    # Pipe drops the unsuppressable javac mandatory-warning notes; ${PIPESTATUS[0]} preserves
    # gradle's real exit code, and genuine errors aren't ノート:/Note: lines so they still show.
    ./gradlew assembleFossRelease -PABI_FILTER=arm64-v8a --console=plain --warning-mode none \
        -Pandroid.javaCompile.suppressSourceTargetDeprecationWarning=true 2>&1 \
        | grep --line-buffered -vE '^[[:space:]]*(ノート|Note):'
    if [ "${PIPESTATUS[0]}" -eq 0 ]; then
      echo -e '\033[1;36m>>> built artifacts:\033[0m'
      r ls -lh build/outputs/apk/foss/release/
      src_apk=$(ls -1 build/outputs/apk/foss/release/*.apk 2>/dev/null | head -1)
      echo -e "\033[1;36m>>> selected source apk: $src_apk\033[0m"

      echo -e '\033[1;36m>>> mkdir -p ~/tmp\033[0m'
      r bash -c 'mkdir -p ~/tmp'

      echo
      echo -e '\033[1;33m================================================================\033[0m'
      echo -e '\033[1;33m>>>   CONNECT THE PHONE TO THE LAPTOP VIA USB CABLE NOW   <<<\033[0m'
      echo -e '\033[1;33m================================================================\033[0m'
      echo
      read -t 0.5 -n 9999 -s _flush
      read -p $'\033[1;33m>>> Phone connected and ready to receive the APK? (y/n) \033[0m' ans2
      if [[ "$ans2" =~ ^[Yy]$ ]]; then
        r adb devices
        echo -e "\033[1;36m>>> adb push $src_apk /sdcard/tmp/$apk_name\033[0m"
        r adb push "$src_apk" "/sdcard/tmp/$apk_name"
      else
        echo -e '\033[1;33m>>> Skipping adb push. Local backup still saved below — sideload it manually.\033[0m'
      fi

      echo -e "\033[1;36m>>> cp $src_apk ~/tmp/$apk_name (always)\033[0m"
      r cp "$src_apk" ~/tmp/"$apk_name"
      r ls -lh ~/tmp/"$apk_name"
    else
      echo -e '\033[1;31m>>> Gradle FAILED — re-run with --stacktrace and read the What went wrong / Caused by lines.\033[0m'
    fi
  else
    echo -e '\033[1;31m>>> native core build FAILED — fix before Gradle. Check the rust target + NDK env.\033[0m'
  fi
else
  echo "Aborted."
fi
```

Key invariants in this block, all required by the user's conventions:
- The **LOUD yellow phone-connect banner** before the on-device step, plus a `read -p` worded as the reminder itself. This is mandatory in every build pipeline (the user has flagged its removal before).
- `read -t 0.5 -n 9999 -s _flush` before each gate, to swallow a stray trailing newline from the pasted block so it can't auto-answer the prompt.
- The local `~/tmp/` copy is **unconditional** — a missing cable never costs the build; sideload from `~/tmp/` via KDE Connect/Bluetooth instead.
- `adb push` to `/sdcard/tmp/` (install via the phone's file manager), **not** `adb install`.

### Claude-run build (non-interactive)

The default now is that Claude builds after every app change (see Iteration cadence). Claude can't use the `read -p` gates in the pipeline above, so it runs Gradle non-interactively. Machine facts that bite a non-interactive run (all verified this machine): `ANDROID_HOME` is **not** exported in non-interactive shells and there is **no `local.properties`**, so export the SDK path explicitly; there is no `org.gradle.java.home` override, so `JAVA_HOME` is honoured (a stale Java-11 daemon from the Jami fork is replaced automatically); and Gradle packages the prebuilt `.so` straight from `libs/` (`sourceSets.main.jniLibs.srcDirs = ['libs']`, **no** `externalNativeBuild`/CMake step), so a Java/resource-only change never recompiles native — `assembleFossRelease` finishes in ~1–2 min.

```bash
cd ~/git/shiroikuma-arcanechat
export ANDROID_HOME=/home/shiroikuma/android-sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_NDK_ROOT="$ANDROID_HOME/ndk/27.0.12077973"
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
[ -f "$HOME/.cargo/env" ] && . "$HOME/.cargo/env"
sed -i 's/^networkTimeout=.*/networkTimeout=600000/' gradle/wrapper/gradle-wrapper.properties   # working-tree only, never staged
./gradlew assembleFossRelease -PABI_FILTER=arm64-v8a --console=plain --warning-mode none \
    -Pandroid.javaCompile.suppressSourceTargetDeprecationWarning=true > ~/tmp/arcanechat_build.log 2>&1
echo "GRADLE_EXIT=$?"
```

Run it in the background (Claude is re-invoked on exit); then read `~/tmp/arcanechat_build.log` for `BUILD SUCCESSFUL` — the source APK is `build/outputs/apk/foss/release/shiroikuma-arcanechat-foss-<versionName>.apk`. The log keeps the unsuppressable javac `ノート:`/`Note:` lines (harmless; the interactive pipeline filters them only for display). Then copy to `~/tmp/` under the filename grammar and ask about `adb push` via an `AskUserQuestion` dialog (Yes first = choice 1; Iteration cadence step 2): on Yes, `adb shell mkdir -p /sdcard/tmp && adb push <src> /sdcard/tmp/<apk>`.

## Versioning

Do **bump versionCode + versionName per feature** (adopted in Step 9), using the same `+N` scheme as the user's other rebranded forks (App Manager, Handy RSS, Jami) so every sideloaded build installs as an *upgrade* (never a downgrade) over the previous one. **Mechanism: committed bump in `build.gradle`** (App Manager / Handy RSS style — the build number lives in git, bumped with each feature), not the Jami `-PshiroikumaBuild=N` property style.

- **`versionName` = `<upstream versionName>+<N>`** — upstream `2.49.0` → `2.49.0+1`, `2.49.0+2`, …
- **base `versionCode` = `<upstream versionCode> + N`** — `30000742` → `30000743` (N=1), `30000744` (N=2), …
- **Why `+ N`, not the other forks' `× 10000 + N`:** ArcaneChat's upstream `versionCode` is already 8 digits (`30000742`) **and** `build.gradle`'s `applicationVariants` block already overrides the per-ABI code as `versionCode × 10 + abi` (arm64 abi = 2). So `× 10000` would blow past Android's ~2.1-billion ceiling. Adding `N` to the base keeps the final arm64 code at `(30000742 + N) × 10 + 2` (≈ 300,007,43X, well under the ceiling, +10 per build).
- Both live in `build.gradle` `defaultConfig` (`versionCode`/`versionName`, lines ~37–38) and are **committed with each feature** — every delivered feature patch bumps both (`+N`→`+N+1`, base code `+1`). One bump per built feature; iterating an unpushed feature can keep the same `+N` (a same-code reinstall is fine) — the bump matters when moving to the next feature.
- **On an upstream rebase** (new upstream version), reset the tail: `versionName` → `<new upstream versionName>+1`, base `versionCode` → `<new upstream versionCode> + 1` (read the new upstream values from the freshly-rebased `build.gradle` first). This line conflicts on every rebase — resolve by re-deriving, not by blindly keeping ours.
- **Adoption point:** the scheme started at `2.49.0+1` / `30000743` (Step 9). Builds before that were pre-convention and carried the bare upstream `2.49.0` / `30000742`.
- The **APK filename derives from `versionName`** (`shiroikuma-arcanechat_<versionName>_arm64-v8a.apk`, e.g. `shiroikuma-arcanechat_2.49.0+1_arm64-v8a.apk`), self-documenting the build number; the old `_YYYY-MM-DD_HH-MM-SS_` datetime stamp is dropped. The rename happens at deploy time in the build block; the keystore stays stable so reinstalls/upgrades over the prior build are accepted.

## Deploy / install

**Always inquire about `adb push` after every build** — the interactive pipeline does this via the LOUD phone-connect gate; a Claude-run build must ask via an `AskUserQuestion` dialog (a single yes/no question with **Yes first = choice 1**; see Iteration cadence step 2) before finishing the turn. Never silently stop at the APK, and never ask in prose.

On-device first (`adb push` to `/sdcard/tmp/`), local backup second (`cp` to `~/tmp/`). Install on the phone via its file manager. The stable keystore means rebuilds update the existing `shiroikuma.arcanechat` install without uninstall. The two coexist with any official ArcaneChat because applicationIds differ; do **not** try to install over an official build signed with a different key — Android will refuse.

## Syncing to a new upstream release

When the user asks to pull a new ArcaneChat version:

```bash
r() { "$@" 2> >(sed $'s/.*/\033[1;31m&\033[0m/' >&2); }
cd ~/git/shiroikuma-arcanechat
r git fetch upstream --tags
# pick the new tag, e.g. v2.50.0
r git checkout main
r git merge --ff-only upstream/main          # keep the mirror current
r git checkout custom
r git rebase <new-tag>                        # replay the 12 customization commits
r git submodule update --init --recursive     # core may have moved
```

Expected conflicts are tiny and predictable: the `applicationId` line in `build.gradle`, the **`versionCode`/`versionName` lines in `build.gradle`** (they always diverge — re-derive per the Versioning section, don't blindly keep ours), the `app_name` lines in `strings.xml` (base + locales), the `google-services.json` client block (rare), and the Step 1–9 files if upstream touched them — most likely `values/themes.xml`, `values/colors.xml`, `values/styles.xml`, `values/arrays.xml`, the two `message_bubble_background_*_alone.xml` drawables, `res/drawable/dialog_yellow_border.xml`, `res/drawable/conversation_list_item_background.xml` + `pinned_list_item_background.xml`, `res/drawable/send_button_bg.xml`, `res/layout/conversation_list_item_view.xml`, `res/layout/conversation_input_panel.xml`, `res/layout/microphone_recorder_view.xml`, `res/layout/document_view.xml`, `ConversationItem.java`/`ConversationActivity.java`/`ConversationListItem.java`/`ConversationListFragment.java`/`ConversationTitleView.java`/`CorrectedPreferenceFragment.java`/`AppearancePreferenceFragment.java`/`ConversationListActivity.java`/`ApplicationPreferencesActivity.java`/`util/DynamicTheme.java`/`util/Prefs.java`, the two `preferences_appearance.xml` variants (Step 10 emptied their Colours/Fonts categories), `res/xml/preferences.xml` + `res/menu/text_secure_normal.xml` (Step 10 entry points), `AndroidManifest.xml` (the `MANAGE_EXTERNAL_STORAGE` line + the Step 10 `.ShiroikumaUiActivity` declaration) + `src/main/AndroidManifest.xml` (the `allowBackup` line — Step 9 dropped its `tools:replace`), `gradle.properties` (Step 9 removed the `buildconfig` line), and the `build.gradle` `buildFeatures`/`debug` buildType/`-Xlint:-options` tweaks, plus the launcher-icon files (`res/drawable/ic_launcher_foreground.xml`, `res/values/ic_launcher_background.xml`, the `mipmap-*/ic_launcher*.webp` rasters, `src/main/ic_launcher-playstore.png` — these only conflict if upstream restyles its own icon; keep ours). The *new* files added by Steps 2/3/5/7/8/10 (`ColorPickerDialog`/`FontPickerDialog`/`FontUtil` + their layouts, the `*_card_background` / `*_card_filled_background` / `*_accentbar_background` / `document_icon_bg` drawables, and Step 10's `ShiroikumaUiActivity.java` / `preferences/ShiroikumaUiPreferenceFragment.java` / `xml/preferences_shiroikuma_ui.xml` / `layout/preference_shiroikuma_indent1.xml` + `_indent2.xml` + `_subheader.xml` / `drawable/shiroikuma_subheader_underline.xml`) are additions and don't conflict. Resolve by keeping our values (except the version lines, which are re-derived). After a clean rebase, `git push --force-with-lease origin custom`, then run the build pipeline. If the native core moved, `ndk-make.sh` recompiles it (slow again). If `scripts/rust-toolchain` changed, the build block's `rustup target add ... --toolchain "$(cat scripts/rust-toolchain)"` line adds the target to the new toolchain automatically.

## Iteration cadence (applying user-requested changes)

This Claude Code setup edits the working tree **directly** — there is no `.patch` round-trip and no `git apply` (see `CLAUDE.md`). When the user requests a code change:
1. Make the change directly in the repo against `origin/custom` as the base, then verify it (re-read the edits, grep a sentinel from each change).
2. **Always build after a change — don't wait to be asked.** After applying any change that affects the app (code or resources), summarise what changed and immediately build it yourself via the non-interactive run below (the `read -p`-gated pipeline can't be driven by an automated session). Java/resource-only changes use the fast path (skip `ndk-make.sh`, reuse the prebuilt `libs/arm64-v8a/*.so`); run `ndk-make.sh` only if the native core moved. (Skill/doc-only edits don't change the APK, so they don't trigger a build.) **After the build succeeds, always: (a) copy the APK to `~/tmp/` under the filename grammar (`shiroikuma-arcanechat_<versionName>_arm64-v8a.apk`) as the unconditional backup, then (b) ask whether to `adb push` it to the phone **via an `AskUserQuestion` dialog**, never as a prose question, so the user answers with one keypress — a single question (header `adb push`) whose **first** option is `Yes — push to the Mate XT` (so it is choice **1**) and whose second is `No — keep the ~/tmp backup only`. Never end a build turn without this dialog.** On Yes, push (`adb shell mkdir -p /sdcard/tmp && adb push <src> /sdcard/tmp/<apk>`, **not** `adb install`); on No, leave the `~/tmp/` copy for manual sideload (KDE Connect/Bluetooth).
3. If it's wrong, fix in place — or reset to a clean base with `git reset --hard origin/custom` **plus** `git clean -fd src/` (a `reset` leaves *untracked* files a change added, so the clean removes them) and redo. Scope the clean to `src/` so `build/` and the root `libs/` native `.so` outputs survive — otherwise `ndk-make.sh` recompiles the Rust core (slow).
4. Bump the version per feature (see Versioning).
5. On "Push." (or similar), commit and push to `origin custom`: stage only the specific feature files by **explicit path** (never `git add -A`; **never** the working-tree `gradle/wrapper/gradle-wrapper.properties` timeout bump), and update this skill in the **same commit** (commit list, customization entry, conflict-file map, traps, current version). End the commit message with the `Co-Authored-By: Claude` trailer. Once it's committed and pushed there is nothing left to sync — the working tree is the source of truth (no post-push re-sync step; that was a browser-chat patch-workflow relic).

Customization commits go on top of the existing customization commits on `custom`, so they survive rebases onto new upstream tags along with the rest.

## Maintenance

Whenever this skill is edited, deliver a refreshed zip to the user in the same turn (per the skill-export convention: `arcanechat-fork_YYYY-MM-DD_HH-MM-SS_SHORTDESC.zip`, skill directory at top level) — the user keeps the zip as a portable backup and wants it current. Do not wait to be asked.
