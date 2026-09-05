# Changelog

This file carries the **白い熊 ArcaneChat** fork's own history, newest first. Upstream ArcaneChat's
changelog is untouched and lives beside it in [`CHANGELOG-upstream.md`](CHANGELOG-upstream.md).

Every release below is built from the `custom` branch on top of the upstream release named in its
heading: `foss` flavor, `arm64-v8a`, app id `shiroikuma.arcanechat`, signed with the fork keystore
so it installs side-by-side with official ArcaneChat.

---

## 白い熊 ArcaneChat 2.59.2+001 — 2026-09-05

Built on upstream **ArcaneChat v2.59.2** (up from v2.59.1). A pure upstream sync — no new fork
features; all 53 customization commits were replayed onto the new release and reconciled.

**What upstream brings**
- **A playback-speed button on voice messages** — tap to speed up a long voice message instead of
  waiting it out.
- **Audio messages now carry their own footer inside the bubble** — timestamp and delivery status
  sit with the player rather than below it, in the right colour for incoming and outgoing.
- **Mini-apps open and reopen without the random long stall**, and each one now shows its own name
  in the recent-apps switcher instead of a blank card.
- **A crashed mini-app renderer no longer takes the app down** — WebRTC is blocked per renderer
  rather than per app, and a renderer that dies closes only its own window.
- **Scanning a channel invite while creating a new profile works** — the QR is honoured instead of
  being dropped on the way through onboarding.
- Fixed the type check on WebXDC realtime data, and refreshed the build against **compileSdk 37**
  with newer AndroidX, Glide and Gradle plugin versions.

**Fork side**
- The build counter restarts at **+001** for the new upstream release, as the versioning scheme
  requires: `2.59.2+001`, versionCode `300075401`.
- Upstream rewrote the block our per-ABI versionCode override lives in for the second release
  running, this time folding the APK file name into it. The override stays disabled — the APK still
  carries exactly the `defaultConfig` code — and the built file now names its ABI.

---

## 白い熊 ArcaneChat 2.59.1+003 — 2026-09-04

Backup automation, rebuilt around the case that actually matters: **putting this app back on a
phone that has just been wiped.**

**Backups now work out of the box**
- The **automation export switch ships ON**, and the authorization token is now **optional and off
  by default**. A pasted secret cannot survive a wipe, so a gate that only works once the phone is
  already set up was no gate for setting the phone up. You can still switch the token on if you
  would rather a caller had to present one — and it is shown only when it is actually being asked
  for, instead of sitting under an off switch inviting you to paste it somewhere it does nothing.
- A token sent by a companion that no longer needs to send one is **ignored rather than refused**,
  so a batch configured months ago does not half-fail for no visible reason.

**Backed up *with its data*, and restored**
- A second, **identified** door lets a companion app pull this app's whole state and put it back.
  It does not trust a name: it checks the caller's **exact package**, cross-checks the uid the
  kernel reports, and **pins the caller's signing certificate** — because on a wiped phone any
  package not yet installed is a name anyone could take.
- The backup travels through a **file descriptor the caller opens**, never a path, so it lands
  inside the companion's own encrypted and checksummed archive rather than beside it in plaintext.
- **Restores are accepted only through that door**, never by broadcast — an import overwrites
  everything, and no app on the phone should be able to trigger that.

**Two fixes that protect a restore you would otherwise have trusted**
- **A restore could report success over data that never reached disk.** Settings were written
  asynchronously, and the companion force-stops this app the moment it hears "done" — precisely so
  a running app cannot undo the restore at shutdown. That same stop killed the write still in
  flight, so the kill protecting the import was truncating it. Settings are now committed before
  the restore reports success.
- **A big backup could be declared dead while it was working.** A batch gives up on an app that
  goes quiet for two minutes, and exporting a whole chat profile is one long silent step — so a
  large profile could be failed mid-backup. Progress now keeps reporting through a long step, on
  both the batch export and the new door.

---

## 白い熊 ArcaneChat 2.59.1+001 — 2026-08-26

Built on upstream **ArcaneChat v2.59.1** (up from v2.58.1). A pure upstream sync — no new fork
features; all 49 customization commits were replayed onto the new release and reconciled.

**What upstream brings**
- **Call crashes fixed** — null-pointer and other exceptions that could kill the app while placing
  a call are gone; the call coordinator was substantially reworked.
