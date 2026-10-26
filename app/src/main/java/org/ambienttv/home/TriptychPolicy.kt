package org.ambienttv.home

/** Stable groups follow the provider's cached order, never repeat a photo within a frame. */
object TriptychPolicy {
 fun <T> group(items: List<T>, anchor: T): List<T> {
  val distinct=items.distinct()
  if(distinct.isEmpty()) return emptyList()
  val start=distinct.indexOf(anchor).coerceAtLeast(0)
  return (0 until minOf(3,distinct.size)).map { distinct[(start+it)%distinct.size] }
 }
 fun <T> anchors(items: List<T>): List<T> = items.distinct().filterIndexed { index,_ -> index%3==0 }
}
