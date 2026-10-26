package org.ambienttv.home
import org.junit.Assert.*
import org.junit.Test
class PhotoMixPolicyTest {
 @Test fun mixesRecentWithRotatingArchiveWithoutDuplicates() {
  val first=PhotoMixPolicy.select((100..147).toList(),(1..48).toList())
  val next=PhotoMixPolicy.select((100..147).toList(),(33..80).toList())
  assertEquals(48,first.size);assertEquals(48,first.distinct().size)
  assertEquals(listOf(100,1,2,101,3,4),first.take(6))
  assertEquals(16,first.count{it>=100});assertFalse(first==next)
 }
 @Test fun smallOverlappingLibraryIsStillComplete() {
  assertEquals(listOf(3,1,2),PhotoMixPolicy.select(listOf(3,1,2),listOf(1,2,3)))
 }
 @Test fun refreshIsDueAfterSixHoursAndRetriesAreThrottled() {
  val now=100000000L
  assertFalse(RefreshPolicy.shouldFetch(now,0,now-5*3600000L,true))
  assertTrue(RefreshPolicy.shouldFetch(now,0,now-6*3600000L,true))
  assertFalse(RefreshPolicy.shouldFetch(now,now-60000,0,false))
 }
}
