<div align="center">

<img src="src/main/ic_launcher-playstore.png" width="120" alt="白い熊 ArcaneChat icon" />

# 白い熊 ArcaneChat

**A fully re-themeable ArcaneChat — every colour, font, and surface yours, plus automation hooks no stock messenger has.**

A fork of [ArcaneChat for Android](https://github.com/ArcaneChat/android) with **major additions**: per-surface configurable colours and fonts, **configurable delivery ticks**, accent presets, selectable chat-list styles, full export/import of **accounts and every setting**, a **token-gated automation export** for one-command backups, a companion-app "protected contacts" privacy channel, and profile copy conveniences.

Installs **side-by-side** with official ArcaneChat (app id `shiroikuma.arcanechat`).

**📥 Latest release: [`2.58.1+015`](https://github.com/ShiroiKuma0/arcanechat/releases/latest)** — [all releases & APK downloads »](https://github.com/ShiroiKuma0/arcanechat/releases)

</div>

---

## 🎨 Every surface, your colours

A dedicated **白い熊 ArcaneChat UI** settings page (reachable from the chat-list menu, Settings, or a **long-press on the toolbar's ⋮ button**) controls the app's whole look: message text, bubble fill, and bubble border — each split into **incoming and outgoing** — conversation background, chat-list title / preview / date, and the new-chat button, all picked with a live RGB/hex colour picker. Defaults reproduce the fork's signature **yellow-on-black** palette; one **Reset** restores it.

The palette is disciplined: black is **`#000000`** on every surface — toolbars, dialogs, the compose bar, date pills, the scroll-to-bottom button — never a near-black that reads greenish next to the yellow, and yellow is **`#FFFF00`**, never amber. Every dialog in the app carries the accent border, including ones deep inside stock ArcaneChat.

## ✅ Delivery ticks you can actually read

The little status marks beside your sent messages are fully yours: **one size** (6–48 dp) for the chat bubbles and the chat list together, and an independent **colour and glyph per state** — Sending, Sent, Received, and Failed — chosen from nine vector glyphs (single / double / triple tick, clock, dot, tick in a circle, up arrow, exclamation, or hidden). Both the settings page and the glyph picker **preview each glyph at your real size and colour**, rendered through the very same code that draws it beside a message, so what you pick is what you get.

The default ladder is built on **shape, not stroke-counting**: a blue clock while the message is still on your phone, a yellow dot once your server has it, a double tick once a read receipt comes back. In group chats an optional extra rung tells **"read by some" from "read by everyone"**.

Honest about what e-mail can tell you: there is no "delivered to their device but unread" state, because SMTP has no delivery receipt — the rungs shown are the ones the protocol actually knows.

## 💾 Export / Import everything — accounts included

One tap saves **your accounts and every setting** — full per-account backups (profiles, chats, messages), UI customizations, protected contacts, and all app settings — as selectable categories into a single zip in a folder you choose; the page shows the **latest export** at a glance every time it opens. Import merges (never wipes): settings apply per key, and accounts already on the device are skipped, so exports travel safely across installs and app versions.

## 🤖 Backups on command — no hands, no UI

An **automation export** switch (off until you turn it on) lets a companion app back this one up as part of a whole-device batch: a token-gated intent runs the very same category export **headlessly** — no window, no taps — writes **one zip** to whatever directory the caller names, and answers with the exact path, byte count, and human size. While it runs it reports **real counts** (`区分 2/4 — Accounts`, `アカウント 1/2 — you@example.org`), never a meaningless percentage. The companion can also ask what this app can export and get back the category list — with **each account offered separately**, so a batch can grab one profile instead of all of them, and with each item saying **whether it should start ticked**, so the picker reflects this app's own answer rather than a guess. A running export can be **stopped from outside**: it unwinds at the next safe boundary, deletes its half-written file, and reports that it was cancelled, so a backup you stopped never quietly finishes and lands anyway. The shared secret lives in its own device-local store, is compared in constant time, and can never appear inside a backup.

## 🔤 Fonts everywhere — including your own files

Independent font **family, weight, and size** per text surface: chat messages, the conversation title, chat-list titles / previews / dates, and the entire Settings UI. Beyond the system families, pick any **`.ttf`/`.otf` file** from storage and it appears in every font list — sizes go up to 300 sp for true large-print use, and chat-list rows grow to fit.

## ✨ Accent presets & chat-list styles

Eight one-tap **accent presets** (yellow, white, cyan, green, orange, red, magenta, blue) drive ripples, switches, dialogs, popup menus, the compose bar, and document chips together. The chat list itself has **six selectable row styles** — rounded cards, filled cards, accent bar, dividers, inset dividers, or plain.

## 📞 In-call controls that match the app

The call screen wears the same palette as everything else: mute, camera, speaker, and switch-camera are **black discs with an accent border and an accent glyph** instead of stock's gray-on-white circles — and they follow the accent preset like every other surface. Hang up stays unmistakably **red**. The speaker button is a plain **speaker ⇄ earpiece toggle** now, one tap per flip, with the full audio-device picker still one **long press** away for a bluetooth or wired headset.

An incoming call matches: the caller's avatar sits in an **accent ring**, and the Audio / Video answer-mode chips are black with an accent border, the chosen one filling **solid accent with a black label** — no washed-out half-transparent state. Answer and Decline keep their **green and red**, because accept and reject should never need a second look.

## 🛡️ Protected contacts (companion automation)

A local broadcast channel (`SET_PROTECTED_CONTACTS` / `GET_PROTECTED_CONTACTS`) lets a companion app mark contacts as **protected**: their incoming messages post a **content-free notification** — no sender, no text, secret on the lockscreen — while still firing, with a marker extra so the companion's own alerting (e.g. an edge-blink) keeps working. Non-protected contacts are untouched.

## 📋 Profile copy conveniences

The contact profile finally **shows the email address** (tap to copy), and long-pressing the name offers the **real contact name**, the nickname, and the address — each one tap to the clipboard, even when a local nickname hides the real name.

## 🌐 In-app language picker

Switch the app language from Settings → Appearance without touching the system locale (AndroidX per-app locales).

---

## Built on ArcaneChat

A fork of [ArcaneChat for Android](https://github.com/ArcaneChat/android) (app id `shiroikuma.arcanechat`, so it coexists with the official build), which is itself a friendly fork of [Delta Chat](https://delta.chat/) — decentralized, end-to-end-encrypted messaging over e-mail, no phone number, no central servers. The code remains under **GPL-3.0**.

## Building

```bash
git clone --recursive git@github.com:ShiroiKuma0/arcanechat.git
cd arcanechat && git checkout custom
# Rust core (arm64), then the foss flavor:
rustup target add aarch64-linux-android --toolchain "$(cat scripts/rust-toolchain)"
scripts/ndk-make.sh arm64-v8a
./gradlew assembleFossRelease -PABI_FILTER=arm64-v8a
```

Requires JDK 17+, the Android SDK with NDK `27.0.12077973`, and rustup. Release signing reads the `DC_RELEASE_*` properties from `~/.gradle/gradle.properties`.
