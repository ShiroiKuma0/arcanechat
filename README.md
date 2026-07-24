<div align="center">

<img src="src/main/ic_launcher-playstore.png" width="120" alt="白い熊 ArcaneChat icon" />

# 白い熊 ArcaneChat

**A fully re-themeable ArcaneChat — every colour, font, and surface yours, plus automation hooks no stock messenger has.**

A fork of [ArcaneChat for Android](https://github.com/ArcaneChat/android) with **major additions**: per-surface configurable colours and fonts, accent presets, selectable chat-list styles, full export/import of **accounts and every setting**, a companion-app "protected contacts" privacy channel, and profile copy conveniences.

Installs **side-by-side** with official ArcaneChat (app id `shiroikuma.arcanechat`).

**📥 Latest release: [`2.56.0+9`](https://github.com/ShiroiKuma0/arcanechat/releases/latest)** — [all releases & APK downloads »](https://github.com/ShiroiKuma0/arcanechat/releases)

</div>

---

## 🎨 Every surface, your colours

A dedicated **白い熊 ArcaneChat UI** settings page (reachable from the chat-list menu, Settings, or a **long-press on the toolbar's ⋮ button**) controls the app's whole look: message text, bubble fill, and bubble border — each split into **incoming and outgoing** — conversation background, chat-list title / preview / date, and the new-chat button, all picked with a live RGB/hex colour picker. Defaults reproduce the fork's signature **yellow-on-black** palette; one **Reset** restores it.

## 💾 Export / Import everything — accounts included

One tap saves **your accounts and every setting** — full per-account backups (profiles, chats, messages), UI customizations, protected contacts, and all app settings — as selectable categories into a single zip in a folder you choose; the page shows the **latest export** at a glance every time it opens. Import merges (never wipes): settings apply per key, and accounts already on the device are skipped, so exports travel safely across installs and app versions.

## 🔤 Fonts everywhere — including your own files

Independent font **family, weight, and size** per text surface: chat messages, the conversation title, chat-list titles / previews / dates, and the entire Settings UI. Beyond the system families, pick any **`.ttf`/`.otf` file** from storage and it appears in every font list — sizes go up to 300 sp for true large-print use, and chat-list rows grow to fit.

## ✨ Accent presets & chat-list styles

Eight one-tap **accent presets** (yellow, white, cyan, green, orange, red, magenta, blue) drive ripples, switches, dialogs, popup menus, the compose bar, and document chips together. The chat list itself has **six selectable row styles** — rounded cards, filled cards, accent bar, dividers, inset dividers, or plain.

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
