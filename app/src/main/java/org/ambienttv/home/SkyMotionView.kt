package org.ambienttv.home

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import kotlin.math.*

/**
 * The moving part of the Home sky, drawn above the still scene and below every control.
 * It redraws at a low, capped frame rate, only while Home is in front, and skips a frame or two
 * whenever the remote is used so focus movement always has the whole frame budget.
 */
internal class SkyMotionView(c:Context):View(c) {
 private var scene:SkyScene?=null
 private var look:SkyLook?=null
 private var sprites:List<Bitmap> = emptyList()
 private val random=java.util.Random(5)
 private var running=false
 private var quietUntil=0L
 private var lastFrame=0L
 private var clock=0.0
 private var frameMs=66L
 private var lastActivity=0L
 private var windDrift=1f
 private var slant=.17f
 private var snowWind=6f

 private class Drift(var x:Float,val y:Float,val speed:Float,val scale:Float,val alpha:Float,val sprite:Int){var width=600f}
 private val target=RectF()
 private val filled=IntArray(3)
 private val drifts=mutableListOf<Drift>()
 // Raindrops and snowflakes as parallel arrays: no per-frame allocation.
 private var count=0
 private var px=FloatArray(0);private var py=FloatArray(0);private var speed=FloatArray(0);private var size=FloatArray(0);private var phase=FloatArray(0)
 private var near=FloatArray(0);private var far=FloatArray(0)
 private var snowPoints=Array(3){FloatArray(0)}
 private var nextLightning=0L;private var lightningAt=-1L
 private var nextBirds=0L;private var birdsAt=-1L;private var birdY=0f
 private val twinkle=FloatArray(24*3)

 private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
 private val rainFar=Paint(Paint.ANTI_ALIAS_FLAG).apply{strokeWidth=1.3f;strokeCap=Paint.Cap.ROUND}
 private val rainNear=Paint(Paint.ANTI_ALIAS_FLAG).apply{strokeWidth=2.1f;strokeCap=Paint.Cap.ROUND}
 private val flake=Paint(Paint.ANTI_ALIAS_FLAG).apply{strokeCap=Paint.Cap.ROUND}
 private val bird=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=2.2f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
 private val birdPath=Path()
 private var flakeAlpha=1f
 private var flash:Shader?=null
 private val resume=Runnable{if(running)invalidate()}
 /** The one pending frame. Rescheduling it on every draw keeps exactly one redraw loop, however often show() runs. */
 private val nextFrame=Runnable{if(running)invalidate()}

 init {
  importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO;isFocusable=false;setWillNotDraw(false)
  // A GPU layer: while motion is paused (remote in use) every frame reuses one small cached texture.
  setLayerType(LAYER_TYPE_HARDWARE,null)
 }

 fun show(next:SkyScene,nextLook:SkyLook,nextSprites:List<Bitmap>){
  val previous=scene
  val changed=previous==null || previous.weather!=next.weather || previous.night!=next.night ||
   SkyPolicy.clouds(previous.weather,previous.night,previous.detail.cloudCover)!=SkyPolicy.clouds(next.weather,next.night,next.detail.cloudCover) ||
   SkyPolicy.particles(previous.weather,previous.detail.precipitationMm)!=SkyPolicy.particles(next.weather,next.detail.precipitationMm)
  scene=next;look=nextLook;sprites=nextSprites
  // Wind sets which way and how fast the clouds go, and how steeply the rain falls.
  val across=next.detail.across;windDrift=SkyPolicy.drift(across);slant=SkyPolicy.slant(across);snowWind=across?.let{(it*.9).toFloat()} ?: 6f
  frameMs=1000L/SkyPolicy.frameRate(next.weather,next.night)
  val rain=SkyColor.mix(nextLook.skyHorizon,Color.WHITE,.55)
  rainFar.color=(rain and 0xffffff) or ((if(next.night)60 else 80) shl 24)
  rainNear.color=(rain and 0xffffff) or ((if(next.night)95 else 125) shl 24)
  flake.color=Color.argb(255,245,248,255);flakeAlpha=if(next.night).72f else 1f
  bird.color=SkyColor.scale(nextLook.skyTop,.45) or (200 shl 24)
  flash=LinearGradient(0f,0f,0f,SkyRenderer.VISIBLE,Color.argb(255,226,230,255),Color.argb(0,226,230,255),Shader.TileMode.CLAMP)
  if(changed)reset(next,nextLook)
  invalidate()
 }
 fun clear(){scene=null;look=null;sprites=emptyList();drifts.clear();count=0;invalidate()}

