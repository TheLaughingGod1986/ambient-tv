package org.ambienttv.home

import kotlin.math.min

data class PhotoSize(val width:Int,val height:Int)
data class ContainedSize(val width:Double,val height:Double)
object ContainGeometry {
 fun fit(width:Int,height:Int,boxWidth:Double,boxHeight:Double):ContainedSize {
  require(boxWidth.isFinite() && boxHeight.isFinite() && boxWidth>0 && boxHeight>0)
  if(width<=0 || height<=0) return ContainedSize(0.0,0.0)
  val scale=min(boxWidth/width,boxHeight/height)
  return ContainedSize(width*scale,height*scale)
 }
}
object DreamPolicy {
 const val INTERVAL=60000L
 const val TOP=140
 const val BOTTOM=1032
 const val BAND_WIDTH=1824.0
 const val BAND_HEIGHT=892.0
 const val GAP=16.0
 const val NEAR_TIE=.005
 /** Occupied photo area in equal-width cells; letterbox and gutters do not count as photo. */
 fun coverage(sizes:List<PhotoSize>,columns:Int):Double {
  if(columns !in 1..3 || sizes.size<columns) return 0.0
  val cellWidth=(BAND_WIDTH-GAP*(columns-1))/columns
  return sizes.take(columns).sumOf {
   val fit=ContainGeometry.fit(it.width,it.height,cellWidth,BAND_HEIGHT)
   fit.width*fit.height
  }/(BAND_WIDTH*BAND_HEIGHT)
 }
 fun chooseCount(sizes:List<PhotoSize>,mode:Int):Int {
  require(mode in 0..3)
  val limit=minOf(sizes.size,3)
  if(limit==0) return 0
  if(mode>0) return minOf(mode,limit)
  val scores=(1..limit).map { coverage(sizes,it) }
  val best=scores.maxOrNull()!!
  // A tiny area difference is not worth making every photograph smaller.
  return scores.indexOfFirst { it>=best-NEAR_TIE }+1
 }
 fun indices(size:Int,start:Int,count:Int):List<Int> {
  if(size<=0) return emptyList()
  return (0 until count.coerceIn(0,minOf(3,size))).map { Math.floorMod(start.toLong()+it,size.toLong()).toInt() }
 }
 fun mayRun(mediaActive:Boolean)=!mediaActive
}
