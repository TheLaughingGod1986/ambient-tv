package org.ambienttv.home
import org.junit.Assert.*
import org.junit.Test
class HomePhotoSimilarityTest {
 @Test fun duplicatesAndSmallExposureDifferencesAreSimilar() {
  val a=IntArray(144){i->val n=i%120; (n shl 16) or ((n+20) shl 8) or (n+40)}
  assertTrue(HomePhotoSimilarity.similar(a,a.copyOf()))
  assertTrue(HomePhotoSimilarity.similar(a,a.map{it+0x080808}.toIntArray()))
 }
 @Test fun reframedSceneColourDistributionStillMatches() {
  val a=IntArray(144){if(it<80)0x2040c0 else 0xd08040}
  assertTrue(HomePhotoSimilarity.similar(a,a.reversedArray()))
 }
 @Test fun differentAnglesOfAColourfulSceneAreStillRelated() {
  // Same blue mat and warm clothing, with altered framing/exposure and more background.
  val a=IntArray(144){when{it<80->0x2040c0;it<120->0xd08040;else->0x203020}}
  val b=IntArray(144){when{it<65->0x3868df;it<100->0xe0a068;else->0x284830}}
  assertTrue(HomePhotoSimilarity.similar(a,b))
 }
 @Test fun differentScenesAreNotDuplicates() {
  assertFalse(HomePhotoSimilarity.similar(IntArray(144){0x204080},IntArray(144){0xe0b080}))
  assertFalse(HomePhotoSimilarity.similar(intArrayOf(),intArrayOf()))
 }
}
