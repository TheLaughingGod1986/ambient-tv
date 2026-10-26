package org.ambienttv.home
import org.junit.Test
import org.junit.Assert.*
class MutedPaletteTest {
 @Test fun darkMutedColoursKeepTextContrastAndColourFamily() {
  for(c in listOf(0xff000000.toInt(),0xffffffff.toInt(),0xffff0000.toInt(),0xff00ff00.toInt(),0xff0000ff.toInt())) {
   val m=MutedPalette.muted(c,8)
   for(shift in listOf(0,8,16)) assertTrue((m ushr shift and 255) in 12..70)
  }
  val blue=MutedPalette.muted(0xff0000ff.toInt());assertTrue((blue and 255)>(blue ushr 16 and 255))
 }
 @Test fun averageIsBoundedAndEmptyInputSafe() {
  assertEquals(0xff7f007f.toInt(),MutedPalette.average(intArrayOf(0xffff0000.toInt(),0xff0000ff.toInt())))
  assertEquals(0xff18222a.toInt(),MutedPalette.average(intArrayOf()))
 }
 @Test fun reducedMotionAndPowerSavingDisableMotion() {
  assertFalse(MutedPalette.motionAllowed(0f,false));assertFalse(MutedPalette.motionAllowed(1f,true));assertTrue(MutedPalette.motionAllowed(.5f,false))
 }
}
