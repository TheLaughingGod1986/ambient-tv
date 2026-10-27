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
 @Test fun differentScenesAreNotDuplicates() {
  assertFalse(HomePhotoSimilarity.similar(IntArray(144){0x204080},IntArray(144){0xe0b080}))
  assertFalse(HomePhotoSimilarity.similar(intArrayOf(),intArrayOf()))
 }
}
