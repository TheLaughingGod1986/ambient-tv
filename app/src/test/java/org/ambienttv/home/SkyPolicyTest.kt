package org.ambienttv.home
import org.junit.Assert.*
import org.junit.Test
class SkyPolicyTest {
 private val lat=51.5;private val lon=-0.12
 /** 17 November 2026 in London (UTC), at the given time. */
 private fun nov(h:Int,m:Int=0)=1794873600000L+h*3_600_000L+m*60_000L

 @Test fun weatherCodesMapToScenes(){
  assertEquals(SkyWeather.CLEAR,SkyWeather.of(0));assertEquals(SkyWeather.PARTLY_CLOUDY,SkyWeather.of(2));assertEquals(SkyWeather.OVERCAST,SkyWeather.of(3))
  assertEquals(SkyWeather.FOG,SkyWeather.of(45));assertEquals(SkyWeather.DRIZZLE,SkyWeather.of(53));assertEquals(SkyWeather.LIGHT_RAIN,SkyWeather.of(61))
  assertEquals(SkyWeather.RAIN,SkyWeather.of(63));assertEquals(SkyWeather.HEAVY_RAIN,SkyWeather.of(82));assertEquals(SkyWeather.SNOW,SkyWeather.of(73))
  assertEquals(SkyWeather.THUNDER,SkyWeather.of(95))
 }
 @Test fun unknownOrMissingWeatherIsAFairSkyNotAGuess(){
  assertEquals(SkyWeather.PARTLY_CLOUDY,SkyWeather.of(null));assertEquals(SkyWeather.PARTLY_CLOUDY,SkyWeather.of(42))
  assertEquals(0,SkyPolicy.particles(SkyWeather.of(null)))
 }
 @Test fun sunMatchesLondonInNovember(){
  assertEquals(19.4,SunPosition.at(nov(12),lat,lon).elevation,0.5)
  // Official sunrise and sunset are at -0.83 degrees: about 07:21 and 16:07 on this day.
  assertEquals(-0.83,SunPosition.at(nov(7,21),lat,lon).elevation,0.6)
  assertEquals(-0.83,SunPosition.at(nov(16,7),lat,lon).elevation,0.6)
  assertTrue(SunPosition.at(nov(9),lat,lon).morning);assertFalse(SunPosition.at(nov(14),lat,lon).morning)
 }
 @Test fun dayPartsFollowTheSun(){
  fun part(h:Int,m:Int)=SkyPolicy.scene(nov(h,m),lat,lon,0).part
  assertEquals(DayPart.NIGHT,part(5,0));assertEquals(DayPart.DAWN,part(7,45));assertEquals(DayPart.MORNING,part(9,40))
  assertEquals(DayPart.AFTERNOON,part(12,40));assertEquals(DayPart.SUNSET,part(15,45));assertEquals(DayPart.DUSK,part(16,30))
  assertEquals(DayPart.NIGHT,part(22,0))
 }
 @Test fun theSunStaysBetweenTheHeaderTextAndTheClock(){
  for(h in 0..23){val s=SkyPolicy.scene(nov(h),lat,lon,0);assertTrue(s.sunX in .45..0.82);assertTrue(s.sunY in 0.0..1.0)}
  val summer=SkyPolicy.scene(1782043200000L,lat,lon,0)
  assertTrue(summer.sunY<.2) // a high midsummer sun sits high in the strip
 }
 @Test fun theSceneIsRedrawnOnlyWhenSomethingVisibleChanges(){
  // Deep night: the light does not change, so the picture is kept.
  assertEquals(SkyPolicy.scene(nov(22),lat,lon,0).key,SkyPolicy.scene(nov(23),lat,lon,0).key)
  // Near sunset the sun moves quickly, so a quarter of an hour is a new picture.
  assertNotEquals(SkyPolicy.scene(nov(15,30),lat,lon,0).key,SkyPolicy.scene(nov(15,45),lat,lon,0).key)
  assertNotEquals(SkyPolicy.scene(nov(13),lat,lon,0).key,SkyPolicy.scene(nov(13),lat,lon,63).key)
 }
 @Test fun weatherAndHourChangeTheLight(){
  val clear=SkyPolicy.scene(nov(13),lat,lon,0).look;val rain=SkyPolicy.scene(nov(13),lat,lon,65).look;val night=SkyPolicy.scene(nov(22),lat,lon,0).look
  assertTrue(rain.brightness<clear.brightness);assertTrue(night.brightness<rain.brightness)
  assertEquals(0.0,rain.sunVisible,1e-9);assertTrue(clear.sunVisible>.9)
  assertTrue(night.stars>.5);assertEquals(0.0,SkyPolicy.scene(nov(22),lat,lon,3).look.stars,.1)
  // Overcast greys the sky: its blue channel stands out less than on a clear afternoon.
  fun blueness(c:Int)=SkyColor.b(c)-(SkyColor.r(c)+SkyColor.g(c))/2
  assertTrue(blueness(SkyPolicy.scene(nov(13),lat,lon,3).look.skyTop)<blueness(clear.skyTop))
  // Morning and afternoon at the same height of sun are distinct palettes.
  assertNotEquals(SkyPolicy.scene(nov(9,30),lat,lon,0).look.skyHorizon,SkyPolicy.scene(nov(14,50),lat,lon,0).look.skyHorizon)
  assertEquals(5,clear.ridges.size)
 }
 @Test fun motionIsBounded(){
  for(w in SkyWeather.values()){assertTrue(SkyPolicy.frameRate(w,false) in 10..30);assertTrue(SkyPolicy.frameRate(w,true) in 10..30);assertTrue(SkyPolicy.particles(w)<=210)}
  val r=java.util.Random(1);repeat(100){assertTrue(SkyPolicy.nextLightningMs(r) in 18_000L..45_000L)}
 }
 @Test fun moonPhaseFollowsTheCalendar(){
  assertEquals(.5,SunPosition.moonPhase(1706205240000L),.03) // full moon, 25 January 2024 17:54 UTC
  val a=SunPosition.moonPhase(nov(0));val b=SunPosition.moonPhase(nov(0)+86_400_000L)
  assertEquals(1/29.53,(b-a+1)%1,.001)
 }
 @Test fun fallbackLocationUsesTheZone(){
  val (fallbackLat,fallbackLon)=SunPosition.fallback(java.util.TimeZone.getTimeZone("Asia/Tokyo"),nov(0))
  assertEquals(45.0,fallbackLat,0.0);assertEquals(135.0,fallbackLon,0.01)
 }
 @Test fun backgroundModesCycleAndParse(){
  assertEquals(HomeSkyMode.LIVING,HomeSkyMode.of(null));assertEquals(HomeSkyMode.LIVING,HomeSkyMode.of("unknown"))
  assertEquals(HomeSkyMode.STILL,HomeSkyMode.LIVING.next());assertEquals(HomeSkyMode.PHOTO,HomeSkyMode.STILL.next());assertEquals(HomeSkyMode.LIVING,HomeSkyMode.PHOTO.next())
  for(m in HomeSkyMode.values())assertEquals(m,HomeSkyMode.of(m.key))
 }
}
