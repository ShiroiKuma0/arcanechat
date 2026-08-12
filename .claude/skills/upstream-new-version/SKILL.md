---
name: upstream-new-version
description: One-command upstream sync + rebuild for the user's ShiroiKuma0/arcanechat fork of ArcaneChat/android. Checks upstream for a newer release tag; if one exists, presents an OK-gated tabular summary of the UI/functional improvements since our last rebase, then fast-forwards `main`, rebases the `custom` branch onto the new tag (reconciling the banked customizations), re-syncs the native core submodule, and builds/signs/deploys via the arcanechat-fork skill. Holds every push until 白い熊 has tested the build on-device and explicitly says "Push". Use when 白い熊 runs /upstream-new-version, or asks to pull/sync/bump to a new ArcaneChat version, rebase custom onto upstream, or rebase-and-rebuild the fork.
---

# upstream-new-version — sync to a newer upstream ArcaneChat and rebuild

This is an **orchestration layer on top of the `arcanechat-fork` skill**. It does not redefine any build/identity/version fact — it sequences the upstream-sync + rebase work, then hands off to `arcanechat-fork` for the build/sign/deploy. **`arcanechat-fork/SKILL.md` remains the single source of truth** for every build trap, conflict-file map, version-math rule, and pipeline invariant.

Apply the `shell-block-formatting` conventions the fork skill uses: the `r()` stderr-reddening helper in every block, cyan `>>>` echoes, `read -p` gates only for the expensive native build.

```bash
r() { "$@" 2> >(sed $'s/.*/\033[1;31m&\033[0m/' >&2); }
```

## Step 0 — read `arcanechat-fork` first (mandatory)

Before doing anything, read `.claude/skills/arcanechat-fork/SKILL.md` (or invoke the `arcanechat-fork` skill). Everything below assumes its banked facts:

- Remotes: **`origin`** = `git@github.com:ShiroiKuma0/arcanechat.git` (SSH, **push here**); **`upstream`** = `https://github.com/ArcaneChat/android.git` (**fetch only** — never push, even though a push URL is configured).
- Branches: **`main`** mirrors upstream (fast-forward only, never carries our changes); **`custom`** carries the customization commits (26 as of `2.53.0+5`) and is **rebased onto each upstream release tag** (`vX.Y.Z`).
- Working tree: `~/git/shiroikuma-arcanechat`. Native core submodule **`jni/deltachat-core-rust`** → `https://github.com/ArcaneChat/core`, pinned by the superproject gitlink; re-synced with `git submodule update --init --recursive` after any checkout/rebase.
- Build flavor **foss**, ABI **arm64-v8a** only; output is signed with `~/.android-keystores/arcanechat-custom.jks`, delivered via **`/after-build`**.

## Non-negotiables this skill must honor

The CLAUDE.md / arcanechat-fork invariants this flow most easily violates — do not break them:

- **`export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`** before any `gradlew` (spotless needs JDK 17+; the Jami fork may flip the machine default to Java 11). Git steps don't need it; the build does.
- **Never stage `gradle/wrapper/gradle-wrapper.properties`** — it carries the working-tree-only `networkTimeout=600000` bump, re-applied every build, never committed.
- **Re-derive the version on every rebase — never blindly keep ours.** The `versionCode`/`versionName` lines in `build.gradle` `defaultConfig` conflict on every sync. Reset to `versionName "<new-upstream>+1"`, base `versionCode <new-upstream-code> + 1`, reading the new upstream base numbers from the freshly-rebased `build.gradle`. The `+N` (not `×10000`) scheme is mandatory — upstream's code is already 8 digits and the per-ABI override does `×10+abi`, so `×10000` would overflow. **Monotonic guard:** if the reset code would be ≤ the last shipped base code, start the tail at the first `N` that exceeds it (real case: `2.53.0` started at `+4` because `2.49.0+7` had shipped as `30000749`); resolve intermediate bump commits to the plain reset value and land the final commit on the guarded value. See the fork skill's **Versioning** section.
- **The `jni/deltachat-core-rust` gitlink rides the rebase** — it advances automatically with `custom` (it sits on top of the upstream tag). Never hand-edit it; just `git submodule update --init --recursive` after the rebase. A moved core means **`scripts/ndk-make.sh` recompiles the Rust core** (slow — the first native build again).
- **Hold every push until 白い熊 has tested on-device and explicitly says "Push".** The rebase rewrites `custom`, and a build can regress on a new upstream — even a green build is not pushed until on-device testing confirms it. See **Step 7**.
- **No Claude attribution** in any commit message (see the footer).

