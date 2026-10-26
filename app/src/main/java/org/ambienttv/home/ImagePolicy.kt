package org.ambienttv.home

object ImagePolicy {
 private val filename=Regex("[0-9]+-[0-9]+\\.jpg")
 fun valid(name: String)=filename.matches(name)
 fun completeGeneration(expected: Int, actual: Int): Boolean = if(expected==0) actual>0 else expected in 1..PhotoPoolPolicy.LIMIT && actual==expected
 /** A launcher may retain an old URI beyond its advertised cache. Alias only into this person's current set. */
 fun resolve(requested: String, available: Set<String>, current: List<String>): String {
  require(valid(requested))
  if(requested in available) return requested
  val safe=current.filter { valid(it) && it in available }
  if(safe.isEmpty()) throw java.io.FileNotFoundException("No cached person photos")
  val item=requested.substringAfter('-')
  return safe.firstOrNull { it.substringAfter('-')==item } ?: safe[Math.floorMod(requested.hashCode(),safe.size)]
 }
}

object RefreshPolicy {
 fun shouldFetch(now: Long, lastAttempt: Long, lastSuccess: Long, hasPhotos: Boolean): Boolean {
  if(lastAttempt>0 && now-lastAttempt in 0 until 900000L) return false
  return !hasPhotos || lastSuccess<=0 || now-lastSuccess !in 0 until 21600000L
 }
}
