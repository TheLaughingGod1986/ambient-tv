package org.ambienttv.home
import org.junit.Assert.*
import org.junit.Test
class SourceMixPolicyTest {
 @Test fun mixesBothSourcesAndPreservesTheirOrder(){assertEquals(listOf(0,4,1,5,2),SourceMixPolicy.indices(listOf("youtube","youtube","youtube","youtube","plex","plex"),5))}
 @Test fun startsWithMostRecentAvailableSource(){assertEquals(listOf(0,2,1,3),SourceMixPolicy.indices(listOf("plex","plex","youtube","youtube"),5))}
 @Test fun usesOnlyRealAvailableItems(){assertEquals(listOf(0,1),SourceMixPolicy.indices(listOf("youtube","youtube"),5));assertTrue(SourceMixPolicy.indices(emptyList(),5).isEmpty())}
 @Test fun zeroLimitIsEmpty(){assertTrue(SourceMixPolicy.indices(listOf("plex"),0).isEmpty())}
 @Test fun nextEpisodeSourceGetsThreeSlotsWithoutChangingItsOrder(){
  assertEquals(listOf(3,0,4,1,5),SourceMixPolicy.indices(listOf("youtube","youtube","youtube","plex","plex","plex"),5,"plex"))
 }
}
