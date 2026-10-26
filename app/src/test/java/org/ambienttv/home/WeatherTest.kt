package org.ambienttv.home

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class WeatherTest {
 private val now=1792516800000L
 private fun response(temp: String="13.6",unit: String="°C",time: Long=now/1000,code: Int=3)= """{"current_units":{"temperature_2m":"$unit"},"current":{"time":$time,"temperature_2m":$temp,"weather_code":$code,"is_day":1}}"""
 @Test fun parsesCelsiusInSelectedTimezone() {
  val data=WeatherData.parse(response(),now)
  val panel=WeatherPanel.from(data,now,zone=java.util.TimeZone.getTimeZone("UTC"))
  assertEquals("14°C",panel.temperature); assertEquals("Overcast",panel.condition)
  assertEquals("Updated 17:20",panel.freshness)
  assertEquals(data,WeatherData.stored(data.json()))
 }
 @Test fun invalidWeatherCannotReplaceValidCache() {
  assertThrows(IllegalArgumentException::class.java) { WeatherData.parse(response(unit="°F"),now) }
  assertThrows(IllegalArgumentException::class.java) { WeatherData.parse(response(temp="100"),now) }
  assertThrows(IllegalArgumentException::class.java) { WeatherData.parse(response(time=now/1000+3600),now) }
 }
 @Test fun offlineAndStaleAreVisibleAndOldTemperaturesDisappear() {
  val data=WeatherData.parse(response(),now)
  assertTrue(WeatherPanel.from(data,now+60000,true).freshness.endsWith("offline"))
  assertEquals("—",WeatherPanel.from(data,now+2*60*60*1000).temperature)
  assertEquals("Weather unavailable",WeatherPanel.from(data,now+2*60*60*1000).condition)
  assertEquals("Weather unavailable",WeatherPanel.from(data,now+7*60*60*1000).condition)
  assertEquals("—",WeatherPanel.from(data,now+7*60*60*1000).temperature)
  assertEquals("Weather unavailable",WeatherPanel.from(null,now).condition)
 }
 @Test fun freezingRainThunderstormsAndUnknownCodesHaveSafeLabels() {
  assertEquals("Freezing rain",WeatherData.parse(response(code=67),now).condition())
  assertEquals("Thunderstorm with hail",WeatherData.parse(response(code=99),now).condition())
  val unknown=WeatherData.parse(response(code=44),now)
  assertEquals("Conditions unavailable",unknown.condition())
  assertNull(WeatherPanel.from(unknown,now).code)
 }
 @Test fun panelReservesThreeRowsAndAvoidsNativeClockAndExportsSyntheticSpec() {
  assertTrue(WeatherLayout.LEFT>=WeatherLayout.WIDTH*.05f)
  assertTrue(WeatherLayout.TOP>=54f)
  assertTrue(WeatherLayout.RIGHT<1309f)
  assertTrue(WeatherLayout.BOTTOM<WeatherLayout.PHOTO_TOP)
  assertTrue(WeatherLayout.PHOTO_TOP<WeatherLayout.PHOTO_BOTTOM)
  assertTrue(WeatherLayout.PHOTO_BOTTOM<=694f)
  assertTrue(WeatherLayout.RIGHT<=1000f)
  assertTrue(WeatherLayout.BOTTOM-WeatherLayout.TOP<=64f)
  val panel=WeatherPanel.from(WeatherData.parse(response(),now),now)
  System.getenv("WEATHER_PREVIEW_SPEC")?.let { path ->
   java.io.File(path).writeText(JSONObject().put("left",WeatherLayout.LEFT).put("top",WeatherLayout.TOP).put("right",WeatherLayout.RIGHT).put("bottom",WeatherLayout.BOTTOM)
    .put("iconX",WeatherLayout.ICON_X).put("iconY",WeatherLayout.ICON_Y).put("textX",WeatherLayout.TEXT_X).put("locationY",WeatherLayout.LOCATION_Y).put("tempY",WeatherLayout.TEMP_Y).put("conditionY",WeatherLayout.CONDITION_Y)
    .put("freshnessY",WeatherLayout.FRESHNESS_Y).put("creditY",WeatherLayout.CREDIT_Y).put("credit",WeatherLayout.CREDIT)
    .put("temperature",panel.temperature).put("condition",panel.condition).put("freshness",panel.freshness).toString())
  }
 }
 @Test fun emptyCacheDoesNotInheritSuccessfulRefreshDelay() {
  assertTrue(RefreshPolicy.shouldFetch(now,0,now-60000,false))
  assertFalse(RefreshPolicy.shouldFetch(now,0,now-60000,true))
  assertFalse(RefreshPolicy.shouldFetch(now,now-60000,0,false))
  assertTrue(RefreshPolicy.shouldFetch(now,0,now+3600000,true))
 }
 @Test fun weatherUsesThirtyMinuteCacheAndFifteenMinuteFailureBackoff() {
  assertFalse(WeatherFetchPolicy.shouldFetch(now,now-29*60000,0))
  assertTrue(WeatherFetchPolicy.shouldFetch(now,now-30*60000,now-30*60000))
  assertFalse(WeatherFetchPolicy.shouldFetch(now,null,now-14*60000))
  assertTrue(WeatherFetchPolicy.shouldFetch(now,null,now-15*60000))
  assertTrue(WeatherFetchPolicy.shouldFetch(now,null,0))
 }
 @Test fun oldLauncherUrisResolveOnlyIntoCurrentPersonCache() {
  val available=setOf("100-2.jpg","200-3.jpg","200-4.jpg")
  assertEquals("100-2.jpg",ImagePolicy.resolve("100-2.jpg",available,listOf("200-3.jpg","200-4.jpg")))
  assertEquals("200-3.jpg",ImagePolicy.resolve("50-3.jpg",available,listOf("200-3.jpg","200-4.jpg")))
  assertTrue(ImagePolicy.resolve("50-9.jpg",available,listOf("200-3.jpg","200-4.jpg")) in setOf("200-3.jpg","200-4.jpg"))
  assertThrows(IllegalArgumentException::class.java) { ImagePolicy.resolve("../session.xml",available,listOf("200-3.jpg")) }
  assertThrows(java.io.FileNotFoundException::class.java) { ImagePolicy.resolve("50-1.jpg",emptySet(),emptyList()) }
 }
}
