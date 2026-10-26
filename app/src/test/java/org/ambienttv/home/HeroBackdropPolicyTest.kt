package org.ambienttv.home
import org.junit.Assert.*
import org.junit.Test
class HeroBackdropPolicyTest {
 @Test fun portraitFillUsesBackgroundBelowDetectedFace(){val r=HeroBackdropPolicy.crop(800,1200,FaceRegion(40f,40f,760f,700f))!!;assertTrue(r.top>700);assertEquals(800,r.width);assertTrue(r.top+r.height<=1200)}
 @Test fun closeupCannotProduceEnlargedDuplicateFace(){assertNull(HeroBackdropPolicy.crop(800,1200,FaceRegion(0f,0f,800f,1200f)))}
 @Test fun landscapeRetainsWholeBackgroundWithoutStretching(){assertEquals(HeroCrop(0,0,1920,1080),HeroBackdropPolicy.crop(1920,1080,null))}
 @Test fun noFacePortraitUsesLowerTextureNotCentreFaceCrop(){val r=HeroBackdropPolicy.crop(800,1200,null)!!;assertEquals(900,r.top);assertEquals(300,r.height)}
}
