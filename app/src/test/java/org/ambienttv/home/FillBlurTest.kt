package org.ambienttv.home
import org.junit.Assert.*
import org.junit.Test
class FillBlurTest {
 private val black=0xFF000000.toInt();private val white=0xFFFFFFFF.toInt()
 @Test fun flatColourIsUnchanged(){
  val out=FillBlur.smooth(IntArray(12){0xFF336699.toInt()},4,3)
  assertTrue(out.all{it==0xFF336699.toInt()})
 }
 @Test fun hardChequerBecomesSoftAndStaysOpaque(){
  val w=8;val h=8
  val out=FillBlur.smooth(IntArray(w*h){if((it%w+it/w)%2==0)black else white},w,h)
  val levels=out.map{it and 255}
  assertTrue("neighbouring pixels should be close after smoothing",levels.max()-levels.min()<60)
  assertTrue(out.all{it ushr 24==255})
 }
 @Test fun inputIsNotModified(){
  val input=intArrayOf(black,white,white,black);val copy=input.copyOf()
  FillBlur.smooth(input,2,2);assertArrayEquals(copy,input)
 }
 @Test(expected=IllegalArgumentException::class)fun rejectsMismatchedSize(){FillBlur.smooth(IntArray(5),2,2)}
}
