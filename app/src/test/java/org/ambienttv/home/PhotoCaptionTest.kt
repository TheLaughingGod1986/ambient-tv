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
 private fun day(text:String)=PhotoDatePolicy.parse(text)!!
 @Test fun ageGrowsFromDaysToWeeksMonthsAndYears(){
  val birth=day("2024-09-10")
  assertEquals("Newborn",PhotoCaption.age(birth+3600,birth))
  assertEquals("1 day old",PhotoCaption.age(day("2024-09-11"),birth))
  assertEquals("9 days old",PhotoCaption.age(day("2024-09-19"),birth))
  assertEquals("3 weeks old",PhotoCaption.age(day("2024-10-03"),birth))
  assertEquals("8 weeks old",PhotoCaption.age(day("2024-11-09"),birth))
  assertEquals("2 months old",PhotoCaption.age(day("2024-11-10"),birth))
  assertEquals("13 months old",PhotoCaption.age(day("2025-10-31"),birth))
  assertEquals("2 years old",PhotoCaption.age(day("2026-11-08"),birth))
  assertNull(PhotoCaption.age(day("2024-09-09"),birth));assertNull(PhotoCaption.age(day("2025-01-01"),null))
 }
 @Test fun anniversariesNeedTheSameDayInAnEarlierYear(){
  val london=java.util.TimeZone.getTimeZone("Europe/London")
  val now=day("2026-11-10")*1000+12*3600000
  assertEquals(2,PhotoCaption.yearsAgoToday(day("2024-11-10")+7200,now,london))
  assertNull(PhotoCaption.yearsAgoToday(day("2026-11-10"),now,london))
  assertNull(PhotoCaption.yearsAgoToday(day("2024-11-11"),now,london));assertNull(PhotoCaption.yearsAgoToday(0,now,london))
 }
 @Test fun captionsCombineDateAndAgeOnlyWhenKnown(){
  val now=day("2026-11-10")*1000+12*3600000;val birth=day("2024-09-10")
  assertEquals("October 2024  ·  3 weeks old",PhotoCaption.headline(day("2024-10-03"),now,birth,Locale.UK))
  assertEquals("October 2024",PhotoCaption.headline(day("2024-10-03"),now,null,Locale.UK))
  assertEquals("2 years ago today  ·  2 months old",PhotoCaption.headline(day("2024-11-10"),now,birth,Locale.UK))
  assertEquals("A year ago today",PhotoCaption.headline(day("2025-11-10"),now,null,Locale.UK))
  assertEquals("3 October 2024  ·  3 weeks old",PhotoCaption.detail(day("2024-10-03"),birth,Locale.UK))
  assertNull(PhotoCaption.headline(0,now,birth,Locale.UK));assertNull(PhotoCaption.detail(0,birth,Locale.UK))
  assertEquals("October 2024" to "3 weeks old",PhotoCaption.lines(day("2024-10-03"),now,birth,Locale.UK))
  assertEquals("October 2024" to null,PhotoCaption.lines(day("2024-10-03"),now,null,Locale.UK))
 }
}
