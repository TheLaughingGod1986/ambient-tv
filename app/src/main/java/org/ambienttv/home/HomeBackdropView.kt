package org.ambienttv.home
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
/**
 * Quiet native ambience behind Home. With [living] it shows the code-drawn sky for the hour and weather,
 * changing it in a few steps when the scene changes; otherwise the bundled mountain photograph. It never
 * redraws frame after frame: the moving parts of the sky live in the small [SkyMotionView] above it.
 */
internal class HomeBackdropView(c:Context,private val sunrise:Boolean=false,private val living:Boolean=false):View(c) {
 private var mountains:Bitmap?=null
 init {
  // Cache the static multi-pass scenery on the GPU; focus changes only composite it.
  setLayerType(View.LAYER_TYPE_HARDWARE,null)
  // Decode the static wallpaper without blocking the first focusable frame. The living sky needs no photograph.
  if(!living)Thread({
   val decoded=BitmapFactory.decodeResource(resources,if(sunrise)R.drawable.home_sunrise else R.drawable.home_mountains)
   val strip=if(sunrise && decoded!=null)headerStrip(decoded)else null
   post{mountains=strip ?: decoded;cropped=strip!=null;invalidate()}
  },"tv-wallpaper").apply{isDaemon=true;start()}
 }
 private var cropped=false
 private val headerArea=RectF(104f,0f,1920f,360f)
 private var scene:Bitmap?=null
 /** The sky being faded out, or null when no fade is running. */
 private var previous:Bitmap?=null
 private var step=BackdropFade.STEPS
 private var watched=false
 private var arrivedAt=0L
 private val fadePaint=Paint(Paint.FILTER_BITMAP_FLAG)
 private val nextStep=Runnable{advance()}
 /**
  * Shows a newly rendered sky: in a few separate steps while someone is watching Home, otherwise at once
  * (see [BackdropFade]). Each step redraws this full-screen layer once; between steps nothing is redrawn.
  */
 fun showScene(next:Bitmap){
  if(!living)return
  val fade=BackdropFade.fades(SystemClock.uptimeMillis(),arrivedAt,watched,scene==null,previous!=null)
  removeCallbacks(nextStep)
  if(fade){previous=scene;step=1;postDelayed(nextStep,BackdropFade.STEP_MS)}else{previous=null;step=BackdropFade.STEPS}
  scene=next;invalidate()
 }
 private fun advance(){
  if(previous==null)return
  step++
  if(step>=BackdropFade.STEPS){previous=null;step=BackdropFade.STEPS}else postDelayed(nextStep,BackdropFade.STEP_MS)
  invalidate()
 }
 /** Home has just come to the front: skies that arrive in the next moments appear at once. */
 fun arrived(){arrivedAt=SystemClock.uptimeMillis()}
 /** Whether Home is in front with nothing covering it. Unwatched, a running fade ends and new skies appear at once. */
 fun setWatched(value:Boolean){watched=value;if(!value)finishFade()}
 /** A remote press puts off the next fade step, so the step's redraw never shares a frame with moving focus. */
 fun yieldToRemote(){if(previous!=null){removeCallbacks(nextStep);postDelayed(nextStep,BackdropFade.STEP_MS)}}
 private fun finishFade(){if(previous!=null){removeCallbacks(nextStep);previous=null;step=BackdropFade.STEPS;invalidate()}}
 override fun onDetachedFromWindow(){super.onDetachedFromWindow();removeCallbacks(nextStep);previous=null;step=BackdropFade.STEPS}
 /** Emulator checks only: how many times the backdrop was drawn, whether a fade is running, and an earlier arrival. */
 internal var drawsForTest=0;private set
 internal val fadingForTest get()=previous!=null
 internal fun arrivedForTest(millisAgo:Long){arrivedAt=SystemClock.uptimeMillis()-millisAgo}
 /** Keep only the rows the header shows, at the size they are drawn, instead of the whole panorama. */
 private fun headerStrip(source:Bitmap):Bitmap? = try {
  val cropH=(source.width*360f/1816f).toInt().coerceAtMost(source.height);val top=(source.height-cropH)/2
  val unit=minOf(resources.displayMetrics.widthPixels/1920f,resources.displayMetrics.heightPixels/1080f)
  val w=(1816*unit).toInt().coerceIn(1,source.width);val h=(360*unit).toInt().coerceIn(1,cropH)
  Bitmap.createBitmap(source,0,top,source.width,cropH,Matrix().apply{setScale(w.toFloat()/source.width,h.toFloat()/cropH)},true).also{if(it!==source)source.recycle()}
 }catch(_:Exception){null}
 private val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
 private val base=LinearGradient(0f,0f,1700f,1080f,intArrayOf(Color.rgb(3,16,36),Color.rgb(10,39,78),Color.rgb(5,23,48)),null,Shader.TileMode.CLAMP)
 private val readableHeader=LinearGradient(0f,0f,0f,480f,intArrayOf(Color.argb(if(sunrise)18 else 145,2,12,25),Color.argb(40,2,12,25)),null,Shader.TileMode.CLAMP)
 private val blue=RadialGradient(1250f,650f,1000f,intArrayOf(Color.argb(42,29,85,149),Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
 private val fade=LinearGradient(0f,if(sunrise)260f else 90f,0f,if(sunrise)360f else 520f,intArrayOf(Color.TRANSPARENT,Color.rgb(7,29,58)),floatArrayOf(0f,1f),Shader.TileMode.CLAMP)
 private val side=LinearGradient(0f,0f,600f,0f,intArrayOf(Color.argb(110,2,10,20),Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
 private val clockShade=RadialGradient(1850f,55f,480f,intArrayOf(Color.argb(175,7,27,61),Color.argb(130,10,32,68),Color.TRANSPARENT),floatArrayOf(0f,.38f,1f),Shader.TileMode.CLAMP)
 private val atmosphere=ColorMatrixColorFilter(floatArrayOf(1.3f,0f,0f,0f,10f,0f,1.32f,0f,0f,15f,0f,0f,1.5f,0f,24f,0f,0f,0f,1f,0f))
 override fun onDraw(c:Canvas){
  c.save();c.scale(width/1920f,height/1080f)
  p.shader=base;c.drawRect(0f,0f,1920f,1080f,p);p.shader=null
  if(living){
   drawsForTest++
   val fading=previous
   if(fading!=null){fadePaint.alpha=255;c.drawBitmap(fading,null,headerArea,fadePaint)}
   scene?.let{fadePaint.alpha=if(fading!=null)BackdropFade.alpha(step) else 255;c.drawBitmap(it,null,headerArea,fadePaint)}
  }
  val mountains=this.mountains
  if(!living && mountains!=null){
  if(cropped)c.drawBitmap(mountains,null,headerArea,p)
  else if(sunrise){
   // Crop the panorama to the header without stretching; keep the sun above the cards.
   val cropH=(mountains.width*360f/1816f).toInt().coerceAtMost(mountains.height)
   val top=(mountains.height-cropH)/2
   c.drawBitmap(mountains,Rect(0,top,mountains.width,top+cropH),RectF(104f,0f,1920f,360f),p)
  }else{p.colorFilter=atmosphere;c.drawBitmap(mountains,null,RectF(104f,0f,1920f,480f),p);p.colorFilter=null}
  }
  if(sunrise){p.shader=clockShade;c.drawRect(104f,0f,1920f,480f,p)}
  p.shader=readableHeader;c.drawRect(104f,0f,1920f,480f,p)

  p.shader=fade;c.drawRect(104f,90f,1920f,550f,p)
  p.shader=blue;c.drawRect(104f,350f,1920f,1080f,p)
  p.shader=side;c.drawRect(0f,0f,1000f,1080f,p);p.shader=null;c.restore()
 }
}
