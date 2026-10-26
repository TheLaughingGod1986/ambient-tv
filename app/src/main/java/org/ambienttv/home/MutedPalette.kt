package org.ambienttv.home

/** Coarse on-device colour only; no subject/face analysis or exported image data. */
object MutedPalette {
 fun average(colors:IntArray):Int {
  if(colors.isEmpty()) return 0xff18222a.toInt()
  var r=0L;var g=0L;var b=0L
  for(color in colors) { r+=color ushr 16 and 255;g+=color ushr 8 and 255;b+=color and 255 }
  return (255 shl 24) or ((r/colors.size).toInt() shl 16) or ((g/colors.size).toInt() shl 8) or (b/colors.size).toInt()
 }
 fun muted(color:Int,lift:Int=0):Int {
  val r=color ushr 16 and 255;val g=color ushr 8 and 255;val b=color and 255
  val grey=(r*54+g*183+b*19)/256
  fun channel(value:Int)=(12+(value+grey)*18/200+lift).coerceIn(12,70)
  return (255 shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
 }
 fun motionAllowed(animationScale:Float,powerSaving:Boolean)=animationScale>0f && !powerSaving
}
