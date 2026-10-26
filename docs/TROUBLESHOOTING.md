# Something is not working

Start with the demo. If demo photos render, the display can work independently of
a NAS account. Keep a short note of the error message; do not export account files.

| What you see | What to try |
| --- | --- |
| Build asks for an SDK | Open SDK Manager in Android Studio, install Platform 35 and Build Tools, then sync again. Use JDK 17. |
| ADB says unauthorized | Approve this computer on the TV. Check the serial with `adb devices`. |
| APK says conflicting signature | Use the same signing key as the installed Ambient TV build. Uninstalling loses this app's data, so do not do it casually. |
| No named person found | Match the unique name under Personal Space → People exactly. An album/folder name does not work. |
| NAS returns a session/MFA error | Re-enter the password and current verification code. Additional approval flows are not implemented. Never copy another app's session. |
| NAS certificate fails | Fix the trusted certificate/hostname or use a correctly configured local connection. Do not bypass certificate verification. |
| Old photos remain after a connection failure | This is the offline fallback. Check the connection, then choose Refresh photos. |
| No newer or older photo appears | Check person membership in Synology Photos and allow a successful refresh. The scan is bounded to 10,000 metadata entries; larger collections need an extended pagination design. |
| Weather is missing | Enable it, enter valid coordinates and save. It is off by default. Stale readings are hidden. |
| Home shows different Plex progress | Check which profile authorized Ambient TV. The native player's current profile may be different. |
| YouTube cards are old | Check YouTube History inside YouTube. Ambient TV reads the feed YouTube publishes; a fresh read cannot invent missing records. |
| Next Plex episode is absent | Plex must supply an eligible unstarted episode. Finish/resume normally in Plex; do not fabricate progress or next episodes. |
| A hidden card stays missing | Open Home Settings → Restore hidden videos. Hiding does not change your native watch history. |
| Screensaver does not start | Check Android's selected screensaver and idle settings. Active audio causes the app to decline the dream. Manufacturer ambient modes may take precedence. |

## The original Home screen still appears

First try the TV's **default Home app** setting. Some firmware handles the physical
remote button differently from a simulated Home intent. Test the actual remote.

The included accessibility bridge targets the legacy `com.google.android.tvlauncher`
package only. It does not handle every Google TV or manufacturer launcher. If you
choose to enable it, read the Android permission dialog and test it while the TV
is free. Briefly showing the original launcher before redirecting can still happen.

Do not disable or uninstall the stock launcher as a generic fix. Model-specific
launcher changes require separate research and a tested recovery method. Keep a
way to open Android Settings and return to the previous Home app.

## Keep Cast and Google Home working

Ambient TV does not replace Cast, Assistant, Google Play services or the TV input
services. Leave them installed and enabled. Test casting and a speaker voice
command yourself; an app build or emulator check cannot prove your household's
speaker integration works. This project does not include a debloat script.

## Report a useful bug

Use the issue template. Include TV model, Android version, app version and exact
steps. Reproduce with demo scenery if possible. A short redacted error is more
useful than a full device dump. Do not share real addresses, linking codes,
account/session files or family screenshots.
