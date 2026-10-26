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
 const val LEFT=244;const val RIGHT=1888;const val PHOTO_Y=244;const val PHOTO_W=396;const val PHOTO_H=390;const val GAP=20
 const val MEDIA_HEADING_Y=654;const val MEDIA_Y=724;const val MEDIA_W=312;const val MEDIA_H=184;const val DOCK_Y=936;const val DOCK_H=76
 fun scale(width:Int,height:Int)=min(width/1920f,height/1080f)
}