- **Read messages sync across your devices even with "Read Receipts" turned off** — the core now
  sends read markers to your own devices regardless of that setting.
- Group and channel events phrased about *you* now read naturally ("You were added by …",
  "You were removed.") instead of naming you in the third person, and the join prompt works for
  channels as well as groups.
- A self-updater for installs that come from neither Google Play nor F-Droid. **It does nothing in
  this build** — it is gated on the `gplay` flavor and the fork ships `foss`, so it can never offer
  official ArcaneChat over your install.
- Fixed the second-device QR registration layout; tidied the QR, welcome, transport and progress
  screens.
- Native core 2.58.0 → **2.59.0** (hidden headers removed, old broadcast-list info messages no
  longer created, filtered reactions demoted from error to info in the device chat), plus a
  refreshed translation set.

**Build system**
- Upstream jumped to **AGP 9.3.1 and Gradle 9.5.0**. Nothing changes for the app, but the fork's
  disabled per-ABI `versionCode` override had to move to the new `androidComponents` API; the
  shipped APK carries `300075301` exactly as designed.
- Two long-standing fork patches became redundant: upstream adopted the `buildConfig` build-feature
  fix verbatim, and removed the obsolete Jetifier, RenderScript, AIDL and legacy-support
  dependencies on its own.

**Fork reconciliation**
- Galician was the only locale to conflict, and only because upstream retranslated the string next
  to ours — the fork label is kept, upstream's better translation is taken.
- The call screen's fork styling was untouched this round: upstream's call work landed in the
  coordinator, not in the activity the fork restyles.

---

## 白い熊 ArcaneChat 2.58.1+001 — 2026-08-12

Built on upstream **ArcaneChat v2.58.1** (up from v2.56.0). A pure upstream sync — no new fork
features; the whole customization stack was replayed onto the new release and reconciled.

**What upstream brings**
- **Reactions in channels**, with a reworked reactions-details overview, and channel owners can
  mute their own channel.
- **Four call fixes**: the mic stays active when the screen goes off on Android 14+, a call now
  renegotiates on a network change instead of dropping, calls support multiple profiles properly,
  and stray leave hints no longer dismiss the incoming-call prompt.
- Images can be picked without granting storage permission; video playback is truly fullscreen;
  videos are re-encoded correctly when sent from the file picker or in bulk; audio recording
  requests and respects audio focus.
- Search mode survives an incoming message instead of collapsing.
- Big unread counts are rendered human-readably instead of overflowing the badge.
- Relay handling reworked — up to 5 relays, the sending relay is a per-device setting, phased-out
  relays are shown as such, and connectivity counts as up if any one relay is connected.
- Native core 2.56 → **2.58.0**, plus reduced traffic (fewer keys on group chats, leaner read
  receipts) and a refreshed translation set including **Hebrew**.

**Fork reconciliation**
- Upstream restyled its dark-mode self-bubbles (`gray70` fill, `light_pink` timestamps). Ours are
  kept: the bubble fill stays `@color/white` — the identity `MULTIPLY` tint the bubble drawable
  depends on — and the timestamps stay `#FFFF00`.
- The unread-badge change was merged rather than overridden: upstream's human-readable count text
  now renders in our luminance-picked colour, so large counts stay black-on-yellow.
- Hebrew joined upstream, so `values-he` gets the fork label — the launcher would otherwise read
  "Delta Chat" under a Hebrew system locale.
**New versioning scheme**

- The build counter now **always restarts at `+001`** for a new upstream release. Previously the
  fork's `versionCode` was `upstreamCode + N`, which gave exactly one slot per upstream release —
  upstream's code moved by 2 across two releases while 16 builds were made — so each sync had to
  start the counter wherever the code stayed monotonic (`+4`, `+6`, `+015`) instead of at 1.
- `versionCode` is now `(upstreamCode − 27000000) × 100 + N`, giving 99 build slots per upstream
  release. The subtraction strips the constant prefix upstream's code carries, because Android's
  `versionCode` is a signed 32-bit int and a plain `× 100` would exceed it.
- The per-ABI `× 10 + abi` multiplier is disabled: it exists for multi-APK store listings, and this
  fork ships a single `arm64-v8a` APK, so it only cost a digit of headroom.
- This release is `300075101`, comfortably above the `300007652` that 2.56.0+016 carried, so it
  still installs as an upgrade.

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
