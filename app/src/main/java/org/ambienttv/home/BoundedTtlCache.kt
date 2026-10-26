package org.ambienttv.home

/** Process-local, weighted cache. Short cache-local synchronization; no credentials or disk writes. */
internal class BoundedTtlCache<K,V>(private val limit:Int,private val ttl:Long,private val weight:(V)->Int) {
 private data class Entry<V>(val value:V,val bytes:Int,val at:Long)
 private val entries=LinkedHashMap<K,Entry<V>>(16,.75f,true)
 var bytes=0;private set
 @Synchronized fun get(key:K,now:Long):V? {
  val entry=entries[key] ?: return null
  if(now<entry.at || now-entry.at>=ttl){entries.remove(key);bytes-=entry.bytes;return null}
  return entry.value
 }
 @Synchronized fun put(key:K,value:V,now:Long){
  entries.remove(key)?.let{bytes-=it.bytes}
  val size=weight(value);if(size<=0 || size>limit)return
  entries[key]=Entry(value,size,now);bytes+=size
  val iterator=entries.entries.iterator()
  while(bytes>limit && iterator.hasNext()){bytes-=iterator.next().value.bytes;iterator.remove()}
 }
 @Synchronized fun clear(){entries.clear();bytes=0}
}

/** A pending decode counts as a request, even before a bitmap is available. */
internal class PhotoRequestState {
 private var key:String?=null
 private var initialized=false
 private var pending=false
 @Volatile var version=0;private set
 fun request(next:String?,hasPhoto:Boolean):Boolean {
  if(initialized && key==next && (pending || hasPhoto || next==null))return false
  initialized=true;key=next;pending=next!=null;version++;return true
 }
 fun complete(){pending=false}
 fun clear(){initialized=false;key=null;pending=false;version++}
}
