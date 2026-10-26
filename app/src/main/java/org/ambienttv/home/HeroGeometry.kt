package org.ambienttv.home

data class HeroRect(val left:Double,val top:Double,val width:Double,val height:Double)
object HeroGeometry {
 fun fit(width:Int,height:Int):HeroRect {
  val fit=ContainGeometry.fit(width,height,1920.0,1080.0)
  val area=if(width>0 && height>0 && width.toDouble()/height<1.3)1440.0 else 1920.0
  return HeroRect(((area-fit.width)/2).coerceAtLeast(0.0),(1080-fit.height)/2,fit.width,fit.height)
 }
 fun previewIndices(size:Int,current:Int):List<Int> {
  if(size<=0)return emptyList()
  val index=Math.floorMod(current,size)
  return DreamPolicy.indices(size,(index/3)*3,3)
 }
}

internal data class HeroFade(val start:Float,val peak:Float,val end:Float)
internal object HeroFadePolicy {
 fun transition(width:Float,right:Float,canvasWidth:Float=1920f,protectRight:Float?=null):HeroFade? {
  if(width<=0 || right<=0 || right>=canvasWidth-1)return null
  val normal=minOf(100f,width*.12f)
  val inside=if(protectRight==null)normal else minOf(normal,(right-protectRight-8f).coerceAtLeast(8f))
  return HeroFade(right-inside,right,minOf(canvasWidth,right+240f))
 }
}

internal data class HeroCrop(val left:Int,val top:Int,val width:Int,val height:Int)
internal object HeroBackdropPolicy {
 fun crop(w:Int,h:Int,face:FaceRegion?):HeroCrop? {
  require(w>0 && h>0)
  if(w.toFloat()/h>=1.25f)return HeroCrop(0,0,w,h)
  val margin=maxOf(1,(h*.03f).toInt())
  val bottom=maxOf((h*.75f).toInt(),face?.let{kotlin.math.ceil(it.bottom).toInt()+margin} ?: 0)
  if(h-bottom>=maxOf(1,h/12))return HeroCrop(0,bottom,w,h-bottom)
  val right=maxOf((w*.75f).toInt(),face?.let{kotlin.math.ceil(it.right).toInt()+margin} ?: 0)
  if(w-right>=maxOf(1,w/12))return HeroCrop(right,0,w-right,h)
  val left=face?.let{(it.left-margin).toInt().coerceIn(0,w)} ?: 0
  if(left>=maxOf(1,w/12))return HeroCrop(0,0,left,h)
  val top=face?.let{(it.top-margin).toInt().coerceIn(0,h)} ?: 0
  if(top>=maxOf(1,h/12))return HeroCrop(0,0,w,top)
  return null // A close-up with no face-free region gets ambient colour, never another face.
 }
}
