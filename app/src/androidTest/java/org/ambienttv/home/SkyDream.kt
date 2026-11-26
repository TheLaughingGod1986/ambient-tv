package org.ambienttv.home

import android.app.Instrumentation
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock

/**
 * Emulator-only sky check: the moving sky keeps moving while the remote is used, holds still while a
 * screensaver covers Home and moves again by itself when it ends, even if it had come to rest beforehand.
 * Also checks how a new sky replaces the old one behind Home: in a few separate steps while watched, at once
 * otherwise, and never with one redraw after another.
 * Covers Android 9's screensaver signals on their own (as a TV sends them) and the emulator's real
 * screensaver. Run with -PtvTestRunner=org.ambienttv.home.SkyDream.
 */
class SkyDream:Instrumentation() {
 override fun onCreate(arguments:Bundle?){super.onCreate(arguments);start()}
 private fun shell(command:String):String=android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand(command)).bufferedReader().readText()
 private fun dreaming()=shell("dumpsys power").contains("mWakefulness=Dreaming")
 private fun waitUntil(what:String,condition:()->Boolean){
  val end=SystemClock.elapsedRealtime()+15_000
  while(SystemClock.elapsedRealtime()<end){if(condition())return;SystemClock.sleep(100)}
  error("Timed out waiting for $what")
 }
 override fun onStart(){
  val results=Bundle()
  val c=targetContext
  try {
   check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk")){"Use a clean emulator, never a personal TV"}
   HomeActivity.previewFiles=DemoPhotos.files(c)
   HomeActivity.previewWeather=WeatherPanel("12°C","Rain","Demo",63,true)
   HomeActivity.previewResolution=1920 to 1080
   AppPreferences.setHomeSky(c,"living")
   shell("input keyevent KEYCODE_WAKEUP")
   val home=startActivitySync(Intent(c,HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as HomeActivity
   waitUntil("Home"){var ready=false;runOnMainSync{ready=home.readyForTest() && home.hasWindowFocus()};ready}
   lateinit var motion:SkyMotionView
   runOnMainSync{
    val sky=home.skyForTest();sky.pinnedForTest=true;sky.motionForTest=true
    // Rain at midday: drops move in every frame, so a moving sky is easy to tell from a still one.
    sky.show(SkyPolicy.scene(1794873600000L+13*3_600_000L,51.5,-0.12,63));sky.setRunning(true)
    motion=sky.motionViewForTest ?: error("No moving layer")
   }
   fun frames():Int{var n=0;runOnMainSync{n=motion.framesForTest};return n}
   fun running():Boolean{var r=false;runOnMainSync{r=motion.runningForTest};return r}
   fun moved(ms:Long):Int{val before=frames();SystemClock.sleep(ms);return frames()-before}
   waitUntil("the sky to move"){moved(400)>2}
   fun rest(){runOnMainSync{motion.restForTest()};SystemClock.sleep(300);check(moved(1000)==0){"A resting sky must hold still"}}

   // A press every 300 ms, as when browsing: the sky gives way for a frame or two each time but keeps moving.
   val start=frames()
   repeat(8){sendKeyDownUpSync(if(it%2==0)android.view.KeyEvent.KEYCODE_DPAD_RIGHT else android.view.KeyEvent.KEYCODE_DPAD_LEFT);SystemClock.sleep(300)}
   val browsing=frames()-start
   check(browsing>8){"The sky must keep moving while the remote is used (frames=$browsing)"}
   results.putString("browsing","moved $browsing frames during 8 presses about 300 ms apart")

   // A new sky behind Home. The old cross-fade redrew the full-screen backdrop every frame for 1.5 s, which on
   // the TV meant 15-17 frames of about 90 ms in a row. Now: a few separate redraws while Home is watched, and
   // at once when Home has just come to the front, behind a screensaver, or while a fade is already running.
   var hour=13
   fun nextScene():SkyScene{hour++;return SkyPolicy.scene(1794873600000L+hour*3_600_000L,51.5,-0.12,63)}
   lateinit var backdrop:HomeBackdropView
   runOnMainSync{backdrop=home.skyForTest().backdrop}
   fun showAndWait(scene:SkyScene){
    runOnMainSync{home.skyForTest().show(scene)}
    waitUntil("the new sky"){var key:String?=null;runOnMainSync{key=home.skyForTest().shownKeyForTest};key==scene.key}
   }
   fun fading():Boolean{var f=false;runOnMainSync{f=backdrop.fadingForTest};return f}
   fun draws():Int{var n=0;runOnMainSync{n=backdrop.drawsForTest};return n}
   fun settled(){runOnMainSync{backdrop.arrivedForTest(BackdropFade.ARRIVAL_MS+1_000L)}}

   runOnMainSync{home.skyForTest().arrived()}
   var before=draws();showAndWait(nextScene())
   check(!fading()){"A sky that arrives just after Home comes to the front must appear at once"}
   SystemClock.sleep(800)
   val atOnce=draws()-before
   check(atOnce<=2){"A sky shown at once must cost one redraw of the backdrop (redraws=$atOnce)"}
   results.putString("sky on arrival","appeared at once with $atOnce backdrop redraw(s)")

   settled();before=draws();showAndWait(nextScene())
   check(fading()){"A sky that changes while Home is watched must fade"}
   val fadeStart=SystemClock.elapsedRealtime()
   waitUntil("the fade to end"){!fading()}
   val fadeMs=SystemClock.elapsedRealtime()-fadeStart;SystemClock.sleep(300)
   val stepped=draws()-before
   check(stepped in BackdropFade.STEPS-1..BackdropFade.STEPS+2){"A fade must be ${BackdropFade.STEPS} separate redraws (redraws=$stepped)"}
   check(fadeMs<=BackdropFade.STEPS*BackdropFade.STEP_MS+1_500L){"A fade must end within about a second (took $fadeMs ms)"}
   results.putString("sky while watched","faded in $stepped backdrop redraws over $fadeMs ms")

   // Presses put off the next step, so a step's redraw never shares a frame with moving focus; it ends afterwards.
   settled();showAndWait(nextScene())
   repeat(10){sendKeyDownUpSync(if(it%2==0)android.view.KeyEvent.KEYCODE_DPAD_RIGHT else android.view.KeyEvent.KEYCODE_DPAD_LEFT);SystemClock.sleep(100)}
   check(fading()){"Remote presses must hold the fade's next step"}
   waitUntil("the fade to end after the presses"){!fading()}
   results.putString("sky while pressing","fade held during 10 quick presses, finished after them")

   // A second sky during a fade replaces it at once instead of starting another fade.
   settled();showAndWait(nextScene());check(fading()){"A sky that changes while Home is watched must fade"}
   showAndWait(nextScene())
   check(!fading()){"A sky arriving during a fade must appear at once, not chain a second fade"}

   // Behind a screensaver nobody sees the change: at once, and nothing redrawn step by step under it.
   runOnMainSync{home.dreamForTest(true)};settled();before=draws();showAndWait(nextScene())
   check(!fading()){"A sky that changes behind the screensaver must appear at once"}
   runOnMainSync{home.dreamForTest(false)}
   results.putString("sky behind screensaver","appeared at once with ${draws()-before} backdrop redraw(s)")

   for(rested in listOf(false,true)){
    val label=if(rested)"rested before" else "moving before"
    // Android 9: the screensaver covers Home without pausing it or moving focus.
    if(rested)rest()
    runOnMainSync{home.dreamForTest(true)}
    check(!running() && moved(800)==0){"Android 9 signals, $label: the sky must stop behind the screensaver"}
    runOnMainSync{home.dreamForTest(false)}
    val signalled=moved(1500)
    check(signalled>5){"Android 9 signals, $label: the sky must move after the screensaver without a press (frames=$signalled)"}
    results.putString("Android 9 signals · $label","moved $signalled frames in 1.5 s after the screensaver")

    // This emulator's real screensaver: newer Android also pauses Home while it shows.
    if(rested)rest()
    shell("cmd dreams start-dreaming");waitUntil("the screensaver"){dreaming()}
    SystemClock.sleep(1500)
    check(!running() && moved(800)==0){"Real screensaver, $label: the sky must stop behind it"}
    shell("cmd dreams stop-dreaming");waitUntil("the screensaver to end"){!dreaming()}
    waitUntil("Home in front again"){var focused=false;runOnMainSync{focused=home.hasWindowFocus()};focused}
    SystemClock.sleep(1000)
    val real=moved(1500)
    check(real>5){"Real screensaver, $label: the sky must move after it without a press (frames=$real)"}
    results.putString("real screensaver · $label","moved $real frames in 1.5 s after the screensaver")
   }
   runOnMainSync{home.finish()}
   finish(-1,results)
  } catch(e:Throwable) {
   shell("cmd dreams stop-dreaming");results.putString("error",e.toString());finish(0,results)
  }
 }
}
