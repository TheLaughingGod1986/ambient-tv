package org.ambienttv.home

import android.graphics.*
import kotlin.math.*

/**
 * Paints the still part of the Home sky into one header-sized bitmap: sky, sun or moon, stars, cloud deck,
 * mountain ranges, valley mist and forest. It runs on a background thread only when the scene changes,
 * so focus movement and scrolling never wait for it. Everything is drawn in code; no images are bundled.
 */
internal object SkyRenderer {
 /** The strip behind the header, in the 1920x1080 design frame. Only the top ~245 rows are visible above the cards. */
 const val LEFT=104f; const val WIDTH=1816f; const val HEIGHT=360f
 const val VISIBLE=248f
 /** Header text (left) and clock (right) in strip coordinates; clouds thin out behind them. */
 val textZones=listOf(RectF(120f,20f,780f,240f),RectF(1340f,20f,1800f,170f))

 private class Ridge(val seed:Long,val base:Float,val amplitude:Float,val frequency:Double,val jag:Float,val peaks:List<Triple<Float,Float,Float>>,val trees:IntRange?)
 private val ridges=listOf(
  // Distant ranges along the horizon, palest in the haze.
  Ridge(11,206f,10f,.004,7f,listOf(Triple(820f,16f,220f),Triple(1180f,26f,240f),Triple(1420f,40f,190f),Triple(1620f,52f,170f),Triple(1790f,36f,150f)),null),
  // The alpine massif rises on the left behind the header text, as in the original photograph.
  Ridge(23,214f,8f,.004,30f,listOf(Triple(130f,150f,210f),Triple(300f,126f,150f),Triple(470f,108f,170f),Triple(640f,84f,160f),Triple(800f,58f,170f),Triple(960f,28f,150f)),null),
  Ridge(37,226f,16f,.006,6f,listOf(Triple(40f,40f,300f),Triple(1120f,30f,260f),Triple(1500f,22f,200f)),null),
  Ridge(41,239f,16f,.007,2f,listOf(Triple(110f,62f,430f),Triple(1300f,14f,300f)),7..15),
  Ridge(53,260f,14f,.008,2f,listOf(Triple(0f,88f,540f),Triple(1816f,40f,420f)),12..30))

 private class Noise(seed:Long) {
  private val v=DoubleArray(512).also{val r=java.util.Random(seed);for(i in it.indices)it[i]=r.nextDouble()}
  private fun at(x:Double):Double{val i=floor(x).toInt();val f=x-i;val s=f*f*(3-2*f);return v[i and 511]*(1-s)+v[(i+1) and 511]*s}
  fun fbm(x:Double,octaves:Int):Double{var sum=0.0;var amp=.5;var f=1.0;repeat(octaves){sum+=amp*at(x*f);f*=2.0;amp*=.5};return sum}
  /** Sharp crests for alpine ridgelines. */
  fun ridged(x:Double,octaves:Int):Double{var sum=0.0;var amp=.5;var f=1.0;repeat(octaves){sum+=amp*(1-abs(2*at(x*f)-1));f*=2.1;amp*=.5};return sum}
 }

 /** Heights of a ridge every 3 design pixels; the same every time, so redraws never reshape the mountains. */
 private const val STEP=3f
 private fun heights(r:Ridge):FloatArray {
  val noise=Noise(r.seed);val steps=(WIDTH/STEP).toInt()+3
  return FloatArray(steps){i->
   val x=i*STEP
   var lift=r.amplitude*(noise.fbm(x*r.frequency,4)-.5f).toFloat()*2f
   var peak=0f
   for((px,h,w) in r.peaks){val d=abs(x-px)/w;if(d<1f)peak=max(peak,h*(1f-d).pow(1.2f))}
   lift+=peak
   // Rocky detail grows with height, so summits are jagged and valleys smooth.
   lift+=r.jag*(.25f+peak/110f)*(noise.ridged(x*.021,5).toFloat()-.5f)
   r.base-lift
  }
 }
 private val profiles by lazy { ridges.map{heights(it)} }
 private fun path(h:FloatArray):Path=Path().apply{moveTo(0f,HEIGHT);h.forEachIndexed{i,y->lineTo(i*STEP,y)};lineTo(WIDTH+8,HEIGHT);close()}

