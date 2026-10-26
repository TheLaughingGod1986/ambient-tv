package org.ambienttv.home

/** UI-thread request gate: one read at a time, strongest pending request wins. */
internal class FeedRefreshState {
 private var pending=false
 private var force=false
 private var running=false
 val hasPending get()=pending
 fun request(forcePlex:Boolean){pending=true;force=force || forcePlex}
 fun begin(active:Boolean):Boolean? {
  if(!active || running || !pending)return null
  running=true;pending=false;val result=force;force=false;return result
 }
 fun finish():Boolean{running=false;return !pending}
 fun pause(){pending=false;force=false}
}
object ResumeFeedPolicy {
 fun fresh(now:Long,at:Long,ttl:Long)=at>0 && now>=at && now-at<ttl
 fun plexMillis(seconds:Long)=if(seconds>0 && seconds<=Long.MAX_VALUE/1000)seconds*1000 else 0
 fun focusIndex(key:String?,previous:Int,keys:List<String>):Int {
  if(previous<0 || keys.isEmpty())return -1
  val same=keys.indexOf(key);return if(same>=0)same else previous.coerceAtMost(keys.lastIndex)
 }
}

/** A failed source read keeps its last snapshot; a successful empty read clears it. */
internal class LastGoodSource<T> {
 private var items=emptyList<T>()
 @Synchronized fun update(result:List<T>?):List<T>{if(result!=null)items=result.toList();return items}
}
