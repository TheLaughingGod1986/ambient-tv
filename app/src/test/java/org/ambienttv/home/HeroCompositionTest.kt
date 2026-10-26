package org.ambienttv.home
import org.junit.Assert.*
import org.junit.Test
class HeroCompositionTest {
 @Test fun faceCompositionPreservesAspectAndFitsFace(){val f=FaceRegion(200f,200f,600f,800f);val r=HeroComposition.place(800,1200,f);val scale=r.width/800;assertEquals(800f/1200,r.width/r.height,.001f);assertTrue(r.top+f.top*scale>=0);assertTrue(r.top+f.bottom*scale<=1080);assertTrue(r.width>1100f);assertTrue(r.height>1080f)}
 @Test fun unknownPortraitKeepsOriginalUncropped(){val r=HeroComposition.place(800,1200,null);assertEquals(720f,r.width,.001f);assertEquals(1080f,r.height,.001f)}
 @Test fun landscapeFillsWithoutDistortion(){val r=HeroComposition.place(1920,1080,null);assertEquals(1920f,r.width,.001f);assertEquals(1080f,r.height,.001f)}
 @Test fun tightPortraitRetainsWholeFace(){val f=FaceRegion(0f,0f,800f,1200f);val r=HeroComposition.place(800,1200,f);assertEquals(720f,r.width,.001f);assertEquals(0f,r.top,.001f)}
 @Test fun faceNearTopAndBottomRemainsVisible(){
  for(top in listOf(0f,100f,700f)){
   val f=FaceRegion(100f,top,600f,top+450f);val r=HeroComposition.place(800,1200,f);val scale=r.width/800
   assertTrue(r.top+f.top*scale>=-.001f);assertTrue(r.top+f.bottom*scale<=1080.001f)
   assertTrue(r.top<=0);assertTrue(r.top+r.height>=1080)
  }
 }
}
