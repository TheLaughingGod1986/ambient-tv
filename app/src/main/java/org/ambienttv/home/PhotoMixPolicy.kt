package org.ambienttv.home

/** A complete bounded pool: recent pictures interleaved with a moving archive window. */
object PhotoMixPolicy {
 const val RECENT=16
 const val MEMORIES=32
 fun select(recent:List<Int>,archive:List<Int>):List<Int> {
  val latest=recent.distinct().take(RECENT)
  val older=archive.distinct().filter{it !in latest}.take(MEMORIES)
  val mixed=mutableListOf<Int>()
  for(i in 0 until maxOf(latest.size,older.size)) {
   latest.getOrNull(i)?.let{mixed.add(it)}
   older.getOrNull(i*2)?.let{mixed.add(it)}
   older.getOrNull(i*2+1)?.let{mixed.add(it)}
  }
  return (mixed+recent+archive).distinct().take(PhotoPoolPolicy.LIMIT)
 }
}
