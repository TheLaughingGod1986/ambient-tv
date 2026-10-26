package org.ambienttv.home
import org.junit.Test
import org.junit.Assert.*
import kotlin.random.Random
import kotlin.math.abs
class DreamPolicyTest {
 private val portrait=PhotoSize(600,900)
 private val landscape=PhotoSize(1920,1080)
 private val square=PhotoSize(1000,1000)
 @Test fun autoChoosesOneTwoAndThreeUsingAllCandidateAspects() {
  assertEquals(1,DreamPolicy.chooseCount(listOf(landscape,portrait,portrait),0))
  assertEquals(2,DreamPolicy.chooseCount(listOf(square,square,landscape),0))
  assertEquals(3,DreamPolicy.chooseCount(listOf(portrait,portrait,portrait),0))
  // Same leading portrait, different following images: genuinely choose two instead of always three.
  assertEquals(2,DreamPolicy.chooseCount(listOf(portrait,landscape,landscape),0))
 }
 @Test fun smallCachesManualModesAndInvalidBoundsRemainSafe() {
  assertEquals(0,DreamPolicy.chooseCount(emptyList(),0))
  assertEquals(1,DreamPolicy.chooseCount(listOf(portrait),3))
  assertEquals(2,DreamPolicy.chooseCount(listOf(square,square),3))
  for(mode in 1..3) assertEquals(mode,DreamPolicy.chooseCount(listOf(landscape,portrait,square),mode))
  assertEquals(1,DreamPolicy.chooseCount(listOf(PhotoSize(-1,0)),0))
 }
 @Test fun coherentPrefixGroupsAdvanceWithoutWeatherBasedReshuffling() {
  val photos=listOf(landscape,portrait,landscape,landscape,portrait,portrait,portrait)
  var start=0;val seen=mutableListOf<Int>()
  while(start<photos.size) {
   val candidates=DreamPolicy.indices(photos.size,start,3)
   val count=DreamPolicy.chooseCount(candidates.map { photos[it] },0)
   val selected=candidates.take(count)
   assertEquals(selected.size,selected.distinct().size)
   seen.addAll(selected.filter { it>=start });start+=count
  }
  assertEquals(photos.indices.toList(),seen)
 }
 @Test fun overflowAndSmallSetsNeverDuplicateWithinAFrame() {
  for(size in 1..12) for(start in listOf(Int.MIN_VALUE,-1,0,11,Int.MAX_VALUE)) {
   val selected=DreamPolicy.indices(size,start,3)
   assertEquals(minOf(3,size),selected.size);assertEquals(selected.size,selected.distinct().size)
   assertTrue(selected.all { it in 0 until size })
  }
  assertEquals(emptyList<Int>(),DreamPolicy.indices(0,0,3))
 }
 @Test fun autoAreaIsNearTheBestAvailableLayoutForRandomAspectMixes() {
  val random=Random(51)
  repeat(1000) {
   val sizes=List(random.nextInt(1,4)) { PhotoSize(random.nextInt(1,20001),random.nextInt(1,20001)) }
   val chosen=DreamPolicy.chooseCount(sizes,0)
   val actual=DreamPolicy.coverage(sizes,chosen)
   assertTrue(actual.isFinite() && actual in 0.0..1.0)
   for(columns in 1..sizes.size) assertTrue(actual+DreamPolicy.NEAR_TIE+1e-9>=DreamPolicy.coverage(sizes,columns))
   assertEquals(chosen,DreamPolicy.chooseCount(sizes,0))
  }
 }
 @Test fun productionContainGeometryPreservesAspectAndBoundsAndDecodeCap() {
  val random=Random(18)
  repeat(1000) {
   val w=random.nextInt(1,20001);val h=random.nextInt(1,20001)
   val columns=random.nextInt(1,4)
   val boxWidth=(DreamPolicy.BAND_WIDTH-DreamPolicy.GAP*(columns-1))/columns
   val fit=ContainGeometry.fit(w,h,boxWidth,DreamPolicy.BAND_HEIGHT)
   assertTrue(fit.width<=boxWidth+1e-6 && fit.height<=DreamPolicy.BAND_HEIGHT+1e-6)
   assertEquals(w.toDouble()/h,fit.width/fit.height,1e-7)
   assertTrue(abs(fit.width-boxWidth)<1e-6 || abs(fit.height-DreamPolicy.BAND_HEIGHT)<1e-6)
   val sample=BitmapPolicy.sampleSize(w,h)
   assertTrue((w+sample-1)/sample<=1920 && (h+sample-1)/sample<=1080)
  }
 }
}
