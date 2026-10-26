package org.ambienttv.home
import org.junit.Assert.*
import org.junit.Test
class FramingPolicyTest {
 @Test fun fullPhotoNeverCutsAnyEdge(){for((w,h) in listOf(600 to 1200,1200 to 600,900 to 900,3000 to 500)){val p=FramingPolicy.place(w,h,null,"full");assertTrue(p.left>=0 && p.top>=0);assertTrue(p.left+p.width<=1920.01);assertTrue(p.top+p.height<=1080.01);assertEquals(w.toFloat()/h,p.width/p.height,.001f)}}
 @Test fun manualPositionsPreserveAspectAndAnchorChosenEdge(){val top=FramingPolicy.place(600,1200,null,"top");val bottom=FramingPolicy.place(600,1200,null,"bottom");assertEquals(0f,top.top,0f);assertEquals(1080f,bottom.top+bottom.height,.001f);assertEquals(top.width,bottom.width,0f)}
 @Test fun invalidModeCannotSilentlyCrop(){assertThrows(IllegalArgumentException::class.java){FramingPolicy.place(600,1200,null,"unknown")}}
}
