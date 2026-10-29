package org.ambienttv.home

import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

data class PhotoFrame(val left:Float,val top:Float,val width:Float,val height:Float)
object HomePhotoPolicy {
 /** Whole portraits/panoramas over a soft fill; moderate landscapes cover without distortion. */
 fun frame(w:Int,h:Int,targetW:Int,targetH:Int,focalX:Float=.5f,focalY:Float=.42f):PhotoFrame {
  require(w>0 && h>0 && targetW>0 && targetH>0)
  val ratio=w.toFloat()/h
  val contain=ratio<1.1f || ratio>2.5f
  val scale=if(contain)min(targetW.toFloat()/w,targetH.toFloat()/h)else max(targetW.toFloat()/w,targetH.toFloat()/h)
  val width=w*scale;val height=h*scale
  val x=if(width<=targetW)(targetW-width)/2 else (targetW/2f-focalX.coerceIn(0f,1f)*width).coerceIn(targetW-width,0f)
  val y=if(height<=targetH)(targetH-height)/2 else (targetH*.42f-focalY.coerceIn(0f,1f)*height).coerceIn(targetH-height,0f)
  return PhotoFrame(x,y,width,height)
 }
 /** Small portrait zoom only when the padded face remains entirely visible; unknown/group shots stay whole. */
 fun featureFrame(w:Int,h:Int,tw:Int,th:Int,face:FaceRegion?):PhotoFrame {
  if(w.toFloat()/h>=1.1f && abs(ln((w.toFloat()/h)/(tw.toFloat()/th)))>.18f) {
   val scale=min(tw.toFloat()/w,th.toFloat()/h)
   return PhotoFrame((tw-w*scale)/2,(th-h*scale)/2,w*scale,h*scale)
  }
  if(face==null || w.toFloat()/h>=1.1f)return frame(w,h,tw,th)
  val fh=(face.bottom-face.top).coerceAtLeast(1f);val fw=(face.right-face.left).coerceAtLeast(1f)
  val l=(face.left-fw*.3f).coerceAtLeast(0f);val r=(face.right+fw*.3f).coerceAtMost(w.toFloat())
  val t=(face.top-fh*.65f).coerceAtLeast(0f);val b=(face.bottom+fh*.45f).coerceAtMost(h.toFloat())
  val contain=min(tw.toFloat()/w,th.toFloat()/h)
  val zoom=min(contain*1.18f,min(tw/(r-l),th/(b-t))).coerceAtLeast(contain)
  val width=w*zoom;val height=h*zoom
  fun offset(size:Float,target:Int,start:Float,end:Float,anchor:Float):Float {
   if(size<=target)return (target-size)/2
   val low=max(target-size,-start*zoom);val high=min(0f,target-end*zoom)
   return if(low<=high)(target*anchor-(start+end)*.5f*zoom).coerceIn(low,high)else (target-size)/2
  }
  return PhotoFrame(offset(width,tw,l,r,.5f),offset(height,th,t,b,.42f),width,height)
 }
 /** Keep a varied pool near the best aspect fit, rather than repeatedly choosing one image. */
 fun fittingIds(ratios:Map<String,Float>,target:Float):Set<String> {
  val scores=ratios.filterValues{it>0f && it.isFinite()}.mapValues{abs(ln(it.value/target))}
  val best=scores.values.minOrNull() ?: return emptySet()
  return scores.filterValues{it<=max(.18f,best+.12f)}.keys
 }
 fun localDay(now:Long,zone:TimeZone=TimeZone.getDefault()):Long {
  val key=SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).apply{timeZone=zone}.format(Date(now))
  val utc=SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).apply{timeZone=TimeZone.getTimeZone("UTC")}.parse(key)!!.time
  return Math.floorDiv(utc,86400000L)
 }
 fun choose(available:List<String>,day:Long,count:Int,previous:List<String> = emptyList(),sameDay:List<String> = emptyList()):List<String> {
  require(count>=0)
  val all=available.distinct().sortedWith(compareBy<String>{it.hashCode()}.thenBy{it})
  if(all.isEmpty() || count==0)return emptyList()
  val keep=sameDay.filter{it in all}.distinct().take(count)
  val offset=Math.floorMod(day*count,all.size.toLong()).toInt()
  val cycle=all.drop(offset)+all.take(offset)
  val fresh=cycle.filter{it !in previous && it !in keep}
  return (keep+fresh+cycle).distinct().take(count)
 }
}

object HomeLayout {
 const val COMPACT_LEFT=124 // 96px icon rail plus a 28px content gutter.
 fun contentX(x:Int,compact:Boolean)=if(!compact)x else COMPACT_LEFT+(x-244)*(1888-COMPACT_LEFT)/(1888-244)
 fun contentWidth(width:Int,compact:Boolean)=if(!compact)width else width*(1888-COMPACT_LEFT)/(1888-244)

 const val LEFT=244;const val RIGHT=1888;const val PHOTO_Y=244;const val PHOTO_W=396;const val PHOTO_H=338;const val GAP=20
 const val MEDIA_HEADING_Y=764;const val MEDIA_Y=824;const val MEDIA_W=396;const val MEDIA_H=223;const val DOCK_Y=654;const val DOCK_H=100
 fun photoWidth(index:Int)=when(index){0->666;1->492;else->446}
 fun photoX(index:Int)=when(index){0->LEFT;1->930;else->1442}
 fun photoY(index:Int)=if(index==3)423 else PHOTO_Y
 fun photoHeight(index:Int)=if(index<2)PHOTO_H else 159
 fun scale(width:Int,height:Int)=min(width/1920f,height/1080f)
}
