package org.ambienttv.home
import org.junit.Assert.*
import org.junit.Test
class ResumeFeedPolicyTest {
 @Test fun debouncedRequestsUseStrongestForceAndNeverRunInParallel(){
  val s=FeedRefreshState();s.request(false);s.request(true);s.request(false)
  assertEquals(true,s.begin(true));assertNull(s.begin(true));assertTrue(s.finish());assertFalse(s.hasPending)
 }
 @Test fun requestDuringReadQueuesOneFreshFollowupAndRejectsOldCommit(){
  val s=FeedRefreshState();s.request(false);assertEquals(false,s.begin(true))
  repeat(10){s.request(true)};assertNull(s.begin(true));assertFalse(s.finish())
  assertEquals(true,s.begin(true));assertTrue(s.finish());assertNull(s.begin(true))
 }
 @Test fun pauseCancelsTimersButResumeForceSurvivesAnOldInFlightRead(){
  val s=FeedRefreshState();s.request(true);s.pause();assertNull(s.begin(false));assertNull(s.begin(true))
  s.request(false);s.begin(true);s.pause();s.request(true);assertNull(s.begin(true));assertFalse(s.finish());assertEquals(true,s.begin(true))
 }
 @Test fun metadataTtlExpiresAndNeverTreatsClockRollbackAsFresh(){
  assertTrue(ResumeFeedPolicy.fresh(159999,100000,60000));assertFalse(ResumeFeedPolicy.fresh(160000,100000,60000));assertFalse(ResumeFeedPolicy.fresh(99999,100000,60000));assertFalse(ResumeFeedPolicy.fresh(1,0,60000))
 }
 @Test fun plexSecondsBecomeUtcMillisWithoutTimezoneOrOverflow(){
  assertEquals(1792891800000,ResumeFeedPolicy.plexMillis(1792891800));assertEquals(0,ResumeFeedPolicy.plexMillis(-1));assertEquals(0,ResumeFeedPolicy.plexMillis(Long.MAX_VALUE))
 }
 @Test fun failuresKeepSourceButSuccessfulEmptySnapshotsRemoveCompletedItems(){
  val source=LastGoodSource<Int>();assertEquals(listOf(1,2),source.update(listOf(1,2)));assertEquals(listOf(1,2),source.update(null));assertTrue(source.update(emptyList()).isEmpty())
 }
 @Test fun focusFollowsItemAcrossRecencyReorderOrNearestSurvivingNeighbour(){
  assertEquals(2,ResumeFeedPolicy.focusIndex("a",0,listOf("b","c","a")));assertEquals(1,ResumeFeedPolicy.focusIndex("gone",4,listOf("b","c")));assertEquals(-1,ResumeFeedPolicy.focusIndex("a",0,emptyList()));assertEquals(-1,ResumeFeedPolicy.focusIndex(null,-1,listOf("a")))
 }
 @Test fun sourceMergeUsesComparableRecencyAndDoesNotLoseYouTubeForPlex(){
  fun item(owner:String,id:String,time:Long)=WatchItem("Same title",owner,id,"",1,100000,time)
  val yt=item("com.google.android.youtube.tv","yt",1000)
  val nativePlex=item("com.plexapp.android","native",500)
  val direct=item("com.plexapp.android","direct",2000)
  assertEquals(listOf(direct,yt),WatchNextReader.merge(listOf(yt,nativePlex),listOf(direct)))
  assertEquals(listOf(yt,nativePlex),WatchNextReader.merge(listOf(yt,nativePlex),emptyList()))
 }
 @Test fun distinctEpisodesWithSameTitleRemainDistinctAndProviderRowIdentityDeduplicates(){
  val a=WatchItem("Same","youtube","a","",1,100000,1000,1)
  val b=a.copy(intentUri="b",providerRowId=2)
  assertEquals(2,WatchNextReader.merge(listOf(a,b),emptyList()).size)
  assertEquals(1,WatchNextReader.merge(listOf(a,a.copy(position=20)),emptyList()).size)
 }
 @Test fun mixedFeedPreservesPlexNextOrderWithoutInventingRecency(){
  val next=WatchItem("Next","com.plexapp.android","next","",0,90000,0,nextEpisode=true)
  val resume=next.copy(title="Resume",intentUri="resume",position=10000,engagedAtMillis=2000,nextEpisode=false)
  val yt=next.copy(owner="com.google.android.youtube.tv",intentUri="yt",engagedAtMillis=1000,nextEpisode=false)
  val result=WatchNextReader.merge(listOf(yt),listOf(next,resume,next))
  assertEquals(listOf(next,resume),result.filter{it.owner=="com.plexapp.android"})
  assertTrue(result.contains(yt));assertEquals(0L,result.first{it.nextEpisode}.engagedAtMillis)
 }
 @Test fun emptyChosenPlexProfileNeverFallsBackToNativeOtherProfile(){
  val native=WatchItem("Other profile","com.plexapp.android","native","",1,90000)
  val yt=native.copy(owner="com.google.android.youtube.tv",intentUri="yt")
  assertEquals(listOf(yt),WatchNextReader.merge(listOf(native,yt),emptyList(),preferDirectPlex=true))
 }
}