## Step 1 — check whether there is a new upstream version

Fetch upstream tags and compare. **Report and stop without building if there is nothing new.**

```bash
r() { "$@" 2> >(sed $'s/.*/\033[1;31m&\033[0m/' >&2); }
cd ~/git/shiroikuma-arcanechat

r git fetch upstream --tags
r git fetch origin

base=$(git merge-base custom upstream/main)                      # our current rebase base (a vX.Y.Z tag commit)
base_tag=$(git describe --tags --exact-match "$base" 2>/dev/null || git describe --tags "$base")
new_tag=$(git tag -l 'v*' --sort=-v:refname | head -1)           # newest upstream release tag
echo -e "\033[1;36m>>> our base: ${base_tag}   newest upstream tag: ${new_tag}\033[0m"

if git merge-base --is-ancestor "$new_tag" custom; then
  echo -e "\033[1;36m>>> No new upstream version — custom is already on ${new_tag}. Nothing to do.\033[0m"
else
  old_vn=$(git show "${base}:build.gradle"    | grep -oP 'versionName "\K[^"]+' | head -1)
  new_vn=$(git show "${new_tag}:build.gradle" | grep -oP 'versionName "\K[^"]+' | head -1)
  old_vc=$(git show "${base}:build.gradle"    | grep -oP 'versionCode \K[0-9]+' | head -1)
  new_vc=$(git show "${new_tag}:build.gradle" | grep -oP 'versionCode \K[0-9]+' | head -1)
  ahead=$(git rev-list --count "${base}..${new_tag}")
  echo -e "\033[1;33m>>> New upstream: versionName ${old_vn} -> ${new_vn}, base versionCode ${old_vc} -> ${new_vc}, ${ahead} upstream commit(s) since our base.\033[0m"
fi
```