 private fun reset(s:SkyScene,l:SkyLook){
  drifts.clear()
  val fog=s.weather==SkyWeather.FOG;val grey=s.weather.rain || s.weather==SkyWeather.OVERCAST || s.weather==SkyWeather.SNOW
  val n=SkyPolicy.clouds(s.weather,s.night,s.detail.cloudCover)
  // Spread the clouds evenly across the sky at the start, each with its own height, size and pace.
  val spacing=(SkyRenderer.WIDTH+600f)/max(1,n)
  repeat(n){i->
   val y=when{fog->160f+random.nextFloat()*60f;grey->40f+random.nextFloat()*110f;s.weather==SkyWeather.CLEAR->70f+random.nextFloat()*70f;else->60f+random.nextFloat()*100f}
   val alpha=when{fog->.7f;s.weather==SkyWeather.CLEAR->if(s.night).3f else .6f;s.weather==SkyWeather.PARTLY_CLOUDY->.95f;else->.55f+random.nextFloat()*.3f}
   val scale=when{fog->1.3f+random.nextFloat()*.5f;grey->1f+random.nextFloat()*.5f;s.weather==SkyWeather.CLEAR->.55f+random.nextFloat()*.25f;else->.7f+random.nextFloat()*.45f}
   // Higher clouds drift a little faster, which reads as depth.
   drifts.add(Drift(i*spacing-500f+random.nextFloat()*spacing*.5f,y,(if(fog)5f else 4f)+(180f-y).coerceAtLeast(0f)/30f+random.nextFloat()*3f,scale,alpha,i))
  }
  count=SkyPolicy.particles(s.weather,s.detail.precipitationMm)
  px=FloatArray(count){random.nextFloat()*(SkyRenderer.WIDTH+200f)-100f}
  py=FloatArray(count){random.nextFloat()*SkyRenderer.VISIBLE}
  val snow=s.weather==SkyWeather.SNOW
  speed=FloatArray(count){if(snow)26f+random.nextFloat()*40f else 820f+random.nextFloat()*480f}
  size=FloatArray(count){if(snow)random.nextInt(3).toFloat() else 14f+random.nextFloat()*22f}
  phase=FloatArray(count){random.nextFloat()*6.28f}
  near=FloatArray(count*4);far=FloatArray(count*4)
  snowPoints=Array(3){FloatArray(count*2)}
  val now=SystemClock.uptimeMillis()
  nextLightning=if(s.weather==SkyWeather.THUNDER)now+6_000L else 0L;lightningAt=-1L
  nextBirds=if(birdsAllowed(s))now+20_000L+random.nextInt(40_000) else 0L;birdsAt=-1L
  val star=java.util.Random(13)
  for(i in 0 until 24){twinkle[i*3]=star.nextFloat()*SkyRenderer.WIDTH;twinkle[i*3+1]=star.nextFloat().pow(1.6f)*160f;twinkle[i*3+2]=star.nextFloat()*6.28f}
 }
 private fun birdsAllowed(s:SkyScene)=(s.weather==SkyWeather.CLEAR || s.weather==SkyWeather.PARTLY_CLOUDY) && s.part!=DayPart.NIGHT && s.part!=DayPart.DUSK

