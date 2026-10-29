package org.ambienttv.home
import android.content.Context
import android.graphics.*
import android.view.View
/** Quiet native ambience using the existing mountain photograph; no animation. */
internal class HomeBackdropView(c:Context,private val sunrise:Boolean=false):View(c) {
 private var mountains:Bitmap?=null
 init {
  // Decode the static wallpaper without blocking the first focusable frame.
  Thread({val decoded=BitmapFactory.decodeResource(resources,if(sunrise)R.drawable.home_sunrise else R.drawable.home_mountains);post{mountains=decoded;invalidate()}},"tv-wallpaper").apply{isDaemon=true;start()}
 }
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
  val mountains=this.mountains
  if(mountains!=null){
  if(sunrise){
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
