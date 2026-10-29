package org.ambienttv.home

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import java.io.File
import org.json.JSONObject

/** Clean-emulator integration checks. Never operates on an authenticated household. */
class TvRegression:Instrumentation(){
 private fun main(action:()->Unit){var failure:Throwable?=null;runOnMainSync{try{action()}catch(e:Throwable){failure=e}};failure?.let{throw it}}
 override fun onCreate(args:Bundle?){super.onCreate(args);start()}
 override fun onStart(){
  var stage="startup";val oldCompact=targetContext.getSharedPreferences("home-ui",0).getBoolean("compactSidebar",false);val report=JSONObject();var home:HomeActivity?=null
  try{
   check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk"))
   val c=targetContext;check(Vault(c).read()==null && PlexApi.vault(c).read()==null)
   val files=DemoPhotos.files(c);HomeActivity.previewFiles=files;HomeActivity.previewWeather=WeatherPanel("—","Weather is off","Demo",null,true)
   home=startActivitySync(Intent(c,HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as HomeActivity
   val h=home
   fun waitFor(condition:()->Boolean){val end=SystemClock.elapsedRealtime()+10000;while(SystemClock.elapsedRealtime()<end){var ok=false;main{ok=condition()};if(ok)return;SystemClock.sleep(100)};var details="";main{details="ready=${h.readyForTest()}, photos=${h.photoReadyForTest()}, window=${h.hasWindowFocus()}, page=${h.pageForTest()}"};error("Timed out waiting for TV UI: $details")}
   waitFor{h.readyForTest() && h.photoReadyForTest() && h.hasWindowFocus()}
   val w=h.resources.displayMetrics.widthPixels;val height=h.resources.displayMetrics.heightPixels
   check(w>=1280 && height>=720);val directory=File(c.filesDir,"regression").apply{mkdirs()}
   fun capture(name:String){main{val content=h.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0);val image=Bitmap.createBitmap(w,height,Bitmap.Config.ARGB_8888);content.draw(Canvas(image));File(directory,"$name.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()}}
   c.getSharedPreferences("home-ui",0).edit().putBoolean("compactSidebar",true).commit()
   main{h.showPageForTest("Home")};waitFor{h.readyForTest() && h.photoReadyForTest()}
   stage="unchanged daily photo fast path"
   main{h.refreshDecorForTest()};waitFor{h.photoWorkIdleForTest()}
   var runs=0;main{runs=h.photoSelectionRunsForTest()}
   repeat(3){main{h.refreshDecorForTest()};waitFor{h.photoWorkIdleForTest()}}
   main{check(h.photoSelectionRunsForTest()==runs)}
   stage="directional navigation"
   main{h.focusForTest("nav:0")};waitFor{h.sidebarExpandedForTest()};SystemClock.sleep(250);capture("sidebar-expanded");var photoBounds=android.graphics.Rect();main{photoBounds=h.boundsForTest().getValue("photo:0");check(kotlin.math.abs(photoBounds.left-w*HomeLayout.COMPACT_LEFT/1920f)<2f);check(photoBounds.width()>h.boundsForTest().getValue("photo:1").width()*1.3f)};sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);waitForIdleSync();waitFor{h.focusKeyForTest()=="photo:0" && !h.sidebarExpandedForTest()};main{check(h.boundsForTest().getValue("photo:0")==photoBounds)};SystemClock.sleep(250);capture("sidebar-compact")
   for(i in 1..2){sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);waitForIdleSync();waitFor{h.focusKeyForTest()=="photo:$i"}}
   sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);waitForIdleSync();waitFor{h.focusKeyForTest()=="photo:3"}
   for(i in 1 downTo 0){sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT);waitForIdleSync();waitFor{h.focusKeyForTest()=="photo:$i"}}
   sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);waitForIdleSync();waitFor{h.focusKeyForTest()=="footer:0"}
   for(i in 1..2){sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);waitForIdleSync();waitFor{h.focusKeyForTest()=="footer:$i"}}
   main{h.focusForTest("nav:1")};sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);waitFor{h.pageForTest()=="TV Shows"}
   sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitFor{h.pageForTest()=="Home"}
   stage="mixed media"
   HomeActivity.previewWatch=(0..3).map{i->WatchItem("Demo video ${i+1}: a deliberately long title to check truncation",if(i%2==0)"com.plexapp.android"else "com.google.android.youtube.tv","https://example.invalid/demo/$i","android.resource://org.ambienttv.home/"+R.drawable.home_mountains,if(i==3)0 else 60000,if(i==3)0 else 600000,providerRowId=i.toLong(),nextEpisode=i==2)}
   main{h.showPageForTest("Home")};waitFor{h.feedSnapshotForTest().size==4}
   main{h.focusForTest("footer:0")};sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);waitForIdleSync();waitFor{h.focusKeyForTest()=="heading:1"}
   sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);waitForIdleSync();waitFor{h.focusKeyForTest()=="media:0"}
   for(i in 1..3){sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);waitForIdleSync();waitFor{h.focusKeyForTest()=="media:$i"}}
   main{h.refreshDecorForTest()};waitForIdleSync();main{check(h.focusKeyForTest()=="media:3")}
   stage="quick launch focus"
   var youtube:android.widget.TextView?=null
   main{h.focusForTest("footer:0");youtube=h.currentFocus as android.widget.TextView;check(youtube!!.currentTextColor==android.graphics.Color.rgb(9,22,36))}
   capture("youtube-focused")
   main{h.focusForTest("footer:1");check(youtube!!.currentTextColor==android.graphics.Color.rgb(240,247,252))}
   capture("youtube-unfocused")
   stage="pages"
   for(page in listOf("Home","TV Shows","Movies","Anime","Apps","Photos","Search","Settings")){
    stage="page: $page";main{h.showPageForTest(page)};waitFor{h.secondaryReadyForTest() && (page!="Home" || h.feedSnapshotForTest().size==4 && h.photoReadyForTest())};SystemClock.sleep(400)
    main{check(h.boundsForTest().values.all{it.left>=0 && it.top>=0 && it.right<=w && it.bottom<=height})}
    capture(page.replace(' ','-'))
   }
   stage="photo viewer and restored Home focus"
   main{h.showPageForTest("Home");h.focusForTest("footer:1")}
   val viewer=startActivitySync(Intent(c,PhotoViewerActivity::class.java).putExtra("photo",files.first().name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as PhotoViewerActivity
   waitFor{viewer.controlsVisibleForTest()}
   waitFor{!viewer.controlsVisibleForTest()}
   sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);waitFor{viewer.controlsVisibleForTest()}
   sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);waitFor{viewer.indexForTest()==1}
   sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitFor{h.hasWindowFocus() && h.focusKeyForTest()=="footer:1"}
   stage="backup"
   val old=PreferenceBackup.snapshot(c)
   try{val sample=PreferenceSnapshot("classic",2,"full",mapOf("demo.jpg" to "top"),setOf("demo.jpg"));PreferenceBackup.restore(c,BackupPolicy.decode(BackupPolicy.encode(sample)));check(PreferenceBackup.snapshot(c)==sample)}finally{PreferenceBackup.restore(c,old)}
   main{h.showPageForTest("Home")}
   // Real Android DreamService lifecycle, with settings changes confined to this clean emulator.
   fun shell(command:String){uiAutomation.executeShellCommand(command).use{android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()}}
   stage="dream start"
   shell("settings put secure screensaver_enabled 1");shell("settings put secure screensaver_components org.ambienttv.home/.PhotoDreamService")
   shell("su 0 cmd dreams start-dreaming")
   waitFor{c.getSharedPreferences("dream-state",0).getBoolean("active",false)}
   stage="dream back";sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
   waitFor{!c.getSharedPreferences("dream-state",0).getBoolean("active",true)}
   main{check(h.pageForTest()=="Home")}
   report.put("passed",true).put("width",w).put("height",height).put("pages",8).put("compactSidebar",true).put("contentDoesNotMove",true).put("directionalNavigation",true).put("mixedMedia",true).put("refreshPreservesFocus",true).put("enterAndBack",true).put("backupRoundTrip",true).put("dreamBack",true)
  }catch(e:Throwable){report.put("passed",false).put("error",stage+": "+e.javaClass.simpleName+": "+e.message)}
  finally{targetContext.getSharedPreferences("home-ui",0).edit().putBoolean("compactSidebar",oldCompact).commit();HomeActivity.previewWatch=null;HomeActivity.previewFiles=null;HomeActivity.previewWeather=null;HomeActivity.previewResolution=null;home?.let{main{it.finish()}}}
  File(targetContext.filesDir,"regression.json").writeText(report.toString(2));finish(if(report.optBoolean("passed"))Activity.RESULT_OK else Activity.RESULT_CANCELED,Bundle().apply{putString("regression",report.toString())})
 }
}
