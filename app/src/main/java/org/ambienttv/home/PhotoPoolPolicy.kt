package org.ambienttv.home

object PhotoPoolPolicy {
 const val LIMIT=48
 const val BATCH=6
 const val NEW_SET_BYTES=48L*1024*1024
 const val TOTAL_SOURCE_BYTES=64L*1024*1024
 fun ordered(names:List<String>,prefix:String,saved:List<String>):List<String> {
  val available=names.filter {ImagePolicy.valid(it) && it.startsWith(prefix)}.distinct()
  return (saved.filter {it in available}+available).distinct().take(LIMIT)
 }
 fun startCursor(savedGeneration:String,currentGeneration:String,saved:Int,size:Int):Int = if(size<=0 || savedGeneration!=currentGeneration)0 else Math.floorMod(saved,size)
 fun nextCursor(current:Int,shown:Int,size:Int):Int = if(size<=0)0 else Math.floorMod(current.toLong()+shown,size.toLong()).toInt()
}
