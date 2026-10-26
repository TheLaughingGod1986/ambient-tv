package org.ambienttv.home
import org.junit.Test
import org.junit.Assert.*
class PhotoPoolPolicyTest {
 @Test fun expandedGenerationMustBeCompleteAndLegacyCacheSurvives() {
  assertTrue(ImagePolicy.completeGeneration(12,12));assertTrue(ImagePolicy.completeGeneration(48,48));assertFalse(ImagePolicy.completeGeneration(48,12));assertFalse(ImagePolicy.completeGeneration(49,49))
 }
 @Test fun newestOrderPersistsAndAll48AreReachable() {
  val names=(1..48).map{"100-$it.jpg"};val order=names.reversed()
  assertEquals(order,PhotoPoolPolicy.ordered(names+listOf("99-1.jpg"),"100-",order))
  assertEquals(order,TriptychPolicy.anchors(order).flatMap {TriptychPolicy.group(order,it)})
 }
 @Test fun shortScreensaverSessionsDoNotRepeatOnlyFirstPictures() {
  var saved=0;val seen=mutableListOf<Int>()
  repeat(16){repeat(3){seen.add(saved);saved=PhotoPoolPolicy.nextCursor(saved,1,48)}}
  assertEquals((0..47).toList(),seen);assertEquals(0,saved)
 }
 @Test fun newCatalogStartsAtNewestAndBadSavedPathsAreIgnored() {
  assertEquals(0,PhotoPoolPolicy.startCursor("old","new",35,48));assertEquals(35,PhotoPoolPolicy.startCursor("same","same",35,48))
  assertEquals(listOf("100-1.jpg"),PhotoPoolPolicy.ordered(listOf("100-1.jpg","../bad.jpg"),"100-",listOf("../bad.jpg")))
 }
}
