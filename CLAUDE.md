# shiroikuma ArcaneChat — Claude Code orientation

This is the **shiroikuma personal fork** of [ArcaneChat for Android](https://github.com/ArcaneChat/android) (a Delta Chat-based messenger; native Rust core at `jni/deltachat-core-rust`; internal Signal namespace `org.thoughtcrime.securesms`). Branding: `applicationId shiroikuma.arcanechat` on the **foss** flavor only, label `白い熊 ArcaneChat`. It installs side-by-side with the official F-Droid build.

## Branch model

- `main` — mirrors upstream, fast-forward only.
- `custom` — carries the fork's changes, rebased onto each new upstream release tag.
- Develop on `custom`. The remote is `origin` (`git@github.com:ShiroiKuma0/arcanechat.git`); upstream is read-only at `ArcaneChat/android`.

## Read this skill before any project work

`.claude/skills/arcanechat-fork/` — full project knowledge: identity, build pipeline, build traps, every customization step (1–9), versioning scheme (committed `+N`, base `versionCode + N` because the `×10000` of the other forks would overflow given upstream's already-8-digit code and the per-ABI `×10`), conflict-file map, upstream-sync procedure. The skill is authoritative for anything project-specific.

## Workflow in Claude Code

Edits go straight into the working tree — there's no `/mnt/user-data/outputs/` patch round-trip. Cadence:

1. Claude reads `.claude/skills/arcanechat-fork/SKILL.md` (and any other relevant skill) to orient.
2. Claude makes edits directly in the repo, plus a summary of what changed.
3. The user reviews, builds, and tests.
4. On "Push.", Claude stages the *specific* feature files (use explicit paths, never a blanket `git add -A`) and pushes to `origin custom`. **Never stage `gradle/wrapper/gradle-wrapper.properties`** — it carries a working-tree-only `networkTimeout=600000` bump.
5. Update `.claude/skills/arcanechat-fork/SKILL.md` in the same change (commit list, new customization entry, conflict-file map, traps) so the skill stays current.

The build pipeline, versioning rules, conflict map, and all the conventions are in the skill — follow them rather than re-deriving.

## Required local state (not in the repo)

- Keystore at `~/.android-keystores/arcanechat-custom.jks` — generated once; passphrase chosen by the user and never committed.
- `~/.gradle/gradle.properties` carries the four `DC_RELEASE_*` signing props that point at the keystore. See the skill's one-time-setup block.
- Working tree at `~/git/shiroikuma-arcanechat/`.
- `gradle/wrapper/gradle-wrapper.properties networkTimeout=600000` is set in the working tree by the skill's pipeline every build; it is deliberately never staged.
- Android SDK at `$ANDROID_HOME` with NDK `27.0.12077973`, JDK 21, rustup with the android target on the toolchain pinned by `scripts/rust-toolchain`.

## Open threads

Carried over from the chat that bootstrapped this config — pick any when convenient:

- **Conversation-title toolbar clipping at very large fonts** — the chat title can clip when a large Step-3 font is set; let it shrink/ellipsize or wrap.
- **Pull a new upstream release** — rebase `custom` onto a newer ArcaneChat tag; the procedure plus the version-reset rule (`versionName <new>+1`, base `versionCode <new>+1`) are in the skill's Versioning section.
- **Style surfaces not yet touched** — profile/settings screens, attachment picker, search, etc.

## Note on this file vs the skill

`CLAUDE.md` is the top-level orientation; the skill is the working manual. When in doubt about a project specific (build trap, naming, conflict file, version math), the skill is authoritative. Update the skill when project facts change; update `CLAUDE.md` only when the orientation itself changes (e.g. new branch model, new top-level pointer).
