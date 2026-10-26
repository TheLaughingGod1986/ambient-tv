package org.ambienttv.home

object BitmapPolicy {
 const val MAX_WIDTH=1920
 const val MAX_HEIGHT=1080
 /** Applied before every decode, including previously persisted source photos. */
 fun sampleSize(width: Int,height: Int,maxWidth:Int=MAX_WIDTH,maxHeight:Int=MAX_HEIGHT): Int {
  require(maxWidth in 1..MAX_WIDTH && maxHeight in 1..MAX_HEIGHT)
  require(width in 1..20000 && height in 1..20000)
  var sample=1
  while((width+sample-1)/sample>maxWidth || (height+sample-1)/sample>maxHeight) sample*=2
  return sample
 }
}
