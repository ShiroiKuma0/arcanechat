# Changelog

This file carries the **白い熊 ArcaneChat** fork's own history, newest first. Upstream ArcaneChat's
changelog is untouched and lives beside it in [`CHANGELOG-upstream.md`](CHANGELOG-upstream.md).

Every release below is built from the `custom` branch on top of the upstream release named in its
heading: `foss` flavor, `arm64-v8a`, app id `shiroikuma.arcanechat`, signed with the fork keystore
so it installs side-by-side with official ArcaneChat.

---

## 白い熊 ArcaneChat 2.56.0+016 — 2026-08-11

Built on upstream **ArcaneChat v2.56.0**. The call screen, which had stayed stock, joins the rest of
the app.

**In-call controls**
- Mute, camera, speaker and switch-camera are now black discs with a 2 dp accent border and an
  accent-tinted glyph, replacing stock's gray `#6B6B6B` circles with white icons.
- They follow the **accent preset** like every other surface. `CallActivity` extends
  `AppCompatActivity` directly, so `DynamicTheme` never ran for it and `?attr/colorAccent` resolved
  to the Material3 default; the accent overlay is now applied in `onCreate` before the layout is
  inflated.
- The hang-up button is deliberately unchanged — still the red FAB with a white glyph.

**Speaker button is a toggle**
- A tap flips **speaker ⇄ earpiece** directly instead of opening the audio-device picker. Anything
  that is not the speaker counts as "not on speaker", so the first tap always lands on the speaker
  and the next one back on the earpiece.
- The full device picker moved to a **long press**, so bluetooth and wired headsets stay reachable.

**Incoming-call screen**
- The Audio / Video answer-mode chips are black with an accent border and label; the chosen one
  fills **solid accent with a black label** — a two-state fill rather than a translucent tint, which
  would read olive against black.
- The caller's avatar card gained an accent ring and a black background.
- Answer and Decline keep their **green and red** — accept and reject should not need a second look.

**Packaging**
- First release under the family-wide **three-digit build counter** (`+016`, not `+16`), so
  filenames and tags sort in build order. Earlier tags stay unpadded and are never renamed.

## 白い熊 ArcaneChat 2.56.0+14 — 2026-07-31

Built on upstream **ArcaneChat v2.56.0**. Two additions to the 保存復元 automation contract this app
answers for the companion backup app.

- **Every export category now states its own default** — the `LIST_CATEGORIES` reply carries a
  fourth positional field (`on`/`off`) per item, so the companion's picker reflects this app's
  answer instead of guessing. Every category here is `on`; account sub-options inherit their
  parent's flag, and the in-app picker is seeded from the same field.
- **`CANCEL_EXPORT`** — a running export can be stopped from outside. It unwinds at the next safe
  boundary (between categories, between accounts, between buffer chunks of a streaming account
  backup), deletes its partial file, and answers `ERROR:cancelled`. Fire-and-forget, and a silent
  no-op when nothing is running.

## 白い熊 ArcaneChat 2.56.0+13 — 2026-07-29

Built on upstream **ArcaneChat v2.56.0**.

- **Configurable delivery ticks** — one shared size (6–48 dp) for chat bubbles and the chat list,
  plus an independent colour and glyph per state: Sending, Sent, Received, Failed, and an optional
  group-only "received by all" rung. Nine vector glyphs (single/double/triple tick, clock, dot,
  tick-in-circle, up arrow, exclamation, hidden), each previewed at the real size and colour through
  the very code that draws it beside a message.
- **Pure `#000000` / `#FFFF00` across the dark theme** — toolbars, dialogs, the compose bar, date
  pills and the scroll-to-bottom button lost their near-black `#111111`, and the leftover ambers and
  grays (unread badge, incoming timestamps, quote text, connecting dot) went to pure yellow.
- **The teal dialog accent is fixed app-wide** — `Theme.AppCompat.Dialog.Alert` redefines
  `colorAccent` to Material teal, so every `AlertDialog` in the app drew a teal border. The dialog
  theme is now pinned per accent preset, declaratively, with no per-call-site code.

## 白い熊 ArcaneChat 2.56.0+10 — 2026-07-25

Built on upstream **ArcaneChat v2.56.0**.

