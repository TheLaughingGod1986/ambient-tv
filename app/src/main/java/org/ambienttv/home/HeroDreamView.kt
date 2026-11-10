package org.ambienttv.home

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

interface SaverDisplay {
 val motionEnabled:Boolean
 val backgroundDraws:Int
 fun startBackground()
 fun updateHeader()
 fun show(bitmap:Bitmap)
 fun clear()
}
/** Reference-inspired single hero. Original photo is composed around its detected face without distortion. */
internal class HeroDreamView(c:Context,private val previewPanel:WeatherPanel?=null):View(c),SaverDisplay {
 private companion object { const val HEADER_LEFT=82f } // the same left margin as the photo captions
 private var dimMinute=-1L;private var dimmed=false
 /** Evening dimming is drawn over the finished frame, so every layout gets it the same way. */
 override fun draw(canvas:Canvas){
  super.draw(canvas)
  val now=System.currentTimeMillis();if(now/60000!=dimMinute){dimMinute=now/60000;dimmed=DimPolicy.active(now,AppPreferences.dimEvening(context))}
  if(dimmed)canvas.drawColor(Color.argb(DimPolicy.ALPHA,0,0,0))
 }
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
 private val normal=Typeface.create("sans-serif-light",Typeface.NORMAL)
 private val currentRect=RectF()
 private val previousRect=RectF()
 private val backdrop=RectF()
 private val thumbRects=Array(3) {i->RectF(980f+i*300,664f,1260f+i*300,940f)}
 private val clip=Path()
 private val thumbImageRects=Array(3){RectF()}
 private val scrim=Paint().apply {shader=LinearGradient(900f,0f,1920f,0f,intArrayOf(Color.TRANSPARENT,Color.argb(170,2,9,16)),null,Shader.TileMode.CLAMP)}
 private val topScrim=Paint().apply {shader=LinearGradient(0f,0f,0f,330f,intArrayOf(Color.argb(150,2,9,16),Color.TRANSPARENT),null,Shader.TileMode.CLAMP)}
 private val bottomScrim=Paint().apply {shader=LinearGradient(0f,670f,0f,1080f,intArrayOf(Color.TRANSPARENT,Color.argb(120,2,9,16)),null,Shader.TileMode.CLAMP)}
 private var current:Bitmap?=null
 private var previous:Bitmap?=null
 private var edge:Shader?=null
 private var oldEdge:Shader?=null
 private var soft:Bitmap?=null
 private var oldSoft:Bitmap?=null
 private var thumbs:List<Bitmap> = emptyList()
 private var thumbColors:IntArray=intArrayOf()
 private var selected=0
 private var dotsLeft=1374.5f
 private var changedAt=0L
 private var minute=-1L
 private var panel=previewPanel ?: WeatherStore.panel(c)
 private val timeFormat=SimpleDateFormat("HH:mm",Locale.getDefault()).apply {timeZone=TimeZone.getDefault()}
 private val dateFormat=SimpleDateFormat("EEE, d MMM",Locale.getDefault()).apply {timeZone=TimeZone.getDefault()}
 private var timeText=timeFormat.format(Date())
 private var dateText=dateFormat.format(Date())
 override var motionEnabled=false;private set
 override var backgroundDraws=0;private set
 private val tick=object:Runnable {override fun run(){if(motionEnabled){invalidate();postDelayed(this,200)}}}
 override fun startBackground(){
  motionEnabled=try {MutedPalette.motionAllowed(android.provider.Settings.Global.getFloat(context.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f),(context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isPowerSaveMode)}catch(_:Exception){false}
  if(motionEnabled)post(tick)
 }
 override fun updateHeader(){val now=System.currentTimeMillis()/60000;if(now!=minute){minute=now;timeText=timeFormat.format(Date());dateText=dateFormat.format(Date());panel=previewPanel ?: WeatherStore.panel(context);invalidate()}}
 override fun show(bitmap:Bitmap){showHero(bitmap,emptyList(),0)}
 private var captions=emptyList<HeroCaption>()
 /** [selection] marks one preview as the photo on screen; a negative value means the strip is what comes next. */
 fun showHero(bitmap:Bitmap,previews:List<Bitmap>,selection:Int,face:FaceRegion?=null,background:Bitmap?=null,framing:String="auto",captions:List<HeroCaption> = emptyList()){
  this.captions=captions
  previous?.recycle();oldSoft?.recycle()
  previous=if(motionEnabled)current else null;oldSoft=if(motionEnabled)soft else null;oldEdge=if(motionEnabled)edge else null
  if(!motionEnabled){current?.recycle();soft?.recycle()}
  previousRect.set(currentRect)
  current=bitmap
  soft=background ?: HeroBackdrop.create(bitmap,face)
  val fit=FramingPolicy.place(bitmap.width,bitmap.height,face,framing)
  currentRect.set(fit.left,fit.top,fit.left+fit.width,fit.top+fit.height)
  edge=edgeShader(bitmap,currentRect,face)
  context.getSharedPreferences("dream-state",0).edit().putBoolean("faceComposition",face!=null).putInt("heroWidth",fit.width.toInt()).apply()
  thumbs.forEach {it.recycle()};thumbs=previews;thumbColors=IntArray(previews.size){MutedPalette.muted(WeatherRenderer.sampleColor(previews[it]),8)}
  val count=minOf(3,previews.size);val left=1860f-(count*280+(count-1).coerceAtLeast(0)*20)
  dotsLeft=(left+1860)/2-(count*23+(count-1).coerceAtLeast(0)*11)/2f
  for(i in 0 until count){thumbRects[i].offsetTo(left+i*300,664f)}
  for(i in 0 until count){val r=thumbRects[i];val zoom=maxOf(r.width()/previews[i].width,r.height()/previews[i].height);val w=previews[i].width*zoom;val h=previews[i].height*zoom;thumbImageRects[i].set(r.centerX()-w/2,r.centerY()-h/2,r.centerX()+w/2,r.centerY()+h/2)}
  selected=if(selection<0)-1 else selection.coerceIn(0,(previews.size-1).coerceAtLeast(0));changedAt=SystemClock.elapsedRealtime();invalidate()
 }
 private fun edgeShader(bitmap:Bitmap,rect:RectF,face:FaceRegion?):Shader? {
  val protectRight=face?.let{rect.left+it.right*rect.width()/bitmap.width}
  val transition=HeroFadePolicy.transition(rect.width(),rect.right,protectRight=protectRight) ?: return null
  val image=LinearGradient(0f,rect.top,0f,rect.bottom,HeroBackdrop.edgeColors(bitmap),null,Shader.TileMode.CLAMP)
  val peak=(transition.peak-transition.start)/(transition.end-transition.start)
  val mask=LinearGradient(transition.start,0f,transition.end,0f,intArrayOf(Color.TRANSPARENT,Color.WHITE,Color.TRANSPARENT),floatArrayOf(0f,peak,1f),Shader.TileMode.CLAMP)
  return ComposeShader(image,mask,PorterDuff.Mode.DST_IN)
 }
 override fun clear(){motionEnabled=false;removeCallbacks(tick);previous?.recycle();previous=null;edge=null;oldEdge=null;current?.recycle();current=null;soft?.recycle();soft=null;oldSoft?.recycle();oldSoft=null;thumbs.forEach{it.recycle()};thumbs=emptyList()}
 override fun onDetachedFromWindow(){motionEnabled=false;removeCallbacks(tick);super.onDetachedFromWindow()}
 override fun onDraw(canvas:Canvas){
  super.onDraw(canvas);canvas.drawColor(Color.rgb(5,14,24));val scale=min(width/1920f,height/1080f)
  canvas.save();canvas.translate((width-1920*scale)/2,(height-1080*scale)/2);canvas.scale(scale,scale)
  val age=SystemClock.elapsedRealtime()-changedAt;val fraction=(age/500f).coerceIn(0f,1f)
  if(fraction>=1){previous?.recycle();previous=null;oldSoft?.recycle();oldSoft=null;oldEdge=null}
  val drift=if(motionEnabled)kotlin.math.sin(SystemClock.elapsedRealtime()/120000.0*2*Math.PI).toFloat()*8 else 0f
  if(motionEnabled)backgroundDraws++
  fun drawLayer(photo:Bitmap?,blur:Bitmap?,rect:RectF,blend:Shader?,alpha:Int){
   if(photo==null)return
   paint.alpha=alpha
   val background=blur ?: photo
   val zoom=maxOf(1952f/background.width,1100f/background.height);val bw=background.width*zoom;val bh=background.height*zoom
   backdrop.set(960-bw/2+drift,540-bh/2,960+bw/2+drift,540+bh/2)
   canvas.drawBitmap(background,null,backdrop,paint)
   // A deep tint turns the blurred backdrop into a calm margin instead of a bright smear.
   paint.color=Color.argb(alpha*120/255,0,6,12);canvas.drawRect(0f,0f,1920f,1080f,paint)
   paint.alpha=alpha
   canvas.drawBitmap(photo,null,rect,paint)
   if(blend!=null){
    val transition=HeroFadePolicy.transition(rect.width(),rect.right)!!
    paint.shader=blend;canvas.drawRect(transition.start,rect.top,transition.end,rect.bottom,paint);paint.shader=null
   }
  }
  drawLayer(previous,oldSoft,previousRect,oldEdge,255)
  drawLayer(current,soft,currentRect,edge,if(previous==null)255 else (fraction*255).toInt())
  // The side scrim only backs the optional preview strip; full-screen photos stay clear.
  paint.alpha=255;if(thumbs.isNotEmpty())canvas.drawRect(0f,0f,1920f,1080f,scrim);canvas.drawRect(0f,0f,1920f,1080f,bottomScrim)
  // Header row as on Home: weather in the top-left corner, clock in the top-right.
  canvas.drawRect(0f,0f,1920f,330f,topScrim)
  paint.setShadowLayer(10f,0f,2f,Color.argb(150,0,0,0))
  paint.typeface=normal;paint.color=Color.WHITE;paint.textAlign=Paint.Align.RIGHT;paint.textSize=112f;canvas.drawText(timeText,1860f,172f,paint)
  paint.textSize=40f;canvas.drawText(dateText,1860f,228f,paint)
  paint.textAlign=Paint.Align.LEFT
  if(AppPreferences.weatherEnabled(context)){
   paint.textSize=22f;canvas.drawText(AppPreferences.weatherLabel(context).uppercase(),HEADER_LEFT,92f,paint)
   paint.textSize=76f;canvas.drawText(panel.temperature,HEADER_LEFT-4f,172f,paint)
   val iconX=HEADER_LEFT+paint.measureText(panel.temperature)+62f
   paint.clearShadowLayer();WeatherRenderer.drawConditionIcon(canvas,panel,iconX,146f,.52f,paint);paint.setShadowLayer(10f,0f,2f,Color.argb(150,0,0,0))
   paint.typeface=normal;paint.textAlign=Paint.Align.LEFT;paint.color=Color.WHITE;paint.textSize=32f
   val room=(1500f-(iconX+64f)).coerceAtLeast(120f)
   canvas.drawText(android.text.TextUtils.ellipsize(panel.condition,android.text.TextPaint(paint),room,android.text.TextUtils.TruncateAt.END).toString(),iconX+64f,160f,paint)
   val range=panel.range
   if(range!=null){paint.textSize=24f;paint.color=Color.rgb(222,231,240);canvas.drawText(android.text.TextUtils.ellipsize(range,android.text.TextPaint(paint),520f,android.text.TextUtils.TruncateAt.END).toString(),HEADER_LEFT,222f,paint)}
   paint.textSize=12f;paint.color=Color.rgb(210,222,231);canvas.drawText("open-meteo.com · CC BY 4.0",HEADER_LEFT,if(range!=null)250f else 216f,paint)
  }
  paint.clearShadowLayer()
  paint.textAlign=Paint.Align.LEFT
  // The photo's own caption: when it was taken and, with a birthday saved, the age then. Nothing when unknown.
  paint.setShadowLayer(10f,0f,2f,Color.argb(150,0,0,0))
  for(item in captions){
   val (whenTaken,age)=item.lines ?: continue
   val x=item.left+82f
   paint.typeface=normal;paint.color=Color.WHITE;paint.textSize=54f;canvas.drawText(whenTaken,x,if(age!=null)902f else 940f,paint)
   if(age!=null){paint.textSize=30f;paint.color=Color.rgb(222,231,240);canvas.drawText(age,x+2f,950f,paint)}
   paint.color=Color.WHITE;paint.strokeWidth=2f;canvas.drawLine(x+2f,978f,x+54f,978f,paint)
  }
  paint.clearShadowLayer()
  if(selected<0 && thumbs.isNotEmpty()){paint.typeface=normal;paint.textSize=18f;paint.color=Color.rgb(210,222,231);canvas.drawText("U P   N E X T",thumbRects[0].left+2f,646f,paint)}
  for(i in 0 until minOf(3,thumbs.size)){
   val r=thumbRects[i];clip.reset();clip.addRoundRect(r,14f,14f,Path.Direction.CW);canvas.save();canvas.clipPath(clip)
   paint.color=thumbColors[i];canvas.drawRect(r,paint)
   val photo=thumbs[i];backdrop.set(thumbImageRects[i])
   canvas.drawBitmap(photo,null,backdrop,paint);canvas.restore()
   paint.style=Paint.Style.STROKE;paint.strokeWidth=if(i==selected)3f else 1.5f;paint.color=if(i==selected)Color.rgb(255,236,204)else Color.argb(150,233,242,250);canvas.drawRoundRect(r,14f,14f,paint);paint.style=Paint.Style.FILL
   if(selected>=0){paint.color=if(i==selected)Color.WHITE else Color.argb(100,240,248,255);canvas.drawRoundRect(dotsLeft+i*34,970f,dotsLeft+23+i*34,978f,4f,4f,paint)}
  }
  if(current==null){paint.textAlign=Paint.Align.CENTER;paint.typeface=normal;paint.textSize=32f;paint.color=Color.WHITE;canvas.drawText("No cached photos available",700f,540f,paint)}
  canvas.restore();if(previous!=null)postInvalidateDelayed(100)
 }
}
