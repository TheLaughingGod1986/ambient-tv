package org.ambienttv.home

import android.service.dreams.DreamService
import android.content.Context
import android.graphics.*
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.KeyEvent
import android.view.WindowManager
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import kotlin.math.min

/** Android owns activation and dismissal; this service never starts itself or acquires wake/audio locks. */
class PhotoDreamService:DreamService() {
 private val handler=Handler(Looper.getMainLooper())
 private val worker=Executors.newSingleThreadExecutor()
 private var active=false
 private var generation=0
 private var rendering=false
 private var nextFrame=0L
 private var startIndex=0
 private var sequenceGeneration=""
 private lateinit var picture:SaverDisplay
 private var heroStyle=true
 private var backDismissed=false
 override fun dispatchKeyEvent(event:KeyEvent):Boolean {
  // Keep both halves (and repeats) of Back inside the dream. Finishing on key-up
  // leaves no trailing Back event to pop the activity underneath.
  if(event.keyCode==KeyEvent.KEYCODE_BACK) {
   if(event.action==KeyEvent.ACTION_UP && !backDismissed) {
    backDismissed=true
    finish()
   }
   return true
  }
  return super.dispatchKeyEvent(event)
 }
 private val tick=object:Runnable {
  override fun run() {
   if(!active) return
   val audio=getSystemService(Context.AUDIO_SERVICE) as AudioManager
   if(!DreamPolicy.mayRun(audio.isMusicActive)) { finish();return }
   val now=SystemClock.elapsedRealtime()
   if(now>=nextFrame && !rendering) { nextFrame=now+DreamPolicy.INTERVAL;render() }
   picture.updateHeader()
   handler.postDelayed(this,1000L.coerceAtLeast(minOf(5000L,60000-System.currentTimeMillis()%60000)))
  }
 }
 override fun onAttachedToWindow() {
  super.onAttachedToWindow()
  isInteractive=false // Android dismisses on the first remote input; no key can launch an app through us.
  isFullscreen=true
  isScreenBright=false // Keep the existing OS dim/sleep policy; no extra keep-awake flag.
  window?.decorView?.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
  window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
  heroStyle=getSharedPreferences("dream",0).getString("style","hero")!="classic"
  picture=if(heroStyle)HeroDreamView(this) else DreamView(this);setContentView(picture as View)
 }
 override fun onDreamingStarted() {
  super.onDreamingStarted()
  backDismissed=false
  val audio=getSystemService(Context.AUDIO_SERVICE) as AudioManager
  if(audio.isMusicActive) { finish();return }
  active=true;generation++;nextFrame=0
  val files=Photos.currentFiles(this);sequenceGeneration=files.firstOrNull()?.name?.substringBefore('-').orEmpty()
  val sequencePrefs=getSharedPreferences("dream",0)
  startIndex=PhotoPoolPolicy.startCursor(sequencePrefs.getString("cursorGeneration","").orEmpty(),sequenceGeneration,sequencePrefs.getInt("cursor",0),files.size)
  picture.startBackground()
  getSharedPreferences("dream-state",0).edit().putBoolean("active",true).putBoolean("backgroundMotion",picture.motionEnabled).putLong("startedAt",System.currentTimeMillis()).apply()
  handler.post(tick)
 }
 private fun render() {
  rendering=true
  Photos.refresh(applicationContext);WeatherStore.refresh(applicationContext)
  val token=generation
  val files=Photos.currentFiles(this)
  val currentGeneration=files.firstOrNull()?.name?.substringBefore('-').orEmpty()
  if(currentGeneration!=sequenceGeneration){sequenceGeneration=currentGeneration;startIndex=0}
  val sequenceKey=sequenceGeneration
  val mode=getSharedPreferences("dream",0).getInt("columns",0).takeIf { it in 0..3 } ?: 0
  worker.execute {
   var bitmap:Bitmap?=null
   var count=0
   var fitPercent=0
   var previews=emptyList<Bitmap>()
   var selection=0
   var face:FaceRegion?=null
   var heroBackground:Bitmap?=null
   var framing="auto"
   try {
    if(files.isNotEmpty()) {
     if(heroStyle){
      val currentIndex=Math.floorMod(startIndex,files.size)
      framing=PhotoFraming.mode(applicationContext,files[currentIndex].name.substringAfter('-'))
      bitmap=PhotoDecoding.load(files[currentIndex]);face=bitmap?.let{HeroComposition.detect(it)}
      heroBackground=bitmap?.let{HeroBackdrop.create(it,face)}
      count=1
      val indices=HeroGeometry.previewIndices(files.size,currentIndex)
      val loaded=indices.mapNotNull {index->try {PhotoDecoding.load(files[index],640,640)?.let{index to it}}catch(_:Exception){null}}
      previews=loaded.map{it.second};selection=loaded.indexOfFirst{it.first==currentIndex}.coerceAtLeast(0)
     } else {
     val candidates=DreamPolicy.indices(files.size,startIndex,3).map { files[it] }
     val sizes=candidates.map {
      val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
      BitmapFactory.decodeFile(it.path,bounds);PhotoSize(bounds.outWidth,bounds.outHeight)
     }
     count=DreamPolicy.chooseCount(sizes,mode)
     fitPercent=(DreamPolicy.coverage(sizes,count)*100).toInt()
     val chosen=candidates.take(count)
     val frame=WeatherRenderer.composeSources(applicationContext,chosen,WeatherStore.panel(applicationContext),fullHeight=true)
     bitmap=BitmapFactory.decodeFile(frame.path)
     }
    }
   } catch(_:Exception) { /* Retain a previous frame on cache/decoder failure; weather/time remain live. */ }
   val result=bitmap;val columns=count;val coveragePercent=fitPercent;val previewImages=previews;val selectedPreview=selection;val heroFace=face;val cachedBackdrop=heroBackground
   handler.post {
    rendering=false
    if(!active || token!=generation) { result?.recycle();cachedBackdrop?.recycle();previewImages.forEach{it.recycle()};return@post }
    if(result!=null) {
     if(picture is HeroDreamView)(picture as HeroDreamView).showHero(result,previewImages,selectedPreview,heroFace,cachedBackdrop,framing) else picture.show(result)
     startIndex=PhotoPoolPolicy.nextCursor(startIndex,columns,files.size)
     getSharedPreferences("dream",0).edit().putInt("cursor",startIndex).putString("cursorGeneration",sequenceKey).apply()
     val state=getSharedPreferences("dream-state",0)
     state.edit().putInt("frames",state.getInt("frames",0)+1).putInt("columns",columns).putInt("fittedAreaPercent",coveragePercent).putString("layoutPolicy",if(heroStyle)"hero-v1" else "area-fit-v1").putLong("frameAt",System.currentTimeMillis()).apply()
    } else {cachedBackdrop?.recycle();previewImages.forEach{it.recycle()}}
   }
  }
 }
 private fun stop() {
  active=false;generation++;rendering=false;handler.removeCallbacks(tick)
  val draws=if(::picture.isInitialized) picture.backgroundDraws else 0
  if(::picture.isInitialized) picture.clear()
  getSharedPreferences("dream-state",0).edit().putBoolean("active",false).putBoolean("backgroundMotion",false).putInt("backgroundDraws",draws).putLong("stoppedAt",System.currentTimeMillis()).apply()
 }
 override fun onDreamingStopped() { stop();super.onDreamingStopped() }
 override fun onDetachedFromWindow() { stop();super.onDetachedFromWindow() }
 override fun onDestroy() { stop();worker.shutdownNow();super.onDestroy() }
}

