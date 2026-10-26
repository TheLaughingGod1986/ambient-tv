package org.ambienttv.home
/** Keep each source's published recency order, alternating when both have resume items. */
object SourceMixPolicy {
 fun indices(owners:List<String>,limit:Int,firstOwner:String?=null):List<Int> {
  require(limit>=0)
  val remaining=owners.indices.toMutableList();val result=mutableListOf<Int>();var last:String?=null
  while(remaining.isNotEmpty() && result.size<limit){
   val next=if(result.isEmpty() && firstOwner!=null)remaining.firstOrNull{owners[it]==firstOwner} ?: remaining.first() else remaining.firstOrNull{owners[it]!=last} ?: remaining.first()
   result.add(next);remaining.remove(next);last=owners[next]
  }
  return result
 }
}
