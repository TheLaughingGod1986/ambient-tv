package org.ambienttv.home
import org.junit.Assert.*
import org.junit.Test

class BitmapPolicyTest {
 @Test fun largeAndAwkwardImagesRemainInsideDecodeBudget() {
  for((width,height) in listOf(20000 to 20000,15361 to 8641,6000 to 4000,4000 to 6000,1921 to 1080,20000 to 1,1 to 20000)) {
   val sample=BitmapPolicy.sampleSize(width,height)
   val decodedWidth=(width+sample-1)/sample; val decodedHeight=(height+sample-1)/sample
   assertTrue(decodedWidth<=1920); assertTrue(decodedHeight<=1080)
   assertTrue(decodedWidth.toLong()*decodedHeight*4<=1920L*1080*4)
  }
 }
 @Test fun corruptDimensionsFailBeforeAllocatingBitmap() {
  for((width,height) in listOf(-1 to 100,0 to 100,100 to 0,20001 to 1,Int.MAX_VALUE to Int.MAX_VALUE))
   assertThrows(IllegalArgumentException::class.java) { BitmapPolicy.sampleSize(width,height) }
 }
 @Test fun incompleteGenerationsCannotReuseSuccessfulRefreshDelay() {
  assertFalse(ImagePolicy.completeGeneration(12,0))
  assertFalse(ImagePolicy.completeGeneration(12,11))
  assertTrue(ImagePolicy.completeGeneration(12,12))
  assertTrue(ImagePolicy.completeGeneration(3,3))
  assertTrue(ImagePolicy.completeGeneration(0,12)) // Existing 0.1 cache has no count metadata.
  assertFalse(ImagePolicy.completeGeneration(0,0))
 }
}
