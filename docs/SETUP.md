# Set up Ambient TV

You can stop after the demo. Photo accounts, Plex, weather and replacing Home are
all optional. Allow about half an hour for a first build; downloading Android tools
can take longer.

![Setup path](images/setup-map.svg)

## 1. Build a copy

Install [Android Studio](https://developer.android.com/studio). Download or clone
this repository, then open its root folder in Android Studio. Let Gradle sync.
Select **JDK 17** for Gradle and install **Android SDK Platform 35** and build tools
when Android Studio asks. Android Studio writes your local SDK path to
`local.properties`; keep that file on your computer.

In the terminal inside the project, run:

```sh
./gradlew :app:assembleDebug
```

On Windows, replace `./gradlew` with `gradlew.bat`. The first build downloads its
dependencies. The APK (the Android installation file) appears at
`app/build/outputs/apk/debug/app-debug.apk`.

A debug build is suitable for trying the app. If you distribute your own ongoing
releases, sign them with your own protected release key. Keep the same signing
key for updates. Never commit that key or share it in a prompt.

## 2. Connect the TV and install

Finish whatever is playing first. Enable developer options and USB debugging or
wireless debugging using your TV manufacturer's instructions. Put the computer
and TV on the same trusted network. Modern Android versions offer a pairing code;
older TVs may expose a network debugging switch. Approve your computer on the TV.

Use [Android's ADB connection guide](https://developer.android.com/tools/adb) for
your Android version. Addresses and pairing codes are entered locally on your
computer, not in a public issue or chat. Then list connected devices:

```sh
adb devices
```

Copy the exact identifier from the left column and replace `YOUR_TV_SERIAL` below.
Always choose a serial explicitly, especially if an emulator is also connected.

```sh
adb -s YOUR_TV_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
```

This installs only Ambient TV. It does not remove apps, change Home or change the
screensaver. Open **Ambient TV Photos** in the TV app list. Scroll to **Preview
Ambient TV**. You should see the bundled lake, coast and forest scenes.

If you do not have a TV available, [create an Android TV virtual device](https://developer.android.com/studio/run/managing-avds)
in Android Studio's Device Manager and install there first.

## 3. Add your own photos, if you want

![The demo Home layout](images/home.png)

Open **Ambient TV Photos**, or **Settings → Photo connection settings** inside Home.

1. In Synology Photos on your phone or computer, open **Personal Space → People**.
   Give the person you want a unique name and copy it exactly.
2. In the TV app, enter that name, your NAS username, password and current verification
   code if your account uses one. This is the NAS login, not a Plex account.
3. Scroll to **Connection address** and enter your own NAS address. Prefer HTTPS
   with a trusted certificate. A private IPv4 HTTP address is accepted only with
   the explicit unencrypted-connection checkbox. Public HTTP addresses and
   embedded passwords in URLs are rejected.
4. Choose **Sign in and load photos**. A successful connection replaces the demo
   collection. Photos may take a little time to arrive.

This version selects a **person**, not an album, folder, Shared Space or arbitrary
cloud library. Two people with the exact same name are rejected; rename one in
Synology Photos. Unsupported additional approval flows may prevent login even if
the normal Synology app works. Do not extract sessions from another app.

The cache holds up to 48 selected thumbnails, aiming for 16 recent pictures and
32 older memories where the collection allows. The next successful refresh moves
through the older pool. Refresh is requested by Home or the wallpaper provider
and normally limited to once per six hours; it is not an always-running scheduler.
Home cover choices change with your TV's local calendar day. A screensaver changes
images about once a minute. A small library naturally repeats sooner.

**Go back to the demo:** choose **Use demo photos**. This keeps your saved sign-ins.
**Remove this app's NAS session and cached private photos:** choose **Disconnect and
erase this app's cached photos**. You can then choose demo mode. It does not delete
anything from your NAS. Uninstalling Ambient TV removes its app-private data.

## 4. Connect Plex, if you want

Choose **Connect Plex for Continue Watching**. Follow the displayed Plex linking
instructions in your browser. Enter any displayed linking code only on Plex's
own site. Never paste it into an issue or assistant prompt. Link the Plex account
whose history you want on Home. The app discovers accessible servers automatically
(up to three); it has no in-app managed-profile selector.

Plex in Ambient TV and the native Plex player are separate connections. If they
use different profiles, their progress can differ. Switching profiles in the
player does not switch Ambient TV's authorization. Next episodes are shown only
when Plex supplies them; Ambient TV does not guess an episode number.

YouTube has no separate login in Ambient TV. Its cards come from Android TV's
published feed and may not mirror every video in YouTube History.

## 5. Choose Home, screensaver or Projectivy

### Use it as Home

Open your TV's default-app or Home-app settings and choose **Ambient TV** if your
firmware offers that choice. Press the physical Home button and verify it returns
directly to the new layout. Also check Back, TV inputs and the apps you use.

Some manufacturers lock Home. The app includes an optional accessibility bridge
for the legacy Google Android TV launcher. It is not a universal Google TV or
manufacturer workaround and may briefly show the original screen. Its permission
is optional; it should not be enabled just to try the demo. See [troubleshooting](TROUBLESHOOTING.md).

**Undo:** select your original Home app in system settings and turn off the
Ambient TV accessibility bridge if you enabled it. Keep the original launcher
installed. This guide never asks you to disable a system launcher.

### Use the screensaver

Open **Android settings → Display → Screensaver** (names vary by TV). Choose
**Ambient TV screensaver**, then use the system's preview/start option. Open
**Photo screensaver layout** in Ambient TV for Hero or Classic layouts. Some TVs
override the idle timeout or use their own ambient mode.

![Hero screensaver with demo scenery](images/screensaver.png)

The app refuses to start its photo dream while active audio is detected. A remote
button dismisses it. Verify with your own playback apps before relying on idle
behavior. To undo, select your previous screensaver in Android settings.

### Keep Projectivy

Leave Projectivy as Home and select **Ambient TV Photos** in its wallpaper provider
settings, if your Projectivy version offers wallpaper plugins. Choose a rotation
interval there. Home, screensaver and wallpaper modes are alternatives you can
combine; Projectivy is not required for the standalone Home screen.

## 6. Optional weather

In photo connection settings, scroll to **Optional weather**. Enter a short place
label and latitude/longitude, check **Show weather for a place I choose**, then
choose **Save weather settings**. A nearby city centre is enough; your exact home
coordinates are unnecessary. Uncheck and save to switch it off.

Your chosen coordinates and network address reach Open-Meteo. The app requests no
Android location permission. Conditions are cached and can lag the weather outside.
Review [Open-Meteo's current API terms](https://open-meteo.com/en/docs) if you adapt
this project for commercial use.

## Before calling setup finished

Check Home, a photo, the screensaver preview, playback and your TV inputs. If you
use Google Home or Cast, test a voice request and casting from your own phone.
Ambient TV does not configure those services. Leave Google Play services, Cast,
Assistant, network and TV system components installed. Turn debugging off again
when you no longer need it.