 /**
  * Starts or stops redrawing. Stopped, the view keeps its last frame and costs nothing. Asking a running sky
  * to run again (Home back in view, a screensaver ending) counts as activity, so a sky that had come to rest moves.
  */
 fun setRunning(value:Boolean){
  if(running==value){if(value){lastActivity=SystemClock.uptimeMillis();invalidate()};return}
  running=value;lastFrame=0L;removeCallbacks(resume);removeCallbacks(nextFrame);lastActivity=SystemClock.uptimeMillis()
  if(value)invalidate()
 }
 /** A frame asked for while the window was hidden is never drawn, which would end the redraw loop; start it again. */
 override fun onWindowVisibilityChanged(visibility:Int){
  super.onWindowVisibilityChanged(visibility)
  if(visibility==VISIBLE && running){lastFrame=0L;invalidate()}
 }
 /**
  * A remote press gets the next frames to itself: the sky drops the frame it had planned, waits a moment and
  * carries on from where it was, so it never looks paused. Holding a direction keeps it still until the key
  * is released. A press also wakes a resting sky.
  */
 fun yieldToRemote(){
  if(!running)return
  lastActivity=SystemClock.uptimeMillis()
  quietUntil=lastActivity+SkyPolicy.YIELD_MS
  removeCallbacks(nextFrame);removeCallbacks(resume);postDelayed(resume,SkyPolicy.YIELD_MS+4)
 }

 /** Emulator checks only: frames in which the sky actually moved, whether it is running, and a way to make it rest. */
 internal var framesForTest=0;private set
 internal val runningForTest get()=running
 internal fun restForTest(){lastActivity=SystemClock.uptimeMillis()-SkyPolicy.REST_AFTER_MS;removeCallbacks(nextFrame)}
 /** Emulator gallery only: moves everything on as if [seconds] had passed, without waiting. */
 internal fun advanceForTest(seconds:Float){val now=SystemClock.uptimeMillis();repeat((seconds*30).toInt()){step(1/30f,now)};invalidate()}
 private fun step(dt:Float,now:Long){
  val s=scene ?: return
  clock+=dt
  // A cloud that has left on one side comes back fully off-screen on the other, so nothing pops into view.
  for(d in drifts){
   d.x+=d.speed*windDrift*dt
   if(windDrift>0 && d.x>SkyRenderer.WIDTH+20f)d.x=-d.width-random.nextFloat()*240f
   if(windDrift<0 && d.x+d.width< -20f)d.x=SkyRenderer.WIDTH+random.nextFloat()*240f
  }
  val snow=s.weather==SkyWeather.SNOW
  for(i in 0 until count){
   if(snow){py[i]+=speed[i]*dt;px[i]+=(sin(clock*.8+phase[i]).toFloat()*14f+snowWind)*dt}
   else{py[i]+=speed[i]*dt;px[i]+=speed[i]*slant*dt}
   if(py[i]>SkyRenderer.VISIBLE+20f || px[i]>SkyRenderer.WIDTH+120f || px[i]< -120f){py[i]=-random.nextFloat()*80f-size[i];px[i]=random.nextFloat()*(SkyRenderer.WIDTH+400f)-200f}
  }
  if(nextLightning>0 && now>=nextLightning){lightningAt=now;nextLightning=now+SkyPolicy.nextLightningMs(random)}
  if(nextBirds>0 && now>=nextBirds && birdsAt<0){birdsAt=now;birdY=105f+random.nextFloat()*45f;nextBirds=now+150_000L+random.nextInt(90_000)}
  if(birdsAt>=0 && now-birdsAt>30_000L)birdsAt=-1L
 }

