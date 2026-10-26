package org.ambienttv.home
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.Shader

/** Bounded, light backdrop smoothing on the existing image worker, once per photo. */
internal object HeroBackdrop {
 fun create(bitmap:Bitmap,face:FaceRegion?=null):Bitmap {
  val crop=HeroBackdropPolicy.crop(bitmap.width,bitmap.height,face)
  if(crop==null){
   val result=Bitmap.createBitmap(32,96,Bitmap.Config.ARGB_8888)
   val colors=edgeColors(bitmap);val paint=Paint().apply{shader=LinearGradient(0f,0f,0f,96f,colors,null,Shader.TileMode.CLAMP)}
   Canvas(result).drawRect(0f,0f,32f,96f,paint);return result
  }
  val source=Bitmap.createBitmap(bitmap,crop.left,crop.top,crop.width,crop.height)
  val factor=512f/maxOf(source.width,source.height)
  val w=(source.width*factor).toInt().coerceAtLeast(1);val h=(source.height*factor).toInt().coerceAtLeast(1)
  val small=Bitmap.createScaledBitmap(source,w,h,true)
  var pixels=IntArray(w*h);var out=IntArray(w*h);small.getPixels(pixels,0,w,0,0,w,h)
  if(small!==bitmap && small!==source)small.recycle()
  if(source!==bitmap)source.recycle()
  fun pass(input:IntArray,output:IntArray,horizontal:Boolean){
   val length=if(horizontal)w else h;val lines=if(horizontal)h else w;val radius=2;val count=radius*2+1
   for(line in 0 until lines){
    fun index(pos:Int)=if(horizontal)line*w+pos.coerceIn(0,length-1) else pos.coerceIn(0,length-1)*w+line
    var red=0;var green=0;var blue=0
    fun add(color:Int,sign:Int){red+=((color shr 16)and 255)*sign;green+=((color shr 8)and 255)*sign;blue+=(color and 255)*sign}
    for(i in -radius..radius)add(input[index(i)],1)
    for(i in 0 until length){output[index(i)]=Color.rgb(red/count,green/count,blue/count);add(input[index(i-radius)],-1);add(input[index(i+radius+1)],1)}
   }
  }
  repeat(1){pass(pixels,out,true);val swap=pixels;pixels=out;out=swap;pass(pixels,out,false);val swap2=pixels;pixels=out;out=swap2}
  val blurred=Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888)
  return blurred
 }
 fun edgeColors(bitmap:Bitmap):IntArray = IntArray(3){band->
  var red=0L;var green=0L;var blue=0L;var count=0
  val x0=(bitmap.width*.94f).toInt().coerceAtMost(bitmap.width-1)
  val y0=band*bitmap.height/3;val y1=((band+1)*bitmap.height/3).coerceAtLeast(y0+1)
  for(y in y0 until y1 step maxOf(1,(y1-y0)/24))for(x in x0 until bitmap.width step maxOf(1,(bitmap.width-x0)/8)){
   val color=bitmap.getPixel(x,y);red+=Color.red(color);green+=Color.green(color);blue+=Color.blue(color);count++
  }
  Color.rgb((red/count).toInt(),(green/count).toInt(),(blue/count).toInt())
 }
}
