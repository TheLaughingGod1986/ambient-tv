package org.ambienttv.home
import org.junit.Assert.*
import org.junit.Test
class HeroFadePolicyTest {
 @Test fun portraitTransitionContinuesBeyondWholePhotoInsteadOfCroppingIt(){
  val f=HeroFadePolicy.transition(809f,809f)!!
  assertEquals(809f,f.peak,0f);assertTrue(f.start in 709f..729f);assertEquals(1049f,f.end,0f)
 }
 @Test fun fullWidthLandscapeNeedsNoArtificialSeam(){assertNull(HeroFadePolicy.transition(1920f,1920f))}
 @Test fun transitionStaysInsideCanvas(){val f=HeroFadePolicy.transition(1800f,1800f)!!;assertEquals(1920f,f.end,0f);assertTrue(f.start<f.peak && f.peak<f.end)}
 @Test fun emptyGeometryDoesNotCreateAnInvalidGradient(){assertNull(HeroFadePolicy.transition(0f,0f));assertNull(HeroFadePolicy.transition(10f,0f))}
 @Test fun faceAtRightEdgeLimitsInsideFade(){val f=HeroFadePolicy.transition(809f,809f,protectRight=790f)!!;assertTrue(f.start>=790f);assertEquals(1049f,f.end,0f)}
}
