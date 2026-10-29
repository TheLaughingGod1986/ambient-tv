package org.ambienttv.home

/** One stable daily row. Prefer card-fitting photos before new scenes; never substitute a non-favourite. */
object DailyHomeSelection {
 fun select(available:List<String>,favourites:Set<String>,day:Long,saved:List<String>,previous:List<String>,related:(String,String)->Boolean,fit:(String,Int)->Double,distance:(String,String)->Double={_,_->1.0}):List<String?> {
  val all=available.distinct();val result=MutableList<String?>(4){null}
  for(slot in listOf(2,0,1,3)) {
   val pool=all.filter{(slot!=2 || it in favourites) && result.filterNotNull().none{chosen->related(it,chosen)}}
   // Wide hero cards must not sacrifice image fit for colour diversity or novelty.
   // Retain the best available orientations even when the cache is mostly portraits.
   val bestFit=pool.minOfOrNull{fit(it,slot)} ?: 0.0
   val oriented=if(slot<2)pool.filter{fit(it,slot)<=bestFit+.10}else pool
   val retained=saved.getOrNull(slot)?.takeIf{it in oriented}
   val fresh=oriented.filter{candidate->previous.filter{it in all}.none{related(candidate,it)}}
   val candidates=if(fresh.isNotEmpty())fresh else oriented.filter{it !in previous}.ifEmpty{oriented}
   fun score(id:String)=fit(id,slot)+2.5*(1.0-(result.filterNotNull().minOfOrNull{distance(id,it)} ?: 1.0))
   val best=candidates.minOfOrNull{score(it)} ?: 0.0
   val fitting=candidates.filter{score(it)<=best+.12}
   result[slot]=retained ?: HomePhotoPolicy.choose(fitting,day,1).firstOrNull()
  }
  return result
 }
}
