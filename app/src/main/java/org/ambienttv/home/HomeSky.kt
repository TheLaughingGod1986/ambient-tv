package org.ambienttv.home

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import java.util.TimeZone
import java.util.concurrent.Executors

/** How Home's background behaves. Living is the default; the original photograph stays available. */
internal enum class HomeSkyMode(val key:String,val label:String) {
 LIVING("living","Weather & time, moving"), STILL("still","Weather & time, still"), PHOTO("photo","Original sunrise picture");
 fun next()=values()[(ordinal+1)%values().size]
 companion object { fun of(key:String?)=values().firstOrNull{it.key==key} ?: LIVING }
}

/**
 * Owns the two background views and decides when the sky must be redrawn. The still scene and cloud
 * sprites are rendered on one low-priority thread, only when the hour's light or the weather changes.
 */
internal class HomeSky(private val context:Context,private val preview:Boolean) {
 var backdrop:HomeBackdropView;private set
 var motion:SkyMotionView?=null;private set
 var mode=HomeSkyMode.of(AppPreferences.homeSky(context));private set
 private val handler=Handler(Looper.getMainLooper())
 private val worker=Executors.newSingleThreadExecutor{r->Thread(r,"home-sky").apply{isDaemon=true}}
 private var shownKey:String?=null
 private var pendingKey:String?=null
 private var wanted=false
 var description="";private set

 init { backdrop=build() }
 private fun build():HomeBackdropView {
  motion=if(mode==HomeSkyMode.PHOTO)null else SkyMotionView(context)
  return HomeBackdropView(context,true,mode!=HomeSkyMode.PHOTO)
 }
 /** The background views stay first in the root, so page changes keep their rendered layers. */
 val views:List<android.view.View> get()=listOfNotNull(backdrop,motion)
 fun attach(root:FrameLayout){
  val metrics=context.resources.displayMetrics
  val unit=minOf(metrics.widthPixels/1920f,metrics.heightPixels/1080f)
  views.forEachIndexed{i,v->if(v.parent==null){
   // The moving layer covers only the visible sky strip, so redraws touch as little of the screen as possible.
   val params=if(v===motion)FrameLayout.LayoutParams((SkyRenderer.WIDTH*unit).toInt(),((SkyRenderer.VISIBLE+8)*unit).toInt()).apply{leftMargin=(SkyRenderer.LEFT*unit).toInt()}
    else FrameLayout.LayoutParams(-1,-1)
   root.addView(v,i,params)
  }}
 }
 /** Rebuilds the views if the owner changed the setting; returns true when the root must re-attach them. */
 fun applyMode(root:FrameLayout):Boolean {
  val chosen=HomeSkyMode.of(AppPreferences.homeSky(context))
  if(chosen==mode)return false
  views.forEach{root.removeView(it)}
  motion?.setRunning(false)
  mode=chosen;shownKey=null;pendingKey=null;backdrop=build();attach(root);refresh();setRunning(wanted)
  return true
 }

 /** Set by the emulator gallery so the minute tick does not replace the scene under review. */
 internal var pinnedForTest=false
 /** Lets the emulator speed check run the moving sky inside the demo preview. */
 internal var motionForTest=false
 fun refresh(now:Long=System.currentTimeMillis()) {
  if(mode==HomeSkyMode.PHOTO || pinnedForTest)return
  val scene=if(preview)SkyPolicy.scene(PREVIEW_TIME,51.5,-0.1,0) else {
   val zone=TimeZone.getDefault()
   val saved=AppPreferences.saved(context,"latitude").toDoubleOrNull()?.let{lat->AppPreferences.saved(context,"longitude").toDoubleOrNull()?.let{lon->lat to lon}}
    ?.takeIf{(lat,lon)->lat in -90.0..90.0 && lon in -180.0..180.0}
   val (lat,lon)=saved ?: SunPosition.fallback(zone,now)
   SkyPolicy.scene(now,lat,lon,currentCode(now))
  }
  show(scene)
 }
 /** Renders [scene] off the main thread unless it is already shown or on its way. Also used by the emulator gallery. */
 internal fun show(scene:SkyScene) {
  if(mode==HomeSkyMode.PHOTO)return
  description=scene.description
  if(scene.key==shownKey || scene.key==pendingKey)return
  pendingKey=scene.key
  val metrics=context.resources.displayMetrics
  val unit=minOf(metrics.widthPixels/1920f,metrics.heightPixels/1080f)
  val target=backdrop;val moving=motion;val first=shownKey==null
  worker.execute {
   try {
    // The first sky is wanted at once; later redraws yield to everything else on the TV.
    android.os.Process.setThreadPriority(if(first)android.os.Process.THREAD_PRIORITY_DEFAULT else android.os.Process.THREAD_PRIORITY_BACKGROUND)
    val look=scene.look
    val picture=SkyRenderer.render(scene,look,unit)
    val sprites:List<Bitmap> =if(moving!=null)SkyRenderer.sprites(scene,look,unit) else emptyList()
    handler.post {
     if(pendingKey!=scene.key || target!==backdrop)return@post
     shownKey=scene.key;pendingKey=null
     target.showScene(picture);moving?.show(scene,look,sprites)
    }
   } catch(_:Throwable) { handler.post{if(pendingKey==scene.key)pendingKey=null} } // an out-of-memory render keeps the previous sky
  }
 }
 /** The latest reading only while it is fresh; otherwise null, which draws a fair sky rather than a guess. */
 private fun currentCode(now:Long):Int? {
  if(!AppPreferences.weatherEnabled(context))return null
  val data=WeatherStore.read(context) ?: return null
  return data.code.takeIf{now-data.observedAt<=90*60*1000L && data.observedAt<=now+15*60*1000L}
 }
 /** Motion runs only for the living mode, while Home is in front, and when Android allows animation. */
 fun setRunning(value:Boolean){
  wanted=value
  val allowed=(!preview || motionForTest) && mode==HomeSkyMode.LIVING && try{MutedPalette.motionAllowed(android.provider.Settings.Global.getFloat(context.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f),(context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isPowerSaveMode)}catch(_:Exception){false}
  motion?.setRunning(value && allowed)
 }
 fun yieldToRemote(){motion?.yieldToRemote()}
 internal val shownKeyForTest get()=shownKey
 fun release(){motion?.setRunning(false);worker.shutdownNow();handler.removeCallbacksAndMessages(null)}

 companion object {
  /** Demo renders use one fixed golden-hour moment so documentation images are reproducible. */
  const val PREVIEW_TIME=1794930300000L
 }
}
