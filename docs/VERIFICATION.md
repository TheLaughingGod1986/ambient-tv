# What has been checked

Public preview version: **0.2.6**. This report describes the generic repository,
not a promise that every television and connected service behaves the same way.

| Check | Result |
| --- | --- |
| Debug app and documentation-helper APK | Build passed with JDK 17, SDK 35 and the official Gradle 8.9 wrapper |
| Unit tests | 186 passed, no failures or skipped tests |
| Android lint | Passed with 0 errors; existing advisory warnings remain |
| Clean API 35 ARM64 emulator | Three bundled demo scenes loaded; Home bounds stayed inside 1920×1080; no media history was read by the documentation helper |
| Emulator navigation regression | `TvRegression` passed in GitHub Actions at 1080p and 720p for this version |
| One physical Android 9 TV | Installed as an update over the previous version with its data kept. Seen working with the owner's own Plex and NAS accounts: Home, the paired featured tile, capture dates and ages, library pages with 16 posters, a poster loaded as a resized copy, the earliest-date and screenshot filters, favourites kept through a refresh, weather with the day's range, and the hero screensaver filling the screen with two upright photos, captions, an up-next strip and progress bar. No crashes were logged |
| Production rendering | Home and Hero screensaver Views rendered successfully; both final images visually reviewed |
| Initial setup privacy | All eight setup text fields were blank; no built-in person, address or account |
| Sign-in protection | Both NAS setup and Plex linking windows had Android's secure-window flag |
| Public files | Checked for private/output files, common credentials, absolute home paths, literal network addresses outside synthetic tests, unexpected binaries and image metadata |
| Original-installation identifiers | Separate local review found none in the publishable files; the private denylist is not included in this repository |
| Secret scan | Gitleaks reported no findings in the prepared public directory and the complete fresh Git history before publication |
| Gradle wrapper | JAR and Gradle distribution checked against Gradle's published SHA-256 values; the distribution checksum is pinned |

## What this does not establish

- One physical TV is one model on one network. Other manufacturers, Android versions
  and screen sizes have not been tried.
- Synology sign-in and Plex linking have not been performed with public/demo accounts.
- On that TV, these were not observed: a landscape photo and a three-across set on the
  hero screensaver, evening dimming, an input shortcut switching input, the Classic
  screensaver layouts, and the 720p layout.
- Page changes on that TV feel quick, but frame timing was not smooth throughout:
  a 20-press navigation sample had a 20 ms median frame and a 133 ms 90th percentile.
- A physical Home button, manufacturer idle timeout, native app playback, Cast and
  Google Home speaker control need testing on the actual user's equipment.
- The documentation renderer does not test the complete system DreamService
  lifecycle. Its images are app-view renders, not physical-TV captures.
- Home's delayed-return/forced-minute refresh change passes local checks but still
  needs a real-player progress-save and episode-to-next transition check.
- YouTube can publish stale/incomplete Watch Next data. This repository does not
  include a verified fix for upstream publishing behavior.
- Automated scans cannot guarantee that all sensitive information has been found.
  Fresh allowlisted history, generated assets and manual review are also used.

Re-run the commands in [DEVELOPMENT.md](DEVELOPMENT.md) after making changes.
GitHub Actions repeats the public-file check, build, unit tests and lint for pushes
and pull requests. Its status is separate from the local results recorded here.
