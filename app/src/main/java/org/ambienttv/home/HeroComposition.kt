package org.ambienttv.home
import android.graphics.Bitmap
import android.graphics.PointF
import android.media.FaceDetector
import kotlin.math.*
data class FaceRegion(val left:Float,val top:Float,val right:Float,val bottom:Float)
data class HeroPlacement(val left:Float,val top:Float,val width:Float,val height:Float)
object HeroComposition {
 fun place(w:Int,h:Int,face:FaceRegion?):HeroPlacement{
  val contain=1080f/h
  if(w.toFloat()/h<1.25f){
   // Portraits can fill the reference's wider hero area when the detected
   // subject fits. Unknown/tight subjects retain the complete photograph.
   if(face==null)return HeroPlacement(0f,0f,w*contain,1080f)
   val faceHeight=(face.bottom-face.top).coerceAtLeast(1f)
   val target=min(1320f/w,contain*1.7f)
   val scale=max(contain,min(target,1000f/faceHeight))
   val height=h*scale
   val ideal=470f-(face.top+face.bottom)*.5f*scale
   // Intersect image coverage with bounds that keep the entire detected face.
   val low=max(1080f-height,-face.top*scale)
   val high=min(0f,1080f-face.bottom*scale)
   val y=if(low<=high)ideal.coerceIn(low,high) else 0f
   return HeroPlacement(0f,y,w*scale,height)
  }
  val cover=max(1920f/w,1080f/h)
  val safe=if(face!=null)1080f/((face.bottom-face.top)*1.18f).coerceAtLeast(1f) else cover
  val scale=max(contain,min(cover,safe));val width=w*scale;val height=h*scale
  val cx=face?.let{(it.left+it.right)/2} ?: w/2f;val cy=face?.let{(it.top+it.bottom)/2} ?: h/2f
  val x=if(width>=1920)(740-cx*scale).coerceIn(1920-width,0f)else 0f
  val y=(470-cy*scale).coerceIn(1080-height,0f)
  return HeroPlacement(x,y,width,height)
 }
 fun detect(bitmap:Bitmap):FaceRegion?=try{
  val ratio=min(1f,640f/max(bitmap.width,bitmap.height));val w=((bitmap.width*ratio).toInt()/2*2).coerceAtLeast(2);val h=(bitmap.height*ratio).toInt().coerceAtLeast(2)
  val scaled=Bitmap.createScaledBitmap(bitmap,w,h,true);val rgb=scaled.copy(Bitmap.Config.RGB_565,false)
  if(scaled!==bitmap)scaled.recycle()
  try{val faces=arrayOfNulls<FaceDetector.Face>(3);val count=FaceDetector(w,h,3).findFaces(rgb,faces)
   val portrait=bitmap.width.toFloat()/bitmap.height<1.25f
   if(portrait && count>1)null else { // Preserve group shots and photo collages in full.
   val rx=bitmap.width.toFloat()/w;val ry=bitmap.height.toFloat()/h
   val regions=(0 until count).mapNotNull{faces[it]}.mapNotNull{face->
    val mid=PointF();face.getMidPoint(mid);val eyes=face.eyesDistance()
    // Small lower-frame detections commonly come from clothing. Do not use
    // them to crop an unknown portrait; conservative containment is safer.
    if(face.confidence()<(if(portrait).45f else .35f) || (portrait && (mid.y>h*.65f || eyes<w*.10f)))null
    else if(portrait)FaceRegion(max(0f,(mid.x-1.25f*eyes)*rx),max(0f,(mid.y-.9f*eyes)*ry),min(bitmap.width.toFloat(),(mid.x+1.25f*eyes)*rx),min(bitmap.height.toFloat(),(mid.y+1.6f*eyes)*ry))
    else FaceRegion(max(0f,(mid.x-2*eyes)*rx),max(0f,(mid.y-1.8f*eyes)*ry),min(bitmap.width.toFloat(),(mid.x+2*eyes)*rx),min(bitmap.height.toFloat(),(mid.y+2.8f*eyes)*ry))
   }
   if(regions.isEmpty())null else if(portrait)FaceRegion(regions.minOf{it.left},regions.minOf{it.top},regions.maxOf{it.right},regions.maxOf{it.bottom})
   else regions.maxByOrNull{(it.right-it.left)*(it.bottom-it.top)}
   }
  }finally{rgb.recycle()}
 }catch(_:Exception){null}
}
