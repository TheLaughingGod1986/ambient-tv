# Work on Ambient TV

Use JDK 17, Android SDK 35 and the included Gradle 8.9 wrapper. Android Studio can
create `local.properties` automatically; a terminal build can instead use your
local `ANDROID_HOME`. No credentials or environment secrets are needed to build.

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
python3 scripts/check-public.py
```

The project uses Kotlin and Android Views/Canvas, without a web runtime. The
`api` module supplies the Projectivy AIDL contract. The `app` module owns Home,
photo acquisition/rendering, the DreamService and optional media integrations.

## Follow a feature through the code

| Area | Start with |
| --- | --- |
| Home navigation, focus, rows and refresh | `HomeActivity.kt`, `HomeFocusPolicy.kt`, `ResumeFeedPolicy.kt` |
| Photo source and selected person | `SettingsActivity.kt`, `AppPreferences.kt`, `Synology.kt`, `Photos.kt` |
| Recent/memory selection and storage budget | `PhotoMixPolicy.kt`, `PhotoPoolPolicy.kt`, `BitmapPolicy.kt` |
| Home photo covers | `HomePhotoPolicy.kt`, `OrientationPhotoView.kt` |
| Hero screensaver | `PhotoDreamService.kt`, `HeroDreamView.kt`, `HeroComposition.kt`, `HeroBackdrop.kt` |
| Continue Watching | `WatchNextReader.kt`, `PlexFeed.kt`, `PlexProtocol.kt`, `HomeDismissals.kt` |
| Weather opt-in | `AppPreferences.kt`, `WeatherStore.kt`, `WeatherData.kt` |
| Demo data and documentation images | `DemoPhotos.kt`, `DemoScreenshots.kt` in androidTest, `docs/ASSETS.md` |

Production Kotlin lives in `app/src/main/java/org/ambienttv/home/`. Tests live in
`app/src/test/java/org/ambienttv/home/`. Pure policies are kept separate where
practical so selection, freshness and cache behavior can be checked without a TV.

## Important boundaries

Photos use the named person's Personal Space membership, not a folder fallback.
A complete cache generation is published before older files are pruned. Downloads
and decoding are bounded. The Photos endpoints depend on Synology's implementation
and are not a stable public Photos API contract; DSM upgrades can break them.
The supported DSM login flow is documented by
[Synology](https://kb.synology.com/en-global/DG/DSM_Login_Web_API_Guide/2).

Plex authorization is independent of the native player. An authenticated empty
profile response stays empty. Next episodes must come from Plex's own feed.
Android's [Watch Next guidance](https://developer.android.com/training/tv/discovery/guidelines-app-developers)
assigns publication to media apps; reading the table is not access to private
YouTube History. Preserve that distinction in the UI and documentation.

Home refreshes on return, performs a short delayed retry and forces a foreground
minute refresh. Pending retries/observers stop when Home pauses. The generic
version includes this timing change but physical-TV verification of that change
is still pending. Do not describe emulator checks as proof of native Plex or
YouTube behavior.

## Make public screenshots

Use only a clean emulator and bundled demo photos. Follow [ASSETS.md](ASSETS.md).
The helper renders actual Home and screensaver views to images in app-private
storage. Do not install a documentation helper on someone's personal TV. Do not
replace generated examples with device captures from an authenticated household.

## Release a fork

Choose your own application ID and provider authority if publishing a separately
maintained app; update manifest/component references consistently. Use a new
Projectivy provider UUID. Configure release signing outside Git. Never publish a
keystore, private account backup or local SDK properties. Review third-party
notices and current service terms. Run the public-file check and an independent
secret scanner on the complete Git history before making a repository public.

## Approved family-first Home

Home uses a large featured memory, Browse photos, and two stacked photo shortcuts.
The three quick actions (YouTube, Plex, Synology Photos) precede four Continue
Watching cards. All apps is in the sidebar; the screensaver remains in Settings.
The compact sidebar reserves a 96px icon rail and a 28px gutter. Expanded labels
use an opaque overlay without moving the content. All eight
pages share the mountain sunset and stacked clock/date treatment.

Daily photo selection uses the Europe/London calendar day, retains its chosen
provider IDs through background cache refreshes, and prefers scenes absent from
the previous daily row. Capture times (when available) and local tiny colour
signatures help avoid burst-like repetitions; this is a heuristic, not semantic
scene recognition. A small library or a single favourite can still repeat across
days. Explicit Refresh photos allows a new daily choice. No personal images or
signatures are uploaded. Photo thumbnails honour JPEG EXIF rotation/mirroring.

`TvRegression` covers the stacked cards, three quick actions, empty and mixed
media, refresh focus, all eight page bounds, and screensaver Back. Its illustrative
feed exists only in the instrumentation source, not in production content.

## Secondary-screen presentation

TV Shows, Movies and Anime share the catalog renderer and `libraryCard`: artwork
is contained above a separate two-line title, so labels do not obscure poster
text. Category queries, ordering and pagination remain in PlexFeed. Photos uses
two complete four-column rows with orientation-aware bounded thumbnails; Apps
retains its installed-app listing in a scrollable four-column grid. Search uses
the native IME and the existing YouTube intent. Settings keeps its real actions
in three groups with a footer below the final card. The cached-photo viewer uses
matching navy controls while retaining favourite and framing actions.

`wireGrid` registers secondary controls with the regression focus/bounds probe.
Emulator checks cover 1080p and 720p, but cannot establish authenticated service
availability or native installed-app playback on a household TV.


## Responsiveness

Home reuses its wallpaper view across page changes instead of decoding the same
resource on every navigation. Initial wallpaper decoding runs off the UI thread
so it does not block the first focusable frame. Daily selection is memoized by local day, cache
file metadata, favourites and refresh state; unchanged minute ticks skip analysis.
Dimension metadata is cached separately. Cached files remain the offline source.
Plex catalog reads have a separate executor from image decoding. Page changes
discard queued obsolete work, with generation checks on running jobs. The photo
viewer drops queued intermediate frames during rapid navigation, hides controls
after five seconds and consumes the first OK used to reveal them.

TvRegression checks the unchanged-photo fast path, viewer hide/reveal, browsing,
Back and restored Home focus at 1080p and 720p. These do not measure Sony-wide
latency or establish NAS availability. On API errors 102/104, Synology requests
advertised API routing/version information and retries only a validated webapi
CGI path. Person-membership validation and cached fallback remain required.
