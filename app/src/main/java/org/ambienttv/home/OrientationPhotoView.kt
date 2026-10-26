package org.ambienttv.home

import android.content.Context
import android.graphics.*
import android.view.View
import android.widget.FrameLayout

/** Match the hardware outline in native software previews as well as on the TV. */
internal class ClippedPhotoCard(c:Context,private val radius:Float):FrameLayout(c) {
 private val clip=Path()
 override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){super.onSizeChanged(w,h,oldw,oldh);clip.reset();clip.addRoundRect(RectF(0f,0f,w.toFloat(),h.toFloat()),radius,radius,Path.Direction.CW)}
 override fun dispatchDraw(canvas:Canvas){val save=canvas.save();canvas.clipPath(clip);super.dispatchDraw(canvas);canvas.restoreToCount(save)}
}

/** Owns only decoded thumbnails. No network, face analysis, animation, or original downloads. */
internal class OrientationPhotoView(c:Context):View(c) {
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
 private var photo:Bitmap?=null
 private var fill:Bitmap?=null
 private val requests=PhotoRequestState()
 val requestVersion get()=requests.version
 val hasPhoto get()=photo!=null
 fun request(key:String?)=requests.request(key,hasPhoto)
 fun show(bitmap:Bitmap?,background:Bitmap?) {
  requests.complete();photo?.recycle();fill?.recycle();photo=bitmap;fill=background;invalidate()
 }
 fun clear(){requests.clear();show(null,null)}
 override fun onDetachedFromWindow(){clear();super.onDetachedFromWindow()}
 override fun onDraw(c:Canvas) {
  super.onDraw(c);c.drawColor(Color.rgb(18,35,49))
  val image=photo ?: return
  fill?.let{blur->
   val scale=maxOf(width.toFloat()/blur.width,height.toFloat()/blur.height)
   val w=blur.width*scale;val h=blur.height*scale
   paint.alpha=150;c.drawBitmap(blur,null,RectF((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2),paint);paint.alpha=255
   c.drawColor(Color.argb(65,3,10,17))
  }
  val frame=HomePhotoPolicy.frame(image.width,image.height,width.coerceAtLeast(1),height.coerceAtLeast(1))
  c.drawBitmap(image,null,RectF(frame.left,frame.top,frame.left+frame.width,frame.top+frame.height),paint)
 }
 companion object {
  /** Deliberately tiny soft fill, made on the existing image worker, never on the UI thread. */
  fun softFill(bitmap:Bitmap):Bitmap {
   val factor=28f/maxOf(bitmap.width,bitmap.height)
   val fill=Bitmap.createScaledBitmap(bitmap,(bitmap.width*factor).toInt().coerceAtLeast(1),(bitmap.height*factor).toInt().coerceAtLeast(1),true)
   return if(fill===bitmap)bitmap.copy(Bitmap.Config.ARGB_8888,false)else fill
  }
 }
}