- **Headless token-gated automation export** — a companion app can back this one up as part of a
  whole-device batch: `LIST_CATEGORIES` reports what can be exported (with each configured account
  offered separately), `EXPORT_STATE` runs the same category zip with no Activity and replies with
  the absolute path, byte count and human size. Progress is reported in real counts, never a
  percentage.
- The switch is **off** until turned on, and the shared token lives in its own device-local store
  that the export engine never reads, so it can never travel inside a backup.
- **Family backup-name grammar** — `shiroikuma-arcanechat_<yyyy-MM-dd_HH-mm-ss>.zip`, so every
  sister app's backups sort uniformly in one directory.

## 白い熊 ArcaneChat 2.56.0+9 — 2026-07-24

Built on upstream **ArcaneChat v2.56.0**.

- **Accounts in Export / Import** — a new first category embeds one core backup per configured
  account (profiles, chats, messages) in the same zip. Import restores each into a fresh account
  and skips addresses already on the device; existing accounts are never touched.
- The export engine became **streaming** (backups are too large to hold in memory), and the
  Export/Import dialogs now end in a success dialog that closes the whole chain.

## 白い熊 ArcaneChat 2.56.0+6 — 2026-07-24

**Rebased onto upstream ArcaneChat v2.56.0** (from v2.53.0). Upstream's multi-file-attach rework
removed `AttachButtonLongClickListener`, so the fork's attach buttons are click-only now. No fork
features changed.

## 白い熊 ArcaneChat 2.53.0+8 — 2026-07-24

Built on upstream **ArcaneChat v2.53.0**.

- **Export / Import of every setting** — a zip of per-category JSON (UI customizations, protected
  contacts, all app settings) written to a folder of your choice; import merges per key and never
  wipes, so exports travel across installs and versions.
- **The 白い熊 ArcaneChat UI page restyled** to the kxkb spec: accent section headers with
  text-width underlines, indented subgroups, tight rows, colour swatches on the right.
- **Chat-list toolbar accented** — title, subtitle and every menu icon; a **long press on the ⋮
  button** opens the UI page directly.

## 白い熊 ArcaneChat 2.53.0+5 — 2026-07-02

The first published fork release, built on upstream **ArcaneChat v2.53.0**. Everything the fork adds
to stock, as of that build:

**Identity & packaging**
- App id `shiroikuma.arcanechat` on the foss flavor, label 白い熊 ArcaneChat in every locale, so it
  installs side-by-side with official ArcaneChat.
- Launcher icon redrawn in the fork palette: a yellow-outlined Arcane leaf, black interior, black
  background — adaptive vector plus regenerated legacy rasters.
- `+N` build versioning adopted, and the build's Gradle/AGP/manifest warnings fixed at the source.

**Theming**
- A yellow-on-black palette across the dark theme: message text, bubbles, chat list, popup and
  overflow menus, dialogs, the compose bar and document chips.
- **Per-surface configurable colours** — message text, bubble fill and bubble border each split into
  incoming and outgoing, plus conversation background, chat-list title / preview / date, and the
  new-chat button, all picked with a live RGB/hex picker.
- **Per-category configurable fonts** — independent family, weight and size for chat messages, the
  conversation title, chat-list titles / previews / dates and the whole Settings UI; any `.ttf` or
  `.otf` file on storage can be picked and is referenced in place, sizes up to 300 sp.
- **Eight accent presets** (yellow, white, cyan, green, orange, red, magenta, blue) driving ripples,
  controls, dialogs and popup menus together.
- **Six chat-list row styles** — cards, filled cards, accent bar, dividers, inset dividers, plain.
- All of it consolidated onto one **白い熊 ArcaneChat UI** page, reachable from the chat-list
  overflow menu and from Settings, with a reset-to-defaults.

**Features**
- **Protected contacts** — a local broadcast channel lets a companion app mark contacts as
  protected; their messages post a content-free notification (no sender, no text, secret on the
  lockscreen) that still fires, carrying a marker extra so companion alerting keeps working.
- **Profile copy conveniences** — the contact profile shows the email address (tap to copy), and a
  long press on the name offers the real contact name, the nickname and the address.
- **In-app language picker** — switch the app language without touching the system locale.
