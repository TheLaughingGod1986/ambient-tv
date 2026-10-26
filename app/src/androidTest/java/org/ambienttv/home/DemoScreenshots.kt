package org.ambienttv.home

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import java.io.File
import org.json.JSONObject

/** Emulator-only documentation renderer. No private library or media account is accessed. */
class DemoScreenshots:Instrumentation() {
 override fun onCreate(arguments:Bundle?){super.onCreate(arguments);start()}
 override fun onStart(){
  val report=JSONObject()
  var home:HomeActivity?=null
  try {
   check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk")){"Use a clean emulator, never a personal TV"}
   val c=targetContext
   check(Vault(c).read()==null && PlexApi.vault(c).read()==null){"Emulator has accounts; use a clean emulator"}
   check(AppPreferences.demo(c) && !AppPreferences.weatherEnabled(c) && AppPreferences.person(c).isEmpty()){"Expected untouched demo defaults"}
   val files=DemoPhotos.files(c);check(files.size==3 && files.all{it.isFile && it.length()>0})
   HomeActivity.previewFiles=files
   HomeActivity.previewWeather=WeatherPanel("—","Weather is off","Demo",null,true)
   HomeActivity.previewResolution=1920 to 1080
   home=startActivitySync(Intent(c,HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as HomeActivity
   val activity=home
   val deadline=SystemClock.elapsedRealtime()+15000
   while(SystemClock.elapsedRealtime()<deadline){var ready=false;runOnMainSync{ready=activity.readyForTest() && activity.photoReadyForTest()};if(ready)break;SystemClock.sleep(100)}
   runOnMainSync{check(activity.readyForTest() && activity.photoReadyForTest())}
   SystemClock.sleep(1200)
   val directory=File(c.filesDir,"demo-screenshots").apply{mkdirs()}
   fun save(view:View,name:String){
    val bitmap=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888)
    view.draw(Canvas(bitmap));File(directory,name).outputStream().use{check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
   }
   runOnMainSync{
    val content=activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
    check(content.width==1920 && content.height==1080){"Use a 1920x1080 emulator"}
    save(content,"home.png")
    check(activity.feedSnapshotForTest().isEmpty()){"Demo must not read personal media history"}
    val bounds=activity.boundsForTest()
    check(bounds.values.all{it.left>=0 && it.top>=0 && it.right<=1920 && it.bottom<=1080})
    activity.showPageForTest("Photos")
    report.put("demoPhotos",files.size).put("homeBoundsValid",true).put("noMediaHistory",true)
   }
   val hero=PhotoDecoding.load(files[0]) ?: error("Cannot decode demo photo")
   val thumbnails=files.map{PhotoDecoding.load(it,640,640) ?: error("Cannot decode demo thumbnail")}
   runOnMainSync{
    val saver=HeroDreamView(c,WeatherPanel("—","Weather is off","Demo",null,true))
    saver.measure(View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY));saver.layout(0,0,1920,1080)
    saver.showHero(hero,thumbnails,0);save(saver,"screensaver.png");saver.clear()
    report.put("productionSaverRendered",true)
    activity.finish()
   }
   val settings=startActivitySync(Intent(c,SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
   runOnMainSync{
    val fields=mutableListOf<android.widget.EditText>()
    fun collect(v:View){if(v is android.widget.EditText)fields.add(v);if(v is android.view.ViewGroup)for(i in 0 until v.childCount)collect(v.getChildAt(i))}
    collect(settings.window.decorView)
    check(fields.size==8 && fields.all{it.text.isEmpty()}){"Setup must not contain an address, name or account default"}
    check(settings.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
    report.put("setupFieldsBlank",true).put("setupScreenProtected",true);settings.finish()
   }
   val plex=startActivitySync(Intent(c,PlexConnectActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
   runOnMainSync{check(plex.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0);report.put("plexLinkScreenProtected",true);plex.finish()}
   report.put("passed",true)
  }catch(e:Exception){report.put("passed",false).put("error",e.javaClass.simpleName+": "+e.message)}
  finally{HomeActivity.previewFiles=null;HomeActivity.previewWeather=null;HomeActivity.previewResolution=null}
  File(targetContext.filesDir,"demo-verification.json").writeText(report.toString(2))
  finish(if(report.optBoolean("passed"))Activity.RESULT_OK else Activity.RESULT_CANCELED,Bundle().apply{putString("demo",report.toString())})
 }
}
