package org.ambienttv.home

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent

/**
 * Emulator-only speed check: compares Home's frame times with the original picture and with the moving sky,
 * at rest and while the D-pad moves focus. Run with -PtvTestRunner=org.ambienttv.home.SkyPerf.
 * An emulator is not TV hardware; this catches regressions, it does not prove TV performance.
 */
class SkyPerf:Instrumentation() {
 override fun onCreate(arguments:Bundle?){super.onCreate(arguments);start()}
 private fun shell(command:String):String=android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand(command)).bufferedReader().readText()
 /** Frames, janky percentage and frame-time percentiles from the framework's own counters. */
 private fun stats():String {
  val text=shell("dumpsys gfxinfo org.ambienttv.home")
  fun field(name:String)=Regex("$name: ([^\\n]+)").find(text)?.groupValues?.get(1)?.trim() ?: "?"
  return "frames=${field("Total frames rendered")} janky=${field("Janky frames")} p50=${field("50th percentile")} p90=${field("90th percentile")} p99=${field("99th percentile")}"
 }
 override fun onStart(){
  val results=Bundle()
  val c=targetContext
  try {
   check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk")){"Use a clean emulator, never a personal TV"}
   HomeActivity.previewFiles=DemoPhotos.files(c)
   HomeActivity.previewWeather=WeatherPanel("12°C","Rain","Demo",63,true)
   HomeActivity.previewResolution=1920 to 1080
   val home=startActivitySync(Intent(c,HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as HomeActivity
   val deadline=SystemClock.elapsedRealtime()+15000
   while(SystemClock.elapsedRealtime()<deadline){var ready=false;runOnMainSync{ready=home.readyForTest()};if(ready)break;SystemClock.sleep(100)}
   SystemClock.sleep(1500)
   fun nov(h:Int,m:Int)=1794873600000L+h*3_600_000L+m*60_000L
   val runs=listOf("photo" to null,"living" to SkyPolicy.scene(nov(14,0),51.5,-0.12,65),"living" to SkyPolicy.scene(nov(11,0),51.5,-0.12,73),"living" to SkyPolicy.scene(nov(12,40),51.5,-0.12,2))
   for((mode,scene) in runs){
    val name=scene?.description ?: "original picture"
    runOnMainSync{
     AppPreferences.setHomeSky(c,mode)
     val sky=home.skyForTest();sky.pinnedForTest=true;sky.motionForTest=true
     sky.applyMode(home.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as android.widget.FrameLayout)
     scene?.let{sky.show(it)};sky.setRunning(true)
    }
    SystemClock.sleep(3500)
    shell("dumpsys gfxinfo org.ambienttv.home reset")
    SystemClock.sleep(10_000)
    results.putString("$name · idle 10s",stats())
    shell("dumpsys gfxinfo org.ambienttv.home reset")
    // Move focus around the Home cards, a press every quarter second, as a viewer browsing would.
    val keys=listOf(KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_LEFT)
    repeat(3){keys.forEach{k->sendKeyDownUpSync(k);SystemClock.sleep(250)}}
    SystemClock.sleep(500)
    results.putString("$name · browsing",stats())
   }
   runOnMainSync{AppPreferences.setHomeSky(c,"living");home.finish()}
   finish(-1,results)
  } catch(e:Throwable) { AppPreferences.setHomeSky(c,"living");results.putString("error",e.toString());finish(0,results) }
 }
}
