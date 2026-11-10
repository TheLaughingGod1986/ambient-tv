package org.ambienttv.home

/** Pure smoothing for the tiny poster/photo fill, kept free of Android types so it can be unit tested. */
internal object FillBlur {
 /** Two passes of a 3x3 average over opaque RGB pixels; edges reuse their nearest neighbours. */
 fun smooth(pixels:IntArray,width:Int,height:Int,passes:Int=2):IntArray {
  require(width>0 && height>0 && pixels.size==width*height)
  var source=pixels
  repeat(passes){
   val out=IntArray(source.size)
   for(y in 0 until height)for(x in 0 until width){
    var r=0;var g=0;var b=0
    for(dy in -1..1)for(dx in -1..1){
     val p=source[(y+dy).coerceIn(0,height-1)*width+(x+dx).coerceIn(0,width-1)]
     r+=p shr 16 and 255;g+=p shr 8 and 255;b+=p and 255
    }
    out[y*width+x]=(255 shl 24) or ((r/9) shl 16) or ((g/9) shl 8) or (b/9)
   }
   source=out
  }
  return source
 }
}
