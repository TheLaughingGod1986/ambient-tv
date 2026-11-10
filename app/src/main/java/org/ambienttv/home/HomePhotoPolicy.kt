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
 /**
  * Home tiles. A picture with one clear face is enlarged to fill the tile as far as the padded
  * face stays entirely visible. Landscapes are only enlarged when that trims top and bottom,
  * never the sides where other people stand. Without a detected face the picture is enlarged
  * only until about a third of its height is off screen, trimmed a little more from the bottom than the top.
  */
 /** Below this width-to-height ratio a photo counts as upright. */
 const val UPRIGHT=1.1f
 private const val MIN_VISIBLE=.66f
 fun featureFrame(w:Int,h:Int,tw:Int,th:Int,face:FaceRegion?):PhotoFrame {
  val ratio=w.toFloat()/h;val target=tw.toFloat()/th
  val landscape=ratio>=1.1f
  val contain=min(tw.toFloat()/w,th.toFloat()/h);val cover=max(tw.toFloat()/w,th.toFloat()/h)
  if(landscape && abs(ln(ratio/target))<=.18f)return frame(w,h,tw,th)
  if(landscape && ratio>target)return PhotoFrame((tw-w*contain)/2,(th-h*contain)/2,w*contain,h*contain)
  if(face==null){
   // No face to protect: enlarge towards filling the tile, but keep about two thirds of the height on screen,
   // which is what a 4:3 picture needs to fill the widest tile.
   val zoom=min(cover,contain/MIN_VISIBLE);val width=w*zoom;val height=h*zoom
   return PhotoFrame((tw-width)/2,if(height<=th)(th-height)/2 else (th*.42f-.42f*height).coerceIn(th-height,0f),width,height)
  }
  val fh=(face.bottom-face.top).coerceAtLeast(1f);val fw=(face.right-face.left).coerceAtLeast(1f)
  val l=(face.left-fw*.3f).coerceAtLeast(0f);val r=(face.right+fw*.3f).coerceAtMost(w.toFloat())
  val t=(face.top-fh*.65f).coerceAtLeast(0f);val b=(face.bottom+fh*.45f).coerceAtMost(h.toFloat())
  // Prefer room around the face. A close-up has no such room, so it may still be enlarged by the
  // same bounded amount as an unknown picture, as long as the detected face itself stays whole.
  val roomy=min(tw/(r-l),th/(b-t));val tight=min(tw/fw,th/fh)
  val zoom=min(cover,max(roomy,min(contain/MIN_VISIBLE,tight))).coerceAtLeast(contain)
  val padded=zoom<=roomy+.0001f
  val width=w*zoom;val height=h*zoom
  fun offset(size:Float,target:Int,start:Float,end:Float,anchor:Float):Float {
   if(size<=target)return (target-size)/2
   val low=max(target-size,-start*zoom);val high=min(0f,target-end*zoom)
   return if(low<=high)(target*anchor-(start+end)*.5f*zoom).coerceIn(low,high)else (target-size)/2
  }
  return PhotoFrame(offset(width,tw,if(padded)l else face.left,if(padded)r else face.right,.5f),offset(height,th,if(padded)t else face.top,if(padded)b else face.bottom,.42f),width,height)
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
 /** Plex library grid: two rows of tiles (poster area above a title strip) that end above the pager row. */
 const val LIBRARY_Y=306;const val POSTER_H=234;const val LIBRARY_CARD_H=298;const val PAGER_Y=944
 /** Eight poster-shaped cards a row, so a page shows 16 titles with little space beside each poster. */
 const val LIBRARY_COLUMNS=8;const val LIBRARY_CARD_W=190
 fun libraryX(column:Int):Int {require(column in 0 until LIBRARY_COLUMNS);return LEFT+column*(RIGHT-LEFT-LIBRARY_CARD_W)/(LIBRARY_COLUMNS-1)}
 /** Each half of the widest photo tile when it shows two upright photos, leaving a 6px gap. */
 const val PAIR_W=330
 const val MEDIA_HEADING_Y=764;const val MEDIA_Y=824;const val MEDIA_W=396;const val MEDIA_H=223;const val DOCK_Y=654;const val DOCK_H=100
 fun photoWidth(index:Int)=when(index){0->666;1->492;else->446}
 fun photoX(index:Int)=when(index){0->LEFT;1->930;else->1442}
 fun photoY(index:Int)=if(index==3)423 else PHOTO_Y
 fun photoHeight(index:Int)=if(index<2)PHOTO_H else 159
 fun scale(width:Int,height:Int)=min(width/1920f,height/1080f)
}
