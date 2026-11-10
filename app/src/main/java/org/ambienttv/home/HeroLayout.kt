package org.ambienttv.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/** Where a caption belongs: the left edge of its photo on the 1920px canvas, and its two lines if known. */
internal class HeroCaption(val left:Float,val lines:Pair<String,String?>?)

/**
 * How the hero screensaver fills a wide screen. A landscape photo fills it alone. An upright photo
 * shares it with one more upright photo, or two more when it is very tall, so the whole screen is
 * picture and only a small part of each photo is trimmed.
 */
internal object HeroLayoutPolicy {
 const val GAP=6
 /** Below this ratio three photos across trim less of each picture than two would. */
 const val VERY_TALL=.68f
 fun upright(ratio:Float)=ratio>0f && ratio<HomePhotoPolicy.UPRIGHT
 fun count(ratio:Float)=if(!upright(ratio))1 else if(ratio<VERY_TALL)3 else 2
 /** Left edge and width of each photo across the 1920px canvas; the last one absorbs any rounding. */
 fun panels(count:Int):List<Pair<Int,Int>> {
  require(count in 1..3)
  val width=(1920-(count-1)*GAP)/count
  return (0 until count).map{val left=it*(width+GAP);left to if(it==count-1)1920-left else width}
 }
}

/** Worker thread only. Draws the chosen photos side by side into one frame and recycles the sources. */
internal object HeroComposite {
 fun compose(sources:List<Bitmap>):Bitmap {
  val out=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888)
  val canvas=Canvas(out);canvas.drawColor(Color.rgb(5,14,24))
  val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
  HeroLayoutPolicy.panels(sources.size).forEachIndexed{i,(left,width)->
   val source=sources[i]
   // The same face-aware, bounded enlargement as the Home tiles, so a face is never cut to fill a panel.
   val frame=HomePhotoPolicy.featureFrame(source.width,source.height,width,1080,HeroComposition.detect(source))
   canvas.save();canvas.clipRect(left,0,left+width,1080)
   canvas.drawBitmap(source,null,RectF(left+frame.left,frame.top,left+frame.left+frame.width,frame.top+frame.height),paint)
   canvas.restore();source.recycle()
  }
  return out
 }
}
