package org.ambienttv.home
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class HomePhotoPolicyTest {
 @Test fun portraitsSquareAndPanoramaKeepTheirEntireImage() {
  for((w,h) in listOf(900 to 1600,900 to 1200,900 to 900,3200 to 600)) {
   val f=HomePhotoPolicy.frame(w,h,396,366)
   assertEquals(w.toFloat()/h,f.width/f.height,.0001f)
   assertTrue(f.left>=0 && f.top>=0 && f.left+f.width<=396.01 && f.top+f.height<=366.01)
  }
 }
 @Test fun landscapesCoverWithoutStretchingAndRespectFocalPoint() {
  val center=HomePhotoPolicy.frame(1600,900,396,366)
  val right=HomePhotoPolicy.frame(1600,900,396,366,1f,.4f)
  assertEquals(1600f/900,center.width/center.height,.0001f)
  assertTrue(center.width>=396 && center.height>=366)
  assertTrue(right.left<center.left)
 }
 @Test fun deterministicSelectionsAvoidPreviousDayWherePoolAllows() {
  val pool=(1..48).map{it.toString()};val day=20000L
  val first=HomePhotoPolicy.choose(pool,day,3)
  assertEquals(first,HomePhotoPolicy.choose(pool.reversed(),day,3))
  val next=HomePhotoPolicy.choose(pool,day+1,3,previous=first)
  assertEquals(3,next.distinct().size);assertTrue(first.none{it in next})
  assertEquals(first,HomePhotoPolicy.choose(pool+"49",day,3,sameDay=first))
 }
 @Test fun tinyEmptyOrChangedPoolsHaveNoFakeOrDuplicatePhotos() {
  assertTrue(HomePhotoPolicy.choose(emptyList(),3,3).isEmpty())
  assertEquals(listOf("1"),HomePhotoPolicy.choose(listOf("1","1"),3,3))
  val result=HomePhotoPolicy.choose(listOf("1","2"),4,3,sameDay=listOf("gone","1"))
  assertEquals(2,result.size);assertEquals("1",result.first())
 }
 @Test fun localMidnightAndBothDstTransitionsUseChosenCalendarDays() {
  fun day(s:String)=HomePhotoPolicy.localDay(Instant.parse(s).toEpochMilli(),java.util.TimeZone.getTimeZone("Europe/Paris"))
  assertEquals(day("2026-07-01T21:59:59Z")+1,day("2026-07-01T22:00:00Z"))
  assertEquals(day("2026-03-29T00:30:00Z"),day("2026-03-29T02:30:00Z"))
  assertEquals(day("2026-10-25T00:30:00Z"),day("2026-10-25T01:30:00Z"))
  assertEquals(day("2026-10-25T22:59:59Z")+1,day("2026-10-25T23:00:00Z"))
 }
 @Test fun allFourPhotosFiveVideosAndFiveDockTargetsFitAtEveryTvSize() {
  for((width,height) in listOf(1280 to 720,1920 to 1080,3840 to 2160)) {
   val scale=HomeLayout.scale(width,height)
   assertTrue((HomeLayout.LEFT+3*(HomeLayout.PHOTO_W+HomeLayout.GAP)+HomeLayout.PHOTO_W)*scale<=width-20*scale)
   assertTrue((HomeLayout.LEFT+4*333+HomeLayout.MEDIA_W)*scale<=width-20*scale)
   assertTrue((HomeLayout.PHOTO_Y+HomeLayout.PHOTO_H+16)*scale<=HomeLayout.MEDIA_HEADING_Y*scale)
   assertTrue((HomeLayout.MEDIA_HEADING_Y+62+8)*scale<=HomeLayout.MEDIA_Y*scale)
   assertTrue((HomeLayout.MEDIA_Y+HomeLayout.MEDIA_H)*scale<HomeLayout.DOCK_Y*scale)
   assertTrue((HomeLayout.DOCK_Y+HomeLayout.DOCK_H)*scale<=height-32*scale)
  }
 }
}
