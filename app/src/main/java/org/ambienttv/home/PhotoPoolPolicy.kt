package org.ambienttv.home

object PhotoPoolPolicy {
 const val LIMIT=48
 const val BATCH=6
 /** Favourites always keep a place in the pool, up to this many, so the rotating memories still have room. */
 const val FAVOURITES=16
 /** Photo ids of saved favourites (stored as "id.jpg") that the NAS still offers, newest id first. */
 fun keptFavourites(saved:Set<String>,available:Set<Int>):List<Int> =
  saved.mapNotNull{it.substringBefore('.').toIntOrNull()}.filter{it in available}.distinct().sortedDescending().take(FAVOURITES)
 const val NEW_SET_BYTES=48L*1024*1024
 const val TOTAL_SOURCE_BYTES=64L*1024*1024
 fun ordered(names:List<String>,prefix:String,saved:List<String>):List<String> {
  val available=names.filter {ImagePolicy.valid(it) && it.startsWith(prefix)}.distinct()
  return (saved.filter {it in available}+available).distinct().take(LIMIT)
 }
 fun startCursor(savedGeneration:String,currentGeneration:String,saved:Int,size:Int):Int = if(size<=0 || savedGeneration!=currentGeneration)0 else Math.floorMod(saved,size)
 fun nextCursor(current:Int,shown:Int,size:Int):Int = if(size<=0)0 else Math.floorMod(current.toLong()+shown,size.toLong()).toInt()
}
