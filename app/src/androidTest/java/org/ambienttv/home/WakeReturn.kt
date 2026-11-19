package org.ambienttv.home

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Emulator-only check of Home just after standby, when a TV shows it several seconds before the network is
 * back. A loopback server stands in for the weather and artwork sources and can be switched off:
 * Continue Watching stills arrive by themselves once the source answers, at once when the network returns,
 * and from the kept copies while it is away; the weather is asked for again within seconds, and at once when
 * the emulator's own network comes back. No remote press is sent anywhere.
 * Run with -PtvTestRunner=org.ambienttv.home.WakeReturn.
 */
class WakeReturn:Instrumentation() {
 override fun onCreate(arguments:Bundle?){super.onCreate(arguments);start()}
 private fun shell(command:String):String=android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand(command)).bufferedReader().readText()
 private fun waitUntil(what:String,ms:Long,condition:()->Boolean):Long {
  val start=SystemClock.elapsedRealtime();val end=start+ms
  while(SystemClock.elapsedRealtime()<end){if(condition())return SystemClock.elapsedRealtime()-start;SystemClock.sleep(50)}
  error("Timed out after $ms ms waiting for $what")
 }
 override fun onStart(){
  val results=Bundle();val c=targetContext
  val setup=c.getSharedPreferences("setup",0)
  val oldWeather=listOf("weatherLabel","latitude","longitude").associateWith{setup.getString(it,null)};val oldEnabled=setup.getBoolean("weatherEnabled",false)
  val up=AtomicBoolean(false);val weather=AtomicInteger();val seen=mutableListOf<String>()
  val loopback=InetAddress.getByAddress(byteArrayOf(127,0,0,1))
  val server=ServerSocket(0,16,loopback);val base="http://${loopback.hostAddress}:${server.localPort}"
  val picture=Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888).also{it.eraseColor(android.graphics.Color.rgb(40,90,140))}
  val png=ByteArrayOutputStream().also{picture.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();picture.recycle()
  fun reading()="""{"current_units":{"temperature_2m":"°C"},"current":{"time":${System.currentTimeMillis()/1000},"temperature_2m":12.0,"weather_code":3,"is_day":1}}""".toByteArray(Charsets.UTF_8)
  val thread=Thread{
   try{while(!server.isClosed)server.accept().use{socket->
    socket.soTimeout=3000
    val reader=socket.getInputStream().bufferedReader();val request=reader.readLine().orEmpty()
    while(!reader.readLine().isNullOrEmpty())Unit
    val isWeather=request.contains("/weather");if(isWeather)weather.incrementAndGet()
    synchronized(seen){seen.add(request.substringAfter(' ').substringBefore(' ')+if(up.get())" ok" else " away")}
    // Switched off, the source hangs up without an answer, as a missing network would.
    if(up.get()){
     val body=if(isWeather)reading() else png
     socket.getOutputStream().apply{write("HTTP/1.1 200 OK\r\nContent-Type: ${if(isWeather)"application/json" else "image/png"}\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray());write(body);flush()}
    }
   }}catch(_:java.io.IOException){ /* Closed by the test's finally. */ }
  }.apply{isDaemon=true;start()}
  var home:HomeActivity?=null
  fun shown():Int{var n=0;runOnMainSync{n=home!!.artShownForTest()};return n}
  fun forgetDecoded(){ArtThumbs.clear();WatchNextReader.trimArtwork()}
  fun asked(tag:String)=synchronized(seen){seen.count{it.startsWith("/still/$tag/")}}
  fun row(tag:String)=(0..3).map{i->WatchItem("Demo video $tag ${i+1}",if(i%2==0)"com.plexapp.android" else "com.google.android.youtube.tv","https://example.invalid/demo/$tag/$i","$base/still/$tag/$i",60000,600000,providerRowId=i.toLong())}
  try {
   check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk")){"Use a clean emulator, never a personal TV"}
   check(Vault(c).read()==null && PlexApi.vault(c).read()==null){"Use an emulator with no accounts"}
   shell("input keyevent KEYCODE_WAKEUP")

   // Continue Watching stills, with demo cards whose pictures come from the loopback source.
   HomeActivity.previewFiles=DemoPhotos.files(c);HomeActivity.previewWeather=WeatherPanel("—","Weather is off","Demo",null,true);HomeActivity.previewResolution=1920 to 1080
   ArtDisk.clear(c);forgetDecoded();HomeActivity.previewWatch=row("a")
   home=startActivitySync(Intent(c,HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as HomeActivity
   val h=home
   fun showing(tag:String):Boolean{var yes=false;runOnMainSync{yes=h.feedSnapshotForTest().let{it.size==4 && it.all{item->item.title.startsWith("Demo video $tag ")}}};return yes}
   waitUntil("Home",15_000){var ready=false;runOnMainSync{ready=h.readyForTest() && h.hasWindowFocus()};ready && showing("a")}
   // Away for long enough that a second try fails as well: one retry is not enough after standby.
   waitUntil("the first and second tries to fail",15_000){asked("a")>=8}
   SystemClock.sleep(500);check(shown()==0){"Nothing can be drawn while the source is away"}
   up.set(true)
   val retried=waitUntil("stills to arrive by themselves",14_000){shown()==4}
   results.putString("stills after the source answers","all 4 drawn ${retried} ms later, no press")
   check(File(c.cacheDir,"row-art").listFiles().orEmpty().count{it.name.endsWith(".jpg")}==4){"Each still is kept for next time"}

   // A new row while the source is away; the network coming back must not wait for the next planned try.
   up.set(false);HomeActivity.previewWatch=row("b")
   runOnMainSync{h.showPageForTest("Home")}
   waitUntil("the new row",10_000){showing("b")}
   waitUntil("the new row's first tries to fail",10_000){asked("b")>=4}
   SystemClock.sleep(300);check(shown()==0){"The new row has no stills yet"}
   up.set(true);runOnMainSync{h.networkBackForTest()}
   val returned=waitUntil("stills after the network returns",2_500){shown()==4}
   results.putString("stills when the network returns","all 4 drawn ${returned} ms later")

   // The same row with the source away again: the kept copies are drawn and the network is not asked.
   up.set(false);forgetDecoded();val before=asked("b")
   runOnMainSync{h.showPageForTest("Home")}
   val kept=waitUntil("kept stills",3_000){showing("b") && shown()==4}
   SystemClock.sleep(1_500);check(asked("b")==before){"A kept still younger than a day needs no request: "+synchronized(seen){seen.takeLast(10).joinToString(" | ")}}
   results.putString("stills with the source away","all 4 drawn from kept copies in ${kept} ms, 0 requests")
   runOnMainSync{h.finish()};waitUntil("Home to close",5_000){h.isDestroyed};home=null

   // Weather, with Home running as it does on a TV (no preview data) and the loopback source as the service.
   HomeActivity.previewFiles=null;HomeActivity.previewWeather=null;HomeActivity.previewWatch=null
   shell("pm grant ${c.packageName} android.permission.READ_TV_LISTINGS")
   AppPreferences.saveWeather(c,true,"Sample town","51.5","-0.1");WeatherStore.clear(c);WeatherStore.endpointForTest="$base/weather"
   up.set(false);weather.set(0)
   home=startActivitySync(Intent(c,HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as HomeActivity
   val live=home
   waitUntil("Home without preview data",15_000){var ready=false;runOnMainSync{ready=live.readyForTest() && live.hasWindowFocus()};ready}
   waitUntil("the first weather try to fail",10_000){weather.get()>=1 && WeatherStore.retryDelay(c)!=null}
   check(WeatherStore.panel(c).condition=="Checking the weather…"){"While tries remain the header says it is checking, not that weather is unavailable"}
   up.set(true)
   val again=waitUntil("weather after a quick retry",9_000){WeatherStore.panel(c).temperature=="12°C"}
   results.putString("weather after the source answers","shown ${again} ms later, no press")

   // Quick tries used up and a fifteen-minute wait in force: the emulator's network going and coming back lifts it.
   val manager=c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
   check(manager.activeNetwork!=null){"The emulator needs a network for this step"}
   val now=System.currentTimeMillis()
   c.getSharedPreferences("weather",0).edit().remove("data").putLong("attemptAt",now).putLong("failedAt",now).putInt("failures",RetryPolicy.QUICK+1).commit()
   check(WeatherStore.panel(c).condition=="Weather unavailable")
   val tries=weather.get()
   shell("cmd connectivity airplane-mode enable")
   waitUntil("the network to go",20_000){manager.activeNetwork==null}
   SystemClock.sleep(2_000);check(weather.get()==tries){"No request while the wait is in force"}
   shell("cmd connectivity airplane-mode disable")
   waitUntil("the network to return",60_000){manager.activeNetwork!=null}
   val back=waitUntil("weather after the network returns",15_000){WeatherStore.panel(c).temperature=="12°C"}
   results.putString("weather when the network returns","shown ${back} ms after it, no press")
  } catch(e:Throwable) {
   results.putString("error",e.toString())
  }
  // Leave the emulator as it was before reporting: finish() ends this process.
  try {
   home?.let{a->runOnMainSync{a.finish()}}
   shell("cmd connectivity airplane-mode disable")
   server.close();thread.join(3000)
   WeatherStore.endpointForTest=null;WeatherStore.clear(c);ArtDisk.clear(c);forgetDecoded()
   setup.edit().putBoolean("weatherEnabled",oldEnabled).apply{oldWeather.forEach{(key,value)->if(value==null)remove(key) else putString(key,value)}}.commit()
   HomeActivity.previewFiles=null;HomeActivity.previewWeather=null;HomeActivity.previewWatch=null;HomeActivity.previewResolution=null
  } catch(e:Throwable) { if(!results.containsKey("error"))results.putString("error","cleanup: $e") }
  finish(if(results.containsKey("error"))0 else -1,results)
 }
}
