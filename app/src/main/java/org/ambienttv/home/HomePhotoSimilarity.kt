package org.ambienttv.home

/** Tiny colour grids catch duplicate exports and near-identical burst shots. Never persisted or uploaded. */
object HomePhotoSimilarity {
 fun similar(a:IntArray,b:IntArray):Boolean {
  if(a.isEmpty() || a.size!=b.size)return false
  var distance=0L
  for(i in a.indices)for(shift in intArrayOf(16,8,0))distance+=kotlin.math.abs(((a[i] ushr shift) and 255)-((b[i] ushr shift) and 255))
  if(distance.toDouble()/(a.size*3)<=28.0)return true
  return colourDistance(a,b)<=.40
 }
 fun colourDistance(a:IntArray,b:IntArray):Double {
  if(a.isEmpty() || a.size!=b.size)return 1.0
  // Colour distribution also catches reframed/rotated copies of the same scene.
  fun histogram(pixels:IntArray):IntArray {
   val bins=IntArray(64)
   for(pixel in pixels){val bin=(((pixel ushr 22) and 3) shl 4) or (((pixel ushr 14) and 3) shl 2) or ((pixel ushr 6) and 3);bins[bin]++}
   return bins
  }
  val ah=histogram(a);val bh=histogram(b)
  return ah.indices.sumOf{kotlin.math.abs(ah[it]-bh[it])}.toDouble()/(2*a.size)
 }
}