internal class DreamView(c:Context,private val previewPanel:WeatherPanel?=null):View(c),SaverDisplay {
 private var current:Bitmap?=null
 private var previous:Bitmap?=null
 private var fadeAt=0L
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
 private val weatherPaint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
 private val clockTypeface=Typeface.create("sans-serif",Typeface.NORMAL)
 private val sourceRect=Rect(48,DreamPolicy.TOP,1872,DreamPolicy.BOTTOM)
 private val destinationRect=RectF(48f,DreamPolicy.TOP.toFloat(),1872f,DreamPolicy.BOTTOM.toFloat())
 private val backgroundPaint=Paint()
 private val drift=Matrix()
 private var gradient:LinearGradient=makeGradient(0xff263743.toInt())
 override var motionEnabled=false;private set
 override var backgroundDraws=0;private set
 private val animationTick=object:Runnable {
  override fun run() { if(motionEnabled) { invalidate();postDelayed(this,200) } }
 }
 private fun makeGradient(color:Int):LinearGradient {
  val base=MutedPalette.muted(color)
  val dark=MutedPalette.muted(base)
  return LinearGradient(-200f,-100f,1920f,1080f,intArrayOf(dark,base,dark),null,Shader.TileMode.MIRROR)
 }
 override fun startBackground() {
  backgroundDraws=0
  motionEnabled=try {
   MutedPalette.motionAllowed(android.provider.Settings.Global.getFloat(context.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f),(context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isPowerSaveMode)
  } catch(_:Exception) { false }
  if(motionEnabled) post(animationTick)
 }
 private fun stopBackground() { motionEnabled=false;removeCallbacks(animationTick) }
 override fun onDetachedFromWindow() { stopBackground();super.onDetachedFromWindow() }
 private var headerMinute=-1L
 private var panel=previewPanel ?: WeatherStore.panel(c)
 private val clock=SimpleDateFormat("HH:mm · EEE, d MMM",Locale.getDefault()).apply { timeZone=TimeZone.getDefault() }
 private var clockText=clock.format(Date())
 override fun updateHeader() {
  val minute=System.currentTimeMillis()/60000
  if(minute!=headerMinute) { headerMinute=minute;clockText=clock.format(Date());panel=previewPanel ?: WeatherStore.panel(context);invalidate() }
 }
 override fun show(bitmap:Bitmap) {
  previous?.recycle();previous=if(motionEnabled)current else null
  if(!motionEnabled) current?.recycle()
  current=bitmap;gradient=makeGradient(WeatherRenderer.sampleColor(bitmap));fadeAt=SystemClock.elapsedRealtime();invalidate()
 }
 override fun clear() { stopBackground();previous?.recycle();previous=null;current?.recycle();current=null }
 override fun onDraw(canvas:Canvas) {
  super.onDraw(canvas);canvas.drawColor(Color.rgb(13,19,25))
  val scale=min(width/1920f,height/1080f)
  canvas.save();canvas.translate((width-1920*scale)/2,(height-1080*scale)/2);canvas.scale(scale,scale)
  if(motionEnabled) {
   val phase=SystemClock.elapsedRealtime()/120000.0*2*Math.PI
   drift.setTranslate((kotlin.math.sin(phase)*160).toFloat(),(kotlin.math.cos(phase)*60).toFloat())
   backgroundDraws++
  } else drift.reset()
  gradient.setLocalMatrix(drift);backgroundPaint.shader=gradient
  canvas.drawRect(0f,0f,1920f,1080f,backgroundPaint)
  val fraction=((SystemClock.elapsedRealtime()-fadeAt)/450f).coerceIn(0f,1f)
  if(previous!=null && fraction>=1) { previous?.recycle();previous=null }
  paint.alpha=255;previous?.let { canvas.drawBitmap(it,sourceRect,destinationRect,paint) }
  paint.alpha=if(previous==null)255 else (255*fraction).toInt();current?.let { canvas.drawBitmap(it,sourceRect,destinationRect,paint) };paint.alpha=255
  if(current==null) {
   paint.color=Color.WHITE;paint.textSize=30f;paint.textAlign=Paint.Align.CENTER
   canvas.drawText("No cached photos available",960f,570f,paint)
  }
  if(AppPreferences.weatherEnabled(context))WeatherRenderer.drawPanel(canvas,panel,weatherPaint,AppPreferences.weatherLabel(context))
  paint.typeface=clockTypeface;paint.color=Color.WHITE;paint.textAlign=Paint.Align.RIGHT;paint.textSize=32f
  canvas.drawText(clockText,1810f,82f,paint);paint.textAlign=Paint.Align.LEFT
  canvas.restore()
  if(previous!=null) {
   if(fraction<1) postInvalidateDelayed(100) else { previous?.recycle();previous=null }
  }
 }
}
