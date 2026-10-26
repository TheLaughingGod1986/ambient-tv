# Pictures you can share

The public demo uses newly generated scenery: an alpine lake, a coastline and a
forest path. No personal reference images were supplied to the image generator.
The original project's family photos, TV photographs, contact sheets, private
screenshots and diagnostic archives are excluded from this repository and history.

| File | Origin and purpose |
| --- | --- |
| `app/src/main/assets/demo/lake.jpg` | AI-generated alpine lake; demo photo |
| `app/src/main/assets/demo/coast.jpg` | AI-generated coast; demo photo |
| `app/src/main/assets/demo/forest.jpg` | AI-generated woodland; demo photo |
| `app/src/main/res/drawable/home_mountains.jpg` | The same generated lake scene, used behind Home |
| `docs/images/home.png` | Production Home view rendered on a clean Android emulator using only bundled scenery |
| `docs/images/screensaver.png` | Production HeroDreamView rendered on that emulator using only bundled scenery |
| `docs/images/setup-map.svg` | Original code-drawn setup diagram |
| App banner and icon XML | Simple vector resources distributed with the app under Apache 2.0 |

Scenery was generated specifically for this public project. No stock-photo download,
photographer identity or external photo account is required. Bundled assets are
distributed with this project under its license to the extent rights apply.
Generated scenery can contain visual imperfections and is not a record of a real
place. JPEG EXIF/XMP/IPTC/comment metadata was removed without decoding its pixels.
The PNG renders contain no text or EXIF metadata.

## Reproduce the demo renders

Create a fresh **1920×1080 Android emulator** in Android Studio. Use API 35, landscape,
and a TV display profile. An ARM64 Google APIs image is used for the current render
check; this is a display/render check, not proof of hardware TV services. Keep all
accounts disconnected and weather off. Do not use a personal TV for documentation.

```sh
adb devices
scripts/make-demo-screenshots.sh emulator-YOUR_SERIAL
```

The script only accepts an emulator serial. It builds and installs the app and its
small test helper, then renders production Views/Canvas into two images. The helper
refuses a configured NAS/Plex session. It bypasses live media reads and supplies
only bundled scenery. These are **Android demo renders**, not a full DreamService
idle-cycle test, authentic media history or a photograph of physical TV hardware.
The test helper is not part of the normal application APK.

If adding new JPEG demo assets, use your own generated/cleared images and remove
metadata with `python3 scripts/strip-image-metadata.py path/to/image.jpg`.
Then run `python3 scripts/check-public.py` and visually inspect every image.
Do not use a black rectangle or blur as the only protection for a family screenshot;
replace it with a clean demo render from the start.
