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
 @Test fun favouritesStillOnTheNasKeepTheirPlaceUpToTheLimit(){
  assertEquals(listOf(30,12),PhotoPoolPolicy.keptFavourites(setOf("12.jpg","30.jpg","99.jpg","not-a-photo"),setOf(12,30,31)))
  val many=(1..40).map{"$it.jpg"}.toSet()
  val kept=PhotoPoolPolicy.keptFavourites(many,(1..40).toSet())
  assertEquals(PhotoPoolPolicy.FAVOURITES,kept.size);assertEquals(40,kept.first())
  assertTrue(PhotoPoolPolicy.keptFavourites(emptySet(),setOf(1)).isEmpty())
 }
 @Test fun pngScreenshotsAreLeftOutButCameraPhotosStay(){
  assertTrue(PhotoPoolPolicy.isScreenshot("IMG_1234.PNG"));assertTrue(PhotoPoolPolicy.isScreenshot("Screenshot 1.png "))
  for(name in listOf("IMG_1234.HEIC","IMG_1234.jpg","photo.jpeg","png.jpg",""))assertFalse(PhotoPoolPolicy.isScreenshot(name))
 }
 @Test fun spreadOrderUsesEveryPhotoOnceAndKeepsNeighboursApart(){
  for(count in 0..60){
   val order=PhotoPoolPolicy.spread(count)
   assertEquals((0 until count).toList(),order.sorted())
   if(count>=8)for(i in 1 until count){val gap=kotlin.math.abs(order[i]-order[i-1]);assertTrue("count $count step $i",minOf(gap,count-gap)>=count/4)}
  }
 }
}