 /** [fadeBottom] lets the forest fade out at the bottom edge, for the screensaver where the strip sits over a backdrop. */
 fun render(scene:SkyScene,look:SkyLook,unit:Float,fadeBottom:Boolean=false):Bitmap {
  val bitmap=Bitmap.createBitmap(max(1,(WIDTH*unit).roundToInt()),max(1,(HEIGHT*unit).roundToInt()),Bitmap.Config.ARGB_8888)
  val c=Canvas(bitmap);c.scale(unit,unit)
  val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
  // Sky.
  p.shader=LinearGradient(0f,0f,0f,232f,intArrayOf(look.skyTop,look.skyMiddle,look.skyHorizon),floatArrayOf(0f,.52f,1f),Shader.TileMode.CLAMP)
  c.drawRect(0f,0f,WIDTH,HEIGHT,p);p.shader=null
  val sunX=(scene.sunX*WIDTH).toFloat();val sunY=(40+scene.sunY*181).toFloat()
  // A broad glow along the horizon, centred under the sun, then a closer halo.
  if(look.glowStrength>.02){
   c.save();c.scale(2.6f,1f,sunX,210f)
   p.shader=RadialGradient(sunX,210f,330f,intArrayOf(alpha(look.glow,look.glowStrength*.85),alpha(look.glow,look.glowStrength*.25),Color.TRANSPARENT),floatArrayOf(0f,.45f,1f),Shader.TileMode.CLAMP)
   c.drawRect(-2000f,0f,WIDTH+2000f,HEIGHT,p);c.restore()
  }
  if(look.stars>.02)stars(c,p,look)
  if(look.sunVisible>.05 && !scene.night){
   p.shader=RadialGradient(sunX,sunY,120f,intArrayOf(alpha(look.sun,.55*look.sunVisible),alpha(look.sun,.12*look.sunVisible),Color.TRANSPARENT),floatArrayOf(0f,.3f,1f),Shader.TileMode.CLAMP)
   c.drawCircle(sunX,sunY,120f,p);p.shader=null
   p.color=alpha(look.sun,look.sunVisible.coerceAtMost(1.0));c.drawCircle(sunX,sunY,13f,p)
  }
  if(scene.night && look.cloudCover<.9)moon(c,p,scene,look)
  if(look.cloudCover>.6)cloudDeck(c,p,look)
  // Ranges from far to near, with mist settling in each valley.
  val paths=profiles.map{path(it)}
  paths.forEachIndexed{i,path->
   p.color=look.ridges[i];c.drawPath(path,p)
   if(i==1 && look.snowCaps>.05)snow(c,p,path,look)
   if(i<paths.size-1 && look.mist>.02){
    val top=ridges[i].base-40;val bottom=ridges[i+1].base+6
    p.shader=LinearGradient(0f,top,0f,bottom,Color.TRANSPARENT,alpha(look.haze,(look.mist*(.62-.1*i)).coerceIn(0.0,.8)),Shader.TileMode.CLAMP)
    c.drawRect(0f,top,WIDTH,bottom,p);p.shader=null
   }
   ridges[i].trees?.let{trees(c,p,profiles[i],ridges[i].seed,it,look.ridges[i])}
  }
  // Bright skies get a soft shade behind the header text and clock, so white text stays readable.
  val shade=((look.brightness-.22)*1.5).coerceIn(0.0,.55)
  if(shade>0){
   for((x,y,rx) in listOf(Triple(420f,110f,560f),Triple(1590f,98f,330f))){
    c.save();c.scale(1f,.42f,x,y)
    p.shader=RadialGradient(x,y,rx,intArrayOf(Color.argb((shade*255).toInt(),4,14,30),Color.argb((shade*120).toInt(),4,14,30),Color.TRANSPARENT),floatArrayOf(0f,.55f,1f),Shader.TileMode.CLAMP)
    c.drawCircle(x,y,rx,p);c.restore()
   }
   p.shader=null
  }
  if(fadeBottom){
   p.xfermode=PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
   p.shader=LinearGradient(0f,190f,0f,HEIGHT,Color.TRANSPARENT,Color.BLACK,Shader.TileMode.CLAMP);c.drawRect(0f,190f,WIDTH,HEIGHT,p)
   p.xfermode=null;p.shader=null
  }
  return bitmap
 }