- "New version" = there is a `vX.Y.Z` tag that is **not** already an ancestor of `custom`. If nothing is new, **stop** — do not ff, rebase, or build; tell 白い熊 the current version and that they're up to date.
- Pick the target tag deliberately: default to the newest `vX.Y.Z`. (You may target `upstream/main`'s tip if 白い熊 wants unreleased work, but a tagged release is the norm.) Everything below uses `${new_tag}` and `${base}`.
- Note that the **core submodule almost always moves** on a sync ("Update to core X.Y.Z" in the changelog), so expect the **long native rebuild** in Step 6 — warn 白い熊.

## Step 2 — OK-gated tabular summary of upstream UI/functional improvements (right before rebase)

**Always**, before touching `main` or `custom`, present 白い熊 a clean **Markdown table** of what the new upstream release brings — focused on **UI and functional improvements**, not internal churn — and then **STOP for an explicit OK**. Do **not** fast-forward `main` or rebase `custom` until 白い熊 says OK / proceed / continue / yes. This step is mandatory on every sync; the rebase is never started silently.

Draw the summary from these sources over the range `${base}..${new_tag}` (most user-facing first):

```bash
r() { "$@" 2> >(sed $'s/.*/\033[1;31m&\033[0m/' >&2); }
cd ~/git/shiroikuma-arcanechat

# 1) PRIMARY: upstream's own per-release changelog of user-facing changes (the new bullet points).
r git diff "${base}..${new_tag}" -- CHANGELOG-upstream.md

# 2) Commit-level detail, to catch anything not in the changelog and to judge conflict risk.
r git log --format='%h | %an | %s' "${base}..${new_tag}"
r git log --stat --format='%n### %h  %s%n%b' "${base}..${new_tag}"   # bodies + files touched

# 3) OPTIONAL deeper dive — the native core's own changelog (submodule; only if the core bump matters):
#    inspect it inside the checked-out submodule, e.g.
#    (cd jni/deltachat-core-rust && git log --oneline <old-core>..<new-core> -- CHANGELOG.md)
```

Present a table, **one row per user-visible or functional change** (fold the recurring noise — `Updated translations`, dependency/CI/lint/format bumps — into a single "housekeeping" row), with these columns:

- **Change** — a short title (from the changelog bullet or commit).
- **Type** — one of **UI** / **UX** / **Functional** / **Fix** / **Perf** / **Security** / **i18n** / **Housekeeping**.
- **What it does** — a plain-language sentence drawn from the changelog bullet or the commit *body*, not just a subject line.
- **Relevance to our fork** — High / Medium / Low **and why**: does it touch a **customization-layer file** (theme/colours/fonts, the `白い熊 ArcaneChat UI` page, `ConversationItem`/`ConversationActivity`/`ConversationListItem`/`NotificationCenter`/`ProfileFragment`/`ProfileAdapter`, the `automation/` protected-contacts code, `build.gradle`/`strings.xml`) so it may **conflict on rebase**, or is it a **genuinely useful UX/functional win** for 白い熊? Flag both.

Lead the table with a one-line headline (e.g. "`v2.49.0 → v2.50.0`: 3 UI improvements, 1 notable functional change (system call integration out of experimental), ~6 fixes; nothing touches our theme/UI layers, so the rebase should be clean"), then **wait**. Only on 白い熊's explicit OK proceed to Step 3.

## Step 3 — fast-forward main, rebase custom onto the new tag (do NOT push yet)

```bash
r() { "$@" 2> >(sed $'s/.*/\033[1;31m&\033[0m/' >&2); }
cd ~/git/shiroikuma-arcanechat

r git status --short                       # working tree must be clean (the wrapper timeout bump is fine/expected)

r git checkout main
r git merge --ff-only upstream/main        # main only ever fast-forwards

r git checkout custom
# HARD GATE - the checkout above can fail silently in a piped block (see Banked failures);
# a rebase started from main replays post-tag upstream commits instead of our stack.
[ "$(git rev-parse --abbrev-ref HEAD)" = "custom" ] || { echo -e '\033[1;31m>>> NOT on custom - fix the checkout before rebasing\033[0m'; false; }

r git rebase "${new_tag}"                   # replay the customization stack onto the new release tag

r git submodule update --init --recursive  # the native core follows the advanced gitlink
```

When Claude runs this flow, the checkout + rebase + conflict-resolution sequence must run **unsandboxed** — our stack commits write into `.claude/skills/`, which the sandbox denies (see Banked failures).

- **Do not `git push` here.** Both `origin main` (ff) and the force-push of `custom` are deferred to Step 7.
- If the rebase stops on conflicts, go to **Step 4**, resolve, `git rebase --continue`. If it goes sideways, `git rebase --abort` (local branches are unchanged except `main`, which is safely ff'd) and re-plan.

## Step 4 — reconcile conflicts (the fork skill's conflict-file map is authoritative)

Do **not** re-derive the conflict list here — the `arcanechat-fork` skill's **"Syncing to a new upstream release"** section is the authoritative, maintained map. In brief, the predictable conflicts and how to resolve them:

- **`build.gradle`** — the `applicationId "shiroikuma.arcanechat"` line (keep ours) and the **`versionCode`/`versionName` lines (re-derive: `<new>+1` / base `<new-code>+1`** from upstream's new numbers — never blindly keep ours), plus the fork's `buildFeatures`/`debug`/`-Xlint:-options` tweaks.
- **`src/main/res/values/strings.xml` + every `values-*/strings.xml`** — `app_name` = `白い熊 ArcaneChat` (base + all ~45 locales), plus the fork-local `protected_notification_channel_name` in base.
- **`google-services.json`** — the `shiroikuma.arcanechat` client block (rare).
- **Customization-layer files** — resolve to keep ours, re-pointing the edit if upstream moved the site: the theme/colours/fonts/accent/chat-list files, the `白い熊 ArcaneChat UI` page, `ConversationItem/Activity/ListItem/ListFragment/TitleView`, `notifications/NotificationCenter.java` (protected-contacts guard), `ProfileFragment.java`/`ProfileAdapter.java` (name/email copy), `AndroidManifest.xml` (`MANAGE_EXTERNAL_STORAGE`, `.ShiroikumaUiActivity`, `.automation.ProtectedContactsReceiver`), `util/Prefs.java`, `util/DynamicTheme.java`, the two `preferences_appearance.xml` variants, launcher-icon files, etc.
- **New files** (Steps 2/3/5/7/10 + `automation/ProtectedContacts*.java`) are additions and don't conflict.

**Reconciliation rule of thumb:** install-identity + label are stable across versions; the version lines conflict every time and are *re-derived*, not kept; theme/UI/protected-contacts edits are mostly additive and only conflict if upstream rewrote the same file — port the intent to the new file shape.

## Step 5 — verify the rebase result before building

```bash
r() { "$@" 2> >(sed $'s/.*/\033[1;31m&\033[0m/' >&2); }
cd ~/git/shiroikuma-arcanechat

r git log "${new_tag}..custom" --oneline    # the replayed customization stack; subjects should look familiar (~24)
r git status --short                         # clean except the (never-staged) wrapper timeout bump
r grep -c 'shiroikuma_yellow' src/main/res/values/colors.xml   # spot-check a customization anchor survived
```

Confirm the invariants below survived the rebase before building:

| What | Expected value | Where |
| --- | --- | --- |
| Installed app ID | `shiroikuma.arcanechat` | `build.gradle` foss flavor |
| Code namespace (unchanged) | `org.thoughtcrime.securesms` | `build.gradle` `namespace` |
| App label | `白い熊 ArcaneChat` | `app_name` in `values/` + all `values-*/strings.xml` |
| Version tail | `<new>+1` / base `<new-code>+1` | `build.gradle` `defaultConfig` |
| Protected-contacts receiver | `.automation.ProtectedContactsReceiver` (`exported="true"`) | `src/main/AndroidManifest.xml` |
| Signing props present | four `DC_RELEASE_*` | `~/.gradle/gradle.properties` (local state, not in repo) |

## Step 6 — build, sign, deploy (apply the `arcanechat-fork` skill)

Hand off to **arcanechat-fork** for the build. Use its **build + deploy pipeline verbatim** (the interactive `read -p`-gated block for a human-run sync, or the non-interactive block when Claude runs it) — do not hand-roll one. That pipeline already pins `JAVA_HOME`/SDK/NDK, applies the wrapper `networkTimeout` bump (unstaged), builds `assembleFossRelease -PABI_FILTER=arm64-v8a` with the warning filters, copies the APK to `~/tmp/` under the filename grammar, and delivers via **`/after-build`** (adb-push to the phone, else scp to skhw — no prompt).

New-upstream specifics:
- Because the **core submodule moved**, `scripts/ndk-make.sh arm64-v8a` must run first and the Rust core recompiles — the build is **long** (like the first build). Expected, not a hang.
- If `scripts/rust-toolchain` changed, the pipeline's `rustup target add aarch64-linux-android --toolchain "$(cat scripts/rust-toolchain)"` line adds the target to the new toolchain automatically.
- A failed Gradle run signs **no** APK — paste the "What went wrong / Caused by" lines, fix, rebuild. Watch the build traps in the fork skill (esp. the signing-props and spotless/Java-11 ones).

## Step 7 — push ONLY after 白い熊 tests and says "Push"

Stop after Step 6 with a built, signed, sideloaded `<new>+1` APK and **wait**. When 白い熊 has tested on-device and **explicitly says "Push"**, then — and only then — in that turn:

```bash
r() { "$@" 2> >(sed $'s/.*/\033[1;31m&\033[0m/' >&2); }
cd ~/git/shiroikuma-arcanechat

# Push the refs directly - NO branch checkouts here: the rebrand.sh working-tree noise
# (see Banked failures) makes `git checkout main` abort, and pushing needs no checkout.
r git push origin main                          # fast-forward, safe
r git push --force-with-lease origin custom     # rebased history → force-with-lease
```

- Update the `arcanechat-fork` skill in the **same push** if the sync changed any project fact (version, conflict-file map, a new trap). Stage the skill explicitly alongside; **never** `git add -A`, **never** the wrapper file.
- The customization commits ride on `custom`, so they survive this and future rebases.

## Banked failures from real runs

From the first real sync (`v2.49.0 → v2.53.0`, 2026-07-02) unless noted:

- **Don't check `main` out at all — fast-forward it in place (v2.58.1 sync, 2026-08-12).** The banked `git checkout main` → `git checkout custom` dance is what created the silent-abort failure below, and it is unnecessary: `git fetch . upstream/main:main` updates `refs/heads/main` from `upstream/main` with **fast-forward-only semantics** (a non-forced ref update is rejected unless it's an ff), never touches the working tree, and leaves HEAD on `custom` the whole time. This sidesteps the skill-file collision entirely. Step 3's `git checkout main; git merge --ff-only` block is kept only as the manual/human form.
- **A dirty working tree blocks the rebase outright (v2.58.1 sync).** `git rebase` refuses with `error: cannot rebase: You have unstaged changes` — and on this repo the tree is **always** dirty before a sync: the never-staged `gradle/wrapper/gradle-wrapper.properties` timeout bump plus the ~79 `rebrand.sh` files from the last native build. Clear both first (`git checkout -- src/main/assets/help src/main/res gradle/wrapper/gradle-wrapper.properties`); the build pipeline re-applies the timeout bump and the next `ndk-make.sh` re-dirties the rebrand files, so nothing is lost.
- **The build steps need `dangerouslyDisableSandbox` too (v2.58.1 sync).** Both halves of the build fail under the sandbox, with errors that look nothing like a sandbox problem: `scripts/ndk-make.sh` dies at `ln: シンボリックリンク '/tmp/android-ndk-root' の作成に失敗しました: 読み込み専用ファイルシステムです` (it symlinks the NDK into `/tmp`, and only `$TMPDIR` is writable), and `./gradlew` dies with `java.io.FileNotFoundException: …/gradle-8.13-bin.zip.lck (読み込み専用ファイルシステムです)` when the wrapper distribution is not yet cached (it locks under `~/.gradle/wrapper/dists`). Run the native build and Gradle **unsandboxed**, like `adb`/`scp`/`git status`.
- **`git checkout custom` failed silently → the rebase ran on `main`.** An **untracked** `.claude/skills/arcanechat-fork/SKILL.md` had re-materialized in the working tree after the `main` checkout removed the tracked copy (the harness re-writes skill files it has loaded), colliding with `custom`'s tracked version — checkout printed `Aborting` (swallowed by a `| tail -1` pipe) and HEAD stayed on `main`. The subsequent `git rebase v2.53.0` then replayed **post-tag upstream commits** (`main` was ahead of the tag) instead of our stack — recognizable by a wrong commit count and foreign author names in the picks. Fixes now baked in: the Step 3 HEAD hard-gate, and if the collision occurs, compare the stray file against `custom`'s copy (`git show custom:<path> | diff - <path>`) and remove it (needs unsandboxed `rm` — the path is sandbox-denied).
- **The sandbox breaks the rebase mid-replay — and can silently drop a commit.** Writes to `.claude/skills/` are denied ("読み込み専用ファイルシステムです"); a sandboxed `git rebase --continue` failed to create the directory while applying the `Add agent config` commit, and after an unsandboxed retry the sequencer had **skipped that commit entirely** (HEAD showed the previous commit; the pick was marked done). If any sandbox error interrupts a replay, don't patch around it — **abort and redo the whole rebase unsandboxed in one pass**, then verify the replayed count (`git log <tag>..custom --oneline | wc -l`).
- **Monotonic version guard** — see the Non-negotiables entry; `2.53.0` had to start at `+4`/`30000750` because upstream's base (`30000746`) trailed our shipped `30000749`, and `2.58.1` at `+015`/`30000766` because its base (`30000751`) trailed our shipped `30000765`. It has bitten on **every** sync so far; compute it, never assume `+1`.
- **A new upstream locale silently keeps upstream's `app_name` (v2.58.1 sync).** v2.58.0 added Hebrew; `values-he/strings.xml` is a pure **addition**, so it never conflicts and the rebase says nothing — but it shipped `app_name` = `Delta Chat`, which would show under a Hebrew system locale. After every sync: `grep -L '白い熊 ArcaneChat' src/main/res/values-*/strings.xml | xargs -r grep -l '<string name="app_name">'` and brand whatever it lists.
- **`rebrand.sh` working-tree noise (v2.53.0+).** The first post-sync native build left ~80 files modified — `ndk-make.sh` now calls `scripts/rebrand.sh`, rewriting "Delta Chat"→"ArcaneChat" in `assets/help/*/help.html` + all locale `strings.xml` **in place**. Working-tree-only, never staged/committed (fork-skill build trap #8); don't mistake it for rebase fallout when reviewing `git status` before the push.

## One-line summary of the flow

`fetch upstream --tags` → new tag? (else stop) → **OK-gated Markdown table of UI/functional improvements since our base + WAIT** → ff `main` → rebase `custom` onto `${new_tag}` (reconcile per the fork skill's conflict map; **re-derive the version tail to `<new>+1`**) → `submodule update` (core moved → long native rebuild) → verify invariants → **apply arcanechat-fork to build/sign/deploy via /after-build** → 白い熊 tests → on "Push": push `main` (ff), force-with-lease `custom`.

---

**Commit convention — no Claude attribution.** Never add a `Co-Authored-By: Claude …` / "Generated with Claude" trailer to commit messages or PR bodies; end the message at the last line of the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
