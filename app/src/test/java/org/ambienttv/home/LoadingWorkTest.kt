package org.ambienttv.home
import org.junit.Assert.*
import org.junit.Test
class LoadingWorkTest {
 @Test fun bytesStayBoundedAndRecentlyUsedPostersSurviveEviction(){
  val cache=BoundedTtlCache<String,ByteArray>(8,100){it.size}
  cache.put("a",ByteArray(4),0);cache.put("b",ByteArray(4),0)
  assertNotNull(cache.get("a",1));cache.put("c",ByteArray(4),1)
  assertNull(cache.get("b",2));assertNotNull(cache.get("a",2));assertNotNull(cache.get("c",2));assertEquals(8,cache.bytes)
 }
 @Test fun expiryIsBoundedDoesNotSlideOnReadsAndRejectsClockRollback(){
  val cache=BoundedTtlCache<String,ByteArray>(8,100){it.size}
  cache.put("a",ByteArray(4),100);assertNotNull(cache.get("a",199));assertNull(cache.get("a",200));assertEquals(0,cache.bytes)
  cache.put("b",ByteArray(4),100);assertNull(cache.get("b",99));assertEquals(0,cache.bytes)
 }
 @Test fun oversizedArtworkIsNotRetainedAndClearReleasesEntries(){
  val cache=BoundedTtlCache<String,ByteArray>(8,100){it.size}
  cache.put("a",ByteArray(4),0);cache.put("huge",ByteArray(9),0)
  assertEquals(4,cache.bytes);assertNull(cache.get("huge",1))
  cache.put("a",ByteArray(2),1);assertEquals(2,cache.bytes);cache.clear();assertNull(cache.get("a",1));assertEquals(0,cache.bytes)
 }
 @Test fun pendingPhotoRequestsQueueOnlyOnceAndFailureCanRetry(){
  val request=PhotoRequestState();assertTrue(request.request("a",false));val first=request.version
  repeat(100){assertFalse(request.request("a",false))};assertEquals(first,request.version)
  request.complete();assertTrue(request.request("a",false));request.complete();assertFalse(request.request("a",true))
 }
 @Test fun changedPhotoAndClearedViewsInvalidateOldRequestsWithoutReloadingEmptySlots(){
  val request=PhotoRequestState();request.request("a",false);val old=request.version
  assertTrue(request.request("b",false));assertTrue(request.version>old)
  request.clear();val cleared=request.version;assertTrue(request.request("b",false));assertTrue(request.version>cleared)
  assertTrue(request.request(null,false));request.complete();assertFalse(request.request(null,false))
 }
}
