package org.ambienttv.home
import org.junit.Assert.*
import org.junit.Test
class HeroLayoutPolicyTest {
 @Test fun landscapeFillsAloneAndUprightPhotosShareTheScreen(){
  assertEquals(1,HeroLayoutPolicy.count(16f/9));assertEquals(1,HeroLayoutPolicy.count(4f/3))
  assertEquals(2,HeroLayoutPolicy.count(3f/4));assertEquals(2,HeroLayoutPolicy.count(1f))
  assertEquals(3,HeroLayoutPolicy.count(9f/16));assertEquals(1,HeroLayoutPolicy.count(0f))
 }
 @Test fun panelsCoverTheWholeWidthWithSlimGaps(){
  for(count in 1..3){
   val panels=HeroLayoutPolicy.panels(count)
   assertEquals(0,panels.first().first);assertEquals(1920,panels.last().first+panels.last().second)
   for(i in 1 until count)assertEquals(HeroLayoutPolicy.GAP,panels[i].first-(panels[i-1].first+panels[i-1].second))
  }
 }
 @Test fun commonPhoneShapesFillTheirPanelWithOnlyASmallTrim(){
  val pair=HeroLayoutPolicy.panels(2)[0].second
  val threeByFour=HomePhotoPolicy.featureFrame(900,1200,pair,1080,null)
  assertTrue(threeByFour.width>=pair-.5f && threeByFour.height>=1079.5f);assertTrue(1080/threeByFour.height>=.8f)
  val triple=HeroLayoutPolicy.panels(3)[0].second
  val nineBySixteen=HomePhotoPolicy.featureFrame(900,1600,triple,1080,null)
  assertTrue(nineBySixteen.width>=triple-.5f && nineBySixteen.height>=1079.5f);assertTrue(1080/nineBySixteen.height>=.9f)
 }
}
