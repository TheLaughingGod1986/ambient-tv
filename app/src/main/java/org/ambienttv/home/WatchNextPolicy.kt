package org.ambienttv.home
object WatchNextPolicy {
 fun progress(position:Long,duration:Long):Int? = if(position<0 || duration<=0)null else ((position.toDouble()/duration).coerceIn(0.0,1.0)*1000).toInt()
 fun sameOwner(owner:String,resolved:String?)=owner.isNotBlank() && owner==resolved
}
