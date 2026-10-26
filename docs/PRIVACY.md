# Privacy and permissions

The public repository contains generic source, synthetic tests and generated
scenery. You supply accounts and configuration on your own TV. It includes no
household photo library, device backup, network configuration or signing key.

## Where data goes

| Feature | What it uses | Where it goes |
| --- | --- | --- |
| Demo photos | Three bundled scenery images | Copied into the app's private directory; no photo service |
| Synology Photos | Your entered NAS address, login and selected person | Directly to your NAS; selected thumbnails are cached on the TV |
| Plex connection | A linking code, revocable token and discovered servers | Plex's linking/resource services and your authorized Plex server |
| Continue Watching | Eligible records published by installed media apps | Read locally; card artwork may be fetched from the record's artwork address |
| Weather, off by default | Coordinates you choose | Open-Meteo over HTTPS; the provider also sees the request's network address |
| Clock | TV time and time zone | Rendered locally |
| Optional Home bridge | Window package names from the original launcher | Used locally to open Ambient TV; does not store screen text |

The app has no analytics, ads, crash-reporting backend or project-operated cloud
service. That does not make network requests anonymous. Your NAS, Plex, media
artwork hosts and weather provider have their own data handling policies.

## How credentials are handled

Synology passwords and verification codes are used for sign-in and are not saved.
Revocable Synology and Plex session tokens are encrypted with Android
Keystore-backed AES-GCM storage. Android backup is disabled for the app. Sign-in
windows block normal screenshots. Keystore protection is not a guarantee against
a compromised or rooted device.

Cached thumbnails and non-secret choices such as the NAS address, chosen person,
weather coordinates and media-library metadata live in app-private storage.
Photos are not individually encrypted at rest by the app. Android's storage
protection applies; a debug build is accessible through authorized `run-as`/ADB.
Do not share an app-data backup or a full diagnostic dump.

HTTPS uses normal certificate verification and redirects are disabled in the NAS
and Plex clients. Optional NAS HTTP is restricted to private IPv4 literals and
requires explicit consent in the UI. HTTP does not encrypt a password or session
even on a home network. Plex may also offer local HTTP server connections; review
your server's secure-connection settings. The manifest permits cleartext for these
local integrations; this is not an HTTPS-only app.

## Why Android asks for permissions

- **Internet:** NAS, Plex, published media artwork and optional weather.
- **TV listings / EPG read:** read the system's eligible Watch Next records. Some
  TV firmware restricts this even when the app requests it.
- **Dream service binding:** Android uses this to run the selected screensaver.
- **Accessibility, optional:** the legacy Home bridge requests window-change
  events and the active window's package name. Android grants broader access to
  accessibility services than this app uses. Leave it off if unnecessary.

There is no camera, microphone, contacts or Android location permission. The app
provides a read-only photo content provider to its own UID and the installed
Projectivy UID. It does not expose the photo directory to arbitrary apps.

## Remove or disconnect

**Disconnect and erase this app's cached photos** removes the app's NAS session
and cached source photos and attempts to revoke that session. Other Synology apps
and your original NAS files are unaffected. **Use demo photos** only changes which
collection is displayed; it is not a logout or data-erasure action. Use **Disconnect Plex from Ambient TV** in photo connection settings to erase
its local Plex token and memory caches. This does not revoke the token at Plex;
you can revoke the app authorization in your Plex account too. Uninstalling the app removes its private app
data; you can also revoke authorizations at the source services.

Before sharing a bug report, switch to bundled demo scenery. Exclude tokens,
linking codes, usernames, person names, server names, media filenames, home/network
addresses and private images. A screenshot may reveal more than an error message.
