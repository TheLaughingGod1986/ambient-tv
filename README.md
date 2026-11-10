<div align="center">

# Ambient TV

### Your photos. Your apps. A calmer TV.

A photo-first home screen and screensaver for Android TV, with optional Synology Photos, Plex and weather.

[Download releases](https://github.com/TheLaughingGod1986/ambient-tv/releases) · [Get started](docs/SETUP.md) · [Copy a setup prompt](prompts/README.md) · [Troubleshooting](docs/TROUBLESHOOTING.md) · [Privacy](docs/PRIVACY.md)

![Ambient TV Home with generated lake, coast and forest demo scenery](docs/images/home.png)

</div>

**Try it without an account.** Ambient TV opens with three bundled scenery photos.
Connect your own services when you are ready. No subscription to Ambient TV, cloud
photo upload or API key is required. Your existing services may have their own terms.

> **Early preview:** this is a community project, not a store-ready replacement for
> every TV. Build and try the demo before changing your default Home app. Android
> versions, manufacturer Home buttons and media integrations behave differently.

## What you get

- **Photos above your apps.** A simple sidebar, photo collection, favourites and quick app shortcuts.
- **A photo screensaver.** A large image, matching backdrop, clock and small previews; classic multi-photo layouts are available too.
- **Old memories and new pictures.** For a chosen person in Synology Photos Personal Space, a bounded pool mixes recent photos with older memories. Cached photos stay available offline.
- **Continue Watching.** Read eligible Android TV records and optionally connect Plex for real progress and server-provided next episodes. Hold OK on a card to hide it locally.
- **Optional weather.** Choose a place yourself. Weather starts switched off; the clock uses your TV's time zone.
- **Optional Projectivy integration.** Use the photos as a wallpaper provider instead of making Ambient TV your Home app.

![Ambient TV hero screensaver rendered with generated scenery](docs/images/screensaver.png)

*These images are generated-scene demo renders from the Android app's production
views on an emulator. They contain no personal photos or account history. Weather
and media accounts are not connected. They are not photographs of a household TV.*

## New in 0.2.5

- Pin up to two TV inputs, such as a games console, beside the app shortcuts on Home. Choose the input and a name in Settings → TV & inputs; each press steps to the next choice, so nothing is typed. Selecting the tile switches the TV to that input.
- Photo thumbnails on the Photos page fill more of their tiles.
- When the featured photo is upright, the wide Home tile shows two upright photos side by side.
- Optional birthday for the chosen person: captions then show the age at the time, such as "3 weeks old". A photo taken on this day in an earlier year leads Home as "2 years ago today".
- Movies, TV Shows and Anime show 16 poster-shaped tiles a page instead of 10.
- Weather shows the day's chance of rain.
- Hero screensaver: the photo's own date and age replace the fixed slogan, upright photos are no longer zoomed far enough to cut people off, the preview strip shows what comes next, the backdrop is calmer, and weather shows the day's range.
- Phone screenshots (PNG files) are left out of the photo feed.
- Favourites stay on the TV through photo refreshes (up to 16), instead of rotating out with the other memories.
- Optional evening dimming of Home and the screensaver from 21:00 to 07:00, in Photo connection settings.

## New in 0.2.4

- The fill beside Plex posters and photos is now a smooth blur instead of coarse blocks.
- Home photo tiles fill more of their frame: a picture with one clear face is enlarged around it and keeps that face whole; other pictures are enlarged by a limited amount.
- The featured memory and the photo viewer show when the picture was taken.
- With no favourites yet, the third Home tile shows another memory instead of an empty box.
- Weather shows today's high and low, with more room above the page heading.
- Optional earliest photo date in Photo connection settings: ignore pictures of the chosen person taken before a day you pick, for when the NAS has filed older photos of other people under the same name.
- A poster whose stored artwork is very large or in a format the TV cannot read now loads as a resized copy from your own Plex server instead of staying blank.

## New in 0.2.3

- The expanded compact sidebar now sits on a solid panel that fades out to the right, so page headings and cards no longer show through the menu.
- One focus style on every Home page and in the photo viewer: a white ring, plus a pale fill on text buttons. The current page is marked quietly in the sidebar.
- Plex library tiles show the whole poster over a soft fill of itself. Paging buttons line up from the left edge.
- The photo viewer keeps the picture visible: a caption bottom-left and two compact buttons bottom-right.
- Faster page changes: decoded thumbnails, artwork and app icons are reused, the Apps list loads off the UI thread, and a library page opened in the last five minutes appears at once.
- The Home clock follows the TV's own time zone.

## New in 0.2.2

Compact mode now gives the content the space beside the narrow rail. Navigation expands over it with a translucent navy panel and a short transition; photos and controls stay fixed. Android’s disabled-animation preference is respected.

## New in 0.2.1

- More breathing room between the sidebar and content.
- Optional compact sidebar: labels appear on focus, with no content reflow. Enable it in Settings → Status & backup. Labelled navigation remains the default.

## New in 0.2

- Source status distinguishes a successful Home refresh from stale YouTube-published records.
- Choose full-photo or cinematic framing, with optional saved per-photo adjustments.
- Export and restore a credential-free preferences file.
- Signed releases and automated TV navigation checks. See [updates and recovery](docs/RELEASES.md).

## Start here

![Four steps: build, try the demo, connect your services, choose Home or screensaver](docs/images/setup-map.svg)

1. **Build the app.** Open this folder in Android Studio, use JDK 17 and install Android SDK 35. Or run `./gradlew :app:assembleDebug` in a terminal. On Windows use `gradlew.bat`.
2. **Install and try the demo.** Connect your TV with ADB, then run `adb -s YOUR_TV_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk`. Open **Ambient TV Photos** in the TV's app list, then choose **Preview Ambient TV**.
3. **Make it yours.** Optionally connect Synology Photos and Plex. Choose Ambient TV in your TV's Home or screensaver settings when you are happy with the demo.

New to Android tools? The [illustrated setup guide](docs/SETUP.md) explains each step,
including how to go back. Prefer help from a coding assistant? Use the
[plain-language prompts](prompts/README.md).

## What you need

| To use… | You need… |
| --- | --- |
| Demo Home and screensaver | An Android TV device, remote, and a computer to build/install the app |
| Your own photo collection | Synology Photos Personal Space with a named person under People, and a NAS account allowed to view those photos |
| Plex progress and next episodes | Plex on the TV, an accessible Plex server, and a separate authorization for Ambient TV |
| YouTube Continue Watching | The native YouTube TV app publishing eligible records to Android TV |
| Wallpaper provider | A compatible Projectivy installation; check its current feature availability |
| Weather | Internet access and a place label plus coordinates you choose |

The app's minimum SDK is 23. The Android Watch Next integration requires API 26+.
The generic build is checked on an API 35 emulator; physical-TV compatibility
is still being collected. YouTube may publish old or incomplete records even
when in-app History is current. Ambient TV cannot turn unpublished history into
resume cards. Plex only uses the profile authorized for Ambient TV; it does not
automatically follow a profile switch in the native player.

## For people who want to tinker

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
python3 scripts/check-public.py
```

[How the code fits together](docs/DEVELOPMENT.md) · [Current verification](docs/VERIFICATION.md)
· [Asset provenance](docs/ASSETS.md) · [Contributing](CONTRIBUTING.md)

Licensed under [Apache 2.0](LICENSE). Third-party credits are in [NOTICE](NOTICE).
Ambient TV is independent of Android, Google, Synology, Plex and Projectivy.
