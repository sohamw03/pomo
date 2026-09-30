# Pomo (Android)

Pomodoro timer. The `android` branch is the Android port of the web app on `main`.

## Iterate / build flow

There is no local Android SDK. A GitHub Codespace (`pomo-android`) is the
build server: JDK 17 + Android SDK, no emulator, no git credentials there.
So local edits are shipped up, built remotely, and the APK comes back down.

```powershell
# Build debug, download to dist/pomo-debug.apk
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Build-Apk.ps1

# Release variant
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Build-Apk.ps1 `
  -Task ":app:assembleRelease" -Label "pomo-release"
```

What the script does (`scripts/Build-Apk.ps1`):

1. `git status --porcelain -z --untracked-files=all` → every changed/new file
   is base64-shipped to `/workspaces/pomo` over `gh codespace ssh`.
   Deleted files are removed remotely. Committed-and-clean files are assumed
   already in sync (push from a machine with credentials when needed).
2. Runs `./gradlew <Task>` on the Codespace.
3. Release builds get zipaligned + signed with the debug keystore.
4. Downloads the APK to `dist/<Label>.apk`.

## Send to phone

Phone (`soham-droid`) and this machine are on the same Tailnet. Push builds
over with Taildrop, then accept in the Tailscale app → Files on the phone:

```powershell
tailscale file cp dist/pomo-debug.apk soham-droid:
```

So the full test loop is: edit → `Build-Apk.ps1` → `tailscale file cp` →
install → look.

## Gotchas

- **Large files break the ship step.** Files go up as base64 `printf` chunks;
  keep chunks small (`$CHUNK` in the script, currently 12 KB — 48 KB exceeded
  Windows' command-line limit and failed with
  `Program 'gh.exe' failed to run: The filename or extension is too long`).
  Keep big audition assets out of the repo: `sfx-preview/` is gitignored,
  `dist/` is gitignored.
- **Orphan files on the Codespace.** Untracked files deleted locally never
  appear in `git status`, so the script won't remove them remotely. Clean up
  explicitly, otherwise stale resources (fonts, sounds) linger in the build:
  ```powershell
  gh codespace ssh -c <codespace-name> -- "rm -rf /workspaces/pomo/<path> && echo cleaned"
  ```
- **Debug builds jank.** Frame pacing in `assembleDebug` (no R8) is not
  representative. Compare against a signed `assembleRelease` before chasing
  animation performance.
- Deprecation warnings (e.g. `Icons.Filled.RotateLeft` → AutoMirrored) show
  up in the build tail; fix when touching those lines.
