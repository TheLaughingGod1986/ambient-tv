package org.ambienttv.home
import org.junit.Assert.*
import org.junit.Test
class DailyHomeSelectionTest {
 private val pool=(0..19).map{it.toString()}
 private fun pick(day:Long,saved:List<String> = emptyList(),previous:List<String> = emptyList())=DailyHomeSelection.select(pool,setOf("6","7","8","9"),day,saved,previous,{a,b->a.toInt()/2==b.toInt()/2},{_,_->0.0})
 @Test fun sameSceneCannotOccupyTwoCards(){val row=pick(12).filterNotNull();assertEquals(4,row.size);assertEquals(4,row.map{it.toInt()/2}.distinct().size);assertTrue(row[2] in setOf("6","7","8","9"))}
 @Test fun nextDayAvoidsAllPreviousScenes(){val first=pick(12).filterNotNull();val next=pick(13,previous=first).filterNotNull();assertTrue(next.none{n->first.any{it.toInt()/2==n.toInt()/2}})}
 @Test fun sameDayRetainsTheWholeRow(){val first=pick(12).filterNotNull();assertEquals(first,pick(12,saved=first))}
 @Test fun wideCardsPreferFitEvenWhenPortraitsAreNewerOrMoreColourful(){
  val row=DailyHomeSelection.select(listOf("wide1","wide2","portrait1","portrait2"),emptySet(),4,emptyList(),listOf("wide1","wide2"),{a,b->a==b},{id,_->if(id.startsWith("wide"))0.0 else 1.0},{_,_->0.0})
  assertTrue(row[0]!!.startsWith("wide"));assertTrue(row[1]!!.startsWith("wide"));assertNotEquals(row[0],row[1])
 }
 @Test fun emptyFavouritesNeverUsesAnArbitraryPhoto(){assertNull(DailyHomeSelection.select(listOf("a","b"),emptySet(),1,emptyList(),emptyList(),{a,b->a==b},{_,_->0.0})[2])}
}
