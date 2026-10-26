package org.ambienttv.home
import org.junit.Test
import org.junit.Assert.*
import kotlin.random.Random
class HeroGeometryTest {
 @Test fun heroAlwaysPreservesFullSourceAndFitsViewport() {
  val random=Random(83)
  repeat(1000){
   val w=random.nextInt(1,20001);val h=random.nextInt(1,20001);val fit=HeroGeometry.fit(w,h)
   assertTrue(fit.left>=0 && fit.top>=0 && fit.left+fit.width<=1920.00001 && fit.top+fit.height<=1080.00001)
   assertEquals(w.toDouble()/h,fit.width/fit.height,1e-7)
   if(w.toDouble()/h<1.3)assertTrue(fit.left+fit.width<=1440.00001)
  }
 }
 @Test fun previewWindowAlwaysContainsCurrentWithoutDuplicates() {
  for(size in 1..48)for(current in -2..100){val ids=HeroGeometry.previewIndices(size,current);assertEquals(minOf(3,size),ids.size);assertEquals(ids.size,ids.distinct().size);assertTrue(Math.floorMod(current,size) in ids)}
 }
 @Test fun thumbnailDecodeIsBoundedIndependentlyOfHero() {
  for((w,h) in listOf(20000 to 20000,6000 to 4000,4000 to 6000)){
   val sample=BitmapPolicy.sampleSize(w,h,320,300);assertTrue((w+sample-1)/sample<=320 && (h+sample-1)/sample<=300)
  }
 }
}
