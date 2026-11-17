package org.ambienttv.home

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import java.io.File

/**
 * Emulator-only: renders Home with the living sky for a fixed set of hours and weather, using demo photos,
 * so the scenes can be reviewed side by side. Run with -PtvTestRunner=org.ambienttv.home.SkyGallery.
 */
class SkyGallery:Instrumentation() {
 override fun onCreate(arguments:Bundle?){super.onCreate(arguments);start()}
 override fun onStart(){
  val results=Bundle()
  try {
   check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk")){"Use a clean emulator, never a personal TV"}
   val c=targetContext
   val files=DemoPhotos.files(c)
   HomeActivity.previewFiles=files
   HomeActivity.previewWeather=WeatherPanel("12°C","Partly cloudy","Demo",2,true,"High 14°  ·  Low 7°")
   HomeActivity.previewResolution=1920 to 1080
   val home=startActivitySync(Intent(c,HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as HomeActivity
   val deadline=SystemClock.elapsedRealtime()+15000
   while(SystemClock.elapsedRealtime()<deadline){var ready=false;runOnMainSync{ready=home.readyForTest()};if(ready)break;SystemClock.sleep(100)}
   SystemClock.sleep(1500)
   val directory=File(c.filesDir,"sky-gallery").apply{deleteRecursively();mkdirs()}
   // London, 17 November (UTC), plus one midsummer noon for a high sun.
   fun nov(h:Int,m:Int)=1794873600000L+h*3_600_000L+m*60_000L
   val cases=listOf(
    Triple("01-sunrise-clear",nov(7,45),0),Triple("02-morning-clear",nov(9,40),0),Triple("03-morning-fog",nov(10,20),45),
    Triple("04-afternoon-partly",nov(12,40),2),Triple("05-afternoon-rain",nov(13,30),63),Triple("06-afternoon-heavy",nov(14,0),65),
    Triple("07-sunset-clear",nov(15,45),0),Triple("08-dusk-overcast",nov(16,30),3),Triple("09-night-clear",nov(22,0),0),
    Triple("10-night-rain",nov(22,0),61),Triple("11-snow",nov(11,0),73),Triple("12-thunder",nov(15,0),95),
    Triple("13-summer-noon",1782043200000L,1),Triple("14-drizzle-morning",nov(9,0),53))
   for((name,time,code) in cases){
    val scene=SkyPolicy.scene(time,51.5,-0.12,code)
    runOnMainSync{home.skyForTest().pinnedForTest=true;home.skyForTest().show(scene)}
    val until=SystemClock.elapsedRealtime()+8000
    while(SystemClock.elapsedRealtime()<until){var shown=false;runOnMainSync{shown=home.skyForTest().shownKeyForTest==scene.key};if(shown)break;SystemClock.sleep(50)}
    SystemClock.sleep(1800)
    runOnMainSync{home.skyForTest().motion?.advanceForTest(6f)}
    SystemClock.sleep(200)
    // A real screen capture: a software draw() would reuse the backdrop's stale drawing cache.
    val bitmap=uiAutomation.takeScreenshot() ?: error("Screenshot failed")
    File(directory,"$name.png").outputStream().use{check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
    results.putString(name,scene.description)
   }
   runOnMainSync{home.finish()}
   finish(-1,results)
  } catch(e:Throwable) { results.putString("error",e.toString());finish(0,results) }
 }
}
