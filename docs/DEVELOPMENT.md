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
sit on a solid navy panel that fades out to the right (`SidebarScrim`), without
moving the content, so page text never shows through the menu. Labels and widths
change in one layout pass; only the fade's opacity animates. All eight
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

TV Shows, Movies and Anime share the catalog renderer and `libraryCard`: the whole
poster is shown over a soft fill of itself, above a separate two-line title, so
labels do not obscure poster text. Category queries, ordering and pagination remain in PlexFeed. Photos uses
two complete four-column rows with orientation-aware bounded thumbnails; Apps
retains its installed-app listing in a scrollable four-column grid. Search uses
the native IME and the existing YouTube intent. Settings keeps its real actions
in three groups with a footer below the final card. The cached-photo viewer keeps
the photo visible: a caption bottom-left and two compact navy buttons bottom-right,
sized on the same 1920x1080 grid as Home, with the favourite and framing actions.

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

Focus styling is state-driven: each control builds its focused and resting
drawables once, so moving focus allocates nothing and needs no layout pass. The
rule is the same on every page: a white ring, plus a pale fill on text tiles. The
current page is marked quietly in the sidebar so a pale fill always means focus.

Decoded pictures are reused across page changes. `PhotoThumbs` (16 MiB) holds
Home and gallery thumbnails keyed by file identity and decode bounds; `ArtThumbs`
(8 MiB, ten minutes) holds Continue Watching stills and Plex posters. Both are
shared rather than owned by a view, are never recycled by a view, and are dropped
when Home is hidden or memory is low. Downloaded artwork bytes are released only
under real memory pressure. `AppIcons` keeps one decoded icon per package and
hands each caller its own drawable; icons are forgotten when Home is hidden, so an
updated app shows its new icon. The Apps listing, its labels and icons load on
their own worker; the last listing is shown at once while its icons are in memory. A Plex library page read in the
last five minutes is reused (Refresh reloads the page and forgets that library's
other kept pages; the cache is cleared with the Plex session). Returning to Home from another page shows the last feed on
the next frame, then replaces it only if the refresh differs; after Home has been
paused it always starts from a fresh read. Automated coverage of these paths in
this repository is limited: the layout constants and the underlying
`BoundedTtlCache` are unit-tested and TvRegression checks page bounds and focus
on an emulator, but the picture caches, the library page reuse and the feed reuse
have no dedicated test, and nothing here has been timed on TV hardware.

Grid pages share `wireGrid`: Right from the sidebar lands on the first card, Up
from a button goes to the card above it, and Down from a row above a shorter last
row still reaches that row. After paging, focus stays on the button that was
pressed while the new page still has it, otherwise it moves to the first item.
The Favourites grid is rebuilt on return from the viewer when its contents changed.

TvRegression checks the unchanged-photo fast path, viewer hide/reveal, browsing,
Back and restored Home focus at 1080p and 720p. These do not measure Sony-wide
latency or establish NAS availability. On API errors 102/104, Synology requests
advertised API routing/version information and retries only a validated webapi
CGI path. Person-membership validation and cached fallback remain required.

## Earliest photo date

Person membership comes from the NAS's own face matching, which can file older
pictures of other people under the selected name. Photo connection settings accept
an optional YYYY-MM-DD day (`PhotoDatePolicy`); photos captured before it are left
out of the catalog before the recent/memory mix is chosen. Blank keeps every photo.
Nothing on the NAS is changed, and membership validation still applies.

## Overnight NAS availability

Photo connection settings can restrict photo acquisition to an overnight NAS
window. Use the NAS timezone, which may differ from the TV timezone. Automatic
sync starts from 01:15 and new attempts stop at 05:45, leaving time before a
06:00 NAS shutdown. JobScheduler requires a network, persists the job across
reboots, and may defer execution in standby; a powered-off or force-stopped TV
cannot be guaranteed to sync. No exact alarm or screen wake is requested.
All photo acquisition, including manual refresh, respects the window when this
mode is enabled. Daytime calls return local cache status without recording a
failed NAS attempt. Turning the mode off restores ordinary refresh behaviour.
Disconnect cancels the pending job. The public app leaves this option off until
configured by its owner.

Settings shows the last successful sync and current cached count. Daily Home
rotation still uses Europe/London and stores at most eight daily rows locally,
preferring scenes absent from that recent history when the pool permits. Photo
IDs remain in private preferences and are not logged or uploaded.

## Responsive artwork and feed updates

The static backdrop and visible photo/video cards use hardware layers to reuse
static drawing between focus changes. Cards use native outline clipping
on hardware and keep their path fallback for software previews. Neither changes
the layout or image treatment.

Video artwork has a two-worker executor for the visible tiles, separate from photo decoding and
Plex catalog requests. Page changes discard queued obsolete work; generation
checks reject stale results. A missing Home thumbnail gets one delayed retry and
is retried on return if still absent. No continuous retry loop is used.

Plex artwork reads an immutable source snapshot instead of waiting for the
catalog/resume network monitor. Verified server connections are reused for up to
15 minutes, with rediscovery on failure. Compressed artwork caches are bounded
(4 MiB Plex, 3 MiB native feed), release under memory pressure and own bytes,
not shared/recycled view bitmaps. Native artwork accepts bounded HTTP(S)
redirects without allowing HTTPS downgrades.

Plex artwork is fetched as stored. If that file exceeds the size bound or cannot
be decoded on the TV, one further bounded request asks the same server's photo
transcoder for a resized copy. The soft fill behind posters and photos is made by
halving the decoded picture step by step and smoothing the tiny result
(`FillBlur`), so it reads as a blur and not as blocks.

A separately debounced native-provider read can update YouTube cards while Plex
is still refreshing. It reads only the Watch Next columns used by Home. YouTube
must publish eligible progress; neither this path nor retries read private
YouTube History or invent resume positions. The clean-emulator regression checks
artwork redirects, cache reuse, invalid redirects and loading while Plex's
metadata monitor is held, alongside the existing TV navigation checks.
