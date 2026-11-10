package org.ambienttv.home
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale
class PhotoCaptionTest {
 @Test fun formatsRealCaptureDatesAndLeavesUnknownBlank(){
  assertEquals("October 2024",PhotoCaption.monthYear(1728734400L,Locale.UK))
  assertEquals("12 October 2024",PhotoCaption.fullDate(1728734400L,Locale.UK))
  assertNull(PhotoCaption.monthYear(0,Locale.UK));assertNull(PhotoCaption.fullDate(-5,Locale.UK))
 }
 @Test fun readsTheTimeForACachedFileNameOnly(){
  val times=org.json.JSONObject().put("77",1728734400L)
  assertEquals(1728734400L,PhotoCaption.seconds("1700000000-77.jpg",times))
  assertEquals(0L,PhotoCaption.seconds("1700000000-78.jpg",times))
 }
 @Test fun extraPhotoIsStableForADayAndNeverOneAlreadyShown(){
  val all=listOf("g-1","g-2","g-3","g-4","g-5");val shown=listOf("g-1","g-2",null,"g-3")
  val first=PhotoCaption.extra(all,shown,20000L)
  assertEquals(first,PhotoCaption.extra(all.reversed(),shown,20000L))
  assertTrue(first in listOf("g-4","g-5"))
  assertNotEquals(first,PhotoCaption.extra(all,shown,20001L))
  assertNull(PhotoCaption.extra(listOf("g-1"),listOf("g-1"),1L))
 }
}