 private fun alpha(color:Int,a:Double)=(color and 0xffffff) or ((a.coerceIn(0.0,1.0)*255).toInt() shl 24)
 private fun alpha(color:Int,a:Float)=alpha(color,a.toDouble())
 private fun stars(c:Canvas,p:Paint,look:SkyLook){
  val r=java.util.Random(7);p.style=Paint.Style.FILL
  repeat(170){
   val x=r.nextFloat()*WIDTH;val y=r.nextFloat().pow(1.5f)*175f
   val size=.6f+r.nextFloat()*1.3f
   p.color=Color.argb((look.stars*(70+r.nextInt(150))).toInt().coerceIn(0,255),235,240,255)
   c.drawCircle(x,y,size,p)
  }
 }
 private fun moon(c:Canvas,p:Paint,scene:SkyScene,look:SkyLook){
  val x=WIDTH*.585f;val y=78f;val radius=17f;val visible=(1-look.cloudCover*.8)
  p.shader=RadialGradient(x,y,110f,intArrayOf(Color.argb((70*visible).toInt(),190,205,235),Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
  c.drawCircle(x,y,110f,p);p.shader=null
  // Only the lit part is drawn: the disc minus an offset disc, so the sky behind stays the sky.
  val phase=scene.moonPhase
  if(phase<.03 || phase>.97)return
  val disc=Path().apply{addCircle(x,y,radius,Path.Direction.CW)}
  if(abs(phase-.5)>.02){
   val offset=if(phase<.5)radius*4f*phase.toFloat() else -radius*4f*(1-phase).toFloat()
   disc.op(Path().apply{addCircle(x-offset,y,radius*1.02f,Path.Direction.CW)},Path.Op.DIFFERENCE)
  }
  p.color=Color.argb((235*visible).toInt(),236,240,250);c.drawPath(disc,p)
 }
 /** A full cover: soft layered bands of cloud, denser at the top, so the sky reads as grey weather, not fog. */
 private fun cloudDeck(c:Canvas,p:Paint,look:SkyLook){
  val r=java.util.Random(19)
  for(row in 0..3) for(i in 0..6){
   val w=420f+r.nextFloat()*360f;val x=i*300f-200f+r.nextFloat()*160f+row*70f;val y=10f+row*38f+r.nextFloat()*18f
   val tone=if(row%2==0)look.cloud else look.cloudShade
   band(c,p,x,y,w,46f+r.nextFloat()*30f,tone,(.42f-row*.06f)*look.cloudCover.toFloat())
  }
 }
 /** One stretched, soft-edged cloud band. */
 private fun band(c:Canvas,p:Paint,x:Float,y:Float,w:Float,h:Float,color:Int,opacity:Float){
  c.save();c.scale(w/h,1f,x,y)
  p.shader=RadialGradient(x,y,h/2,intArrayOf(alpha(color,opacity),alpha(color,opacity*.55f),Color.TRANSPARENT),floatArrayOf(0f,.55f,1f),Shader.TileMode.CLAMP)
  c.drawCircle(x,y,h/2,p);p.shader=null;c.restore()
 }
 private fun snow(c:Canvas,p:Paint,ridge:Path,look:SkyLook){
  // Snow only on the summits: the crest is brushed white above a snowline, fading out below it.
  c.save();c.clipPath(ridge);c.clipRect(0f,0f,WIDTH,170f)
  val tone=SkyColor.mix(look.skyHorizon,Color.WHITE,.72)
  p.style=Paint.Style.STROKE;p.strokeJoin=Paint.Join.ROUND
  p.shader=LinearGradient(0f,60f,0f,165f,alpha(tone,.75*look.snowCaps),Color.TRANSPARENT,Shader.TileMode.CLAMP);p.strokeWidth=12f;c.drawPath(ridge,p)
  p.shader=LinearGradient(0f,60f,0f,150f,alpha(tone,.30*look.snowCaps),Color.TRANSPARENT,Shader.TileMode.CLAMP);p.strokeWidth=40f;c.drawPath(ridge,p)
  p.shader=null;p.style=Paint.Style.FILL;c.restore()
 }
 private fun trees(c:Canvas,p:Paint,h:FloatArray,seed:Long,size:IntRange,color:Int){
  val r=java.util.Random(seed*31);p.color=color;val tree=Path()
  var x=r.nextFloat()*6f
  while(x<WIDTH){
   val i=(x/STEP).toInt().coerceIn(0,h.size-1);val base=h[i]+4f
   if(base<VISIBLE+10){
    val height=size.first+r.nextFloat()*(size.last-size.first);val half=height*.2f
    tree.reset();tree.moveTo(x,base-height)
    // Three tiers of branches on each side.
    for(k in 1..3){val y=base-height+height*k/3.4f;tree.lineTo(x+half*k/2.2f+half*.35f,y);tree.lineTo(x+half*k/3.6f,y-height*.06f)}
    tree.lineTo(x+half*.25f,base+6f);tree.lineTo(x-half*.25f,base+6f)
    for(k in 3 downTo 1){val y=base-height+height*k/3.4f;tree.lineTo(x-half*k/3.6f,y-height*.06f);tree.lineTo(x-half*k/2.2f-half*.35f,y)}
    tree.close();c.drawPath(tree,p)
   }
   x+=size.first*.45f+r.nextFloat()*size.first*.9f
  }
 }

 /** Design size of each sprite kind; sprites are rendered at half resolution because soft shapes do not need more. */
 fun spriteSize(weather:SkyWeather)=when{weather==SkyWeather.FOG->900f to 110f;weather.rain || weather==SkyWeather.OVERCAST || weather==SkyWeather.SNOW->720f to 150f;else->420f to 210f}
 fun sprites(scene:SkyScene,look:SkyLook,unit:Float):List<Bitmap> {
  val half=unit*.5f;val p=Paint(Paint.ANTI_ALIAS_FLAG)
  val (w,h)=spriteSize(scene.weather)
  return (0..3).map{i->
   Bitmap.createBitmap(max(1,(w*half).toInt()),max(1,(h*half).toInt()),Bitmap.Config.ARGB_8888).also{b->
    val c=Canvas(b);c.scale(half,half)
    when{
     scene.weather==SkyWeather.FOG->band(c,p,w/2,h/2,w*.92f,h*.8f,look.haze,.7f)
     w==720f->stratus(c,p,w,h,31L+i,look)
     else->cumulus(c,p,w,h,41L+i,look)
    }
   }
  }
 }
 /** A fair-weather cumulus: rounded, sunlit top and a flat, shaded base. */
 private fun cumulus(c:Canvas,p:Paint,w:Float,h:Float,seed:Long,look:SkyLook){
  val r=java.util.Random(seed);val base=h*.74f;val cx=w/2
  val blobs=mutableListOf<FloatArray>()
  for(k in 0 until 5){val rad=26f+r.nextFloat()*16f;blobs.add(floatArrayOf(cx+(k-2)*w*.14f+r.nextFloat()*12f,base-rad*.45f,rad))}
  for(k in 0 until 3){val rad=40f+r.nextFloat()*18f;blobs.add(floatArrayOf(cx+(k-1)*w*.16f+r.nextFloat()*14f,base-48f-r.nextFloat()*12f,rad))}
  val topRad=36f+r.nextFloat()*16f;blobs.add(floatArrayOf(cx+(r.nextFloat()-.5f)*w*.18f,base-86f-r.nextFloat()*10f,topRad))
  // A continuous body under the puffs, so the base is one soft line rather than a row of bubbles.
  band(c,p,cx,base-20f,w*.78f,58f,look.cloud,.92f)
  for(b in blobs){
   p.shader=RadialGradient(b[0],b[1],b[2],intArrayOf(alpha(look.cloud,.92f),alpha(look.cloud,.7f),Color.TRANSPARENT),floatArrayOf(0f,.5f,1f),Shader.TileMode.CLAMP)
   c.drawCircle(b[0],b[1],b[2],p)
  }
  // Shade the lower half towards the base colour, then soften the bottom into a flat base.
  p.xfermode=PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
  p.shader=LinearGradient(0f,base-95f,0f,base,Color.TRANSPARENT,alpha(look.cloudShade,.85f),Shader.TileMode.CLAMP);c.drawRect(0f,0f,w,h,p)
  p.xfermode=PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
  p.shader=LinearGradient(0f,base-4f,0f,base+16f,Color.TRANSPARENT,Color.BLACK,Shader.TileMode.CLAMP);c.drawRect(0f,0f,w,h,p)
  p.xfermode=null;p.shader=null
 }
 /** A ragged grey stratus patch for rainy and overcast skies: many soft bands, darker underneath. */
 private fun stratus(c:Canvas,p:Paint,w:Float,h:Float,seed:Long,look:SkyLook){
  val r=java.util.Random(seed)
  for(k in 0 until 11){
   // Every band stays inside the sprite, so no edge is ever cut straight.
   val bw=w*(.25f+r.nextFloat()*.35f);val bh=h*(.28f+r.nextFloat()*.25f)
   val x=bw/2+r.nextFloat()*(w-bw);val y=bh/2+h*.08f+r.nextFloat()*(h*.84f-bh)
   band(c,p,x,y,bw,bh,look.cloud,.55f+r.nextFloat()*.25f)
  }
  p.xfermode=PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
  p.shader=LinearGradient(0f,h*.3f,0f,h*.85f,Color.TRANSPARENT,alpha(look.cloudShade,.8f),Shader.TileMode.CLAMP);c.drawRect(0f,0f,w,h,p)
  p.xfermode=null;p.shader=null
 }
}