 override fun onDraw(canvas:Canvas){
  val s=scene ?: return;val l=look ?: return
  val now=SystemClock.uptimeMillis()
  val quiet=now<quietUntil
  // Nobody has pressed a button for a while: hold the sky still until the next press.
  val resting=SkyPolicy.resting(now,lastActivity)
  if(running && !quiet && !resting){val dt=if(lastFrame==0L)0f else ((now-lastFrame).coerceIn(0L,SkyPolicy.MAX_STEP_MS))/1000f;step(dt,now);framesForTest++}
  lastFrame=now
  val unit=width/SkyRenderer.WIDTH
  canvas.save();canvas.scale(unit,unit)
  canvas.clipRect(0f,0f,SkyRenderer.WIDTH,SkyRenderer.VISIBLE+6f)
  // Clouds or fog banks.
  for(d in drifts){
   val sprite=sprites.getOrNull(d.sprite%max(1,sprites.size)) ?: continue
   val w=sprite.width/(unit*.5f)*d.scale;val h=sprite.height/(unit*.5f)*d.scale;d.width=w
   target.set(d.x,d.y-h/2,d.x+w,d.y+h/2)
   // Clouds thin out while they pass behind the header text or the clock, so white text stays readable.
   var behind=0f
   for(zone in SkyRenderer.textZones){val overlap=max(0f,min(target.right,zone.right)-max(target.left,zone.left));behind=max(behind,overlap/w)}
   paint.alpha=(d.alpha*(1f-.6f*behind)*255).toInt();canvas.drawBitmap(sprite,null,target,paint)
  }
  paint.alpha=255
  if(l.stars>.05 && s.night){
   paint.style=Paint.Style.FILL
   for(i in 0 until 24){
    val a=(l.stars*(.35+.65*(.5+.5*sin(clock*(1.1+i%5*.37)+twinkle[i*3+2])))*230).toInt().coerceIn(0,255)
    paint.color=Color.argb(a,240,244,255);canvas.drawCircle(twinkle[i*3],twinkle[i*3+1],1.6f,paint)
   }
   paint.color=Color.WHITE
  }
  if(birdsAt>=0)birds(canvas,(now-birdsAt)/30_000f)
  if(count>0){
   if(s.weather==SkyWeather.SNOW)snow(canvas) else rain(canvas)
  }
  if(lightningAt>=0){
   val t=now-lightningAt
   // Two soft pulses over about half a second; never a hard white strobe.
   val a=when{t<90->t/90f*.30f;t<190->.30f-(t-90)/100f*.22f;t<270->.08f+(t-190)/80f*.14f;t<620->.22f*(1-(t-270)/350f);else->{lightningAt=-1L;0f}}
   if(a>0){paint.shader=flash;paint.alpha=(a*255).toInt();canvas.drawRect(0f,0f,SkyRenderer.WIDTH,SkyRenderer.VISIBLE,paint);paint.shader=null;paint.alpha=255}
  }
  canvas.restore()
  removeCallbacks(nextFrame)
  if(running){
   if(quiet){removeCallbacks(resume);postDelayed(resume,quietUntil-now+16)}
   else if(!resting)postDelayed(nextFrame,frameMs)
  }
 }

 private fun rain(c:Canvas){
  var n=0;var f=0
  for(i in 0 until count){
   val len=size[i];val x=px[i];val y=py[i]
   val fade=((SkyRenderer.VISIBLE-y)/50f).coerceIn(0f,1f)
   if(fade<=0f)continue
   val tail=len*fade
   if(i%3==0){near[n]=x;near[n+1]=y;near[n+2]=x-tail*slant;near[n+3]=y-tail;n+=4}
   else{far[f]=x;far[f+1]=y;far[f+2]=x-tail*.75f*slant;far[f+3]=y-tail*.75f;f+=4}
  }
  c.drawLines(far,0,f,rainFar);c.drawLines(near,0,n,rainNear)
 }
 private fun snow(c:Canvas){
  filled.fill(0)
  for(i in 0 until count){val k=size[i].toInt().coerceIn(0,2);val a=snowPoints[k];a[filled[k]]=px[i];a[filled[k]+1]=py[i];filled[k]+=2}
  // Three flake sizes: the large ones read as near, soft snow rather than stars.
  for(k in 0..2){flake.strokeWidth=3.4f+k*2.4f;flake.alpha=((if(k==2)235 else 150+k*40)*flakeAlpha).toInt();c.drawPoints(snowPoints[k],0,filled[k],flake)}
 }
 private fun birds(c:Canvas,t:Float){
  if(t>1f)return
  val baseX=SkyRenderer.WIDTH*(.38f+.5f*t)
  for(i in 0 until 4){
   val x=baseX-i*26f-(i%2)*8f;val y=birdY+i*7f+sin(clock*1.3+i).toFloat()*3f
   val flap=sin(clock*7+i*1.7).toFloat()*4f;val w=8f
   birdPath.reset();birdPath.moveTo(x-w,y-2f-flap);birdPath.quadTo(x-w*.4f,y-3f-flap*.5f,x,y);birdPath.quadTo(x+w*.4f,y-3f-flap*.5f,x+w,y-2f-flap)
   c.drawPath(birdPath,bird)
  }
 }
}
