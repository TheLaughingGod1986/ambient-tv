package org.ambienttv.home
import org.junit.Assert.*
import org.junit.Test
class RetryPolicyTest {
 private val now=1_800_000_000_000L
 @Test fun quickTriesGrowFurtherApartAndThenStop(){
  assertNull(RetryPolicy.delay(0))
  val waits=(1..RetryPolicy.QUICK).map{RetryPolicy.delay(it)!!}
  assertEquals(4_000L,waits.first());assertEquals(waits.sorted(),waits);assertEquals(waits.size,waits.toSet().size)
  // Together they cover the minutes a TV can take to get its network back after standby.
  assertTrue(waits.sum()>=3*60_000L)
  assertNull(RetryPolicy.delay(RetryPolicy.QUICK+1))
 }
 @Test fun weatherIsAskedAgainWithinSecondsAfterAFailedFirstTry(){
  // The old rule: any failed try meant fifteen minutes without weather.
  assertFalse(WeatherFetchPolicy.shouldFetch(now,null,now-14*60000))
  // One failure, as on waking before the network is back: four seconds.
  assertFalse(WeatherFetchPolicy.shouldFetch(now,null,now-3_000,1))
  assertTrue(WeatherFetchPolicy.shouldFetch(now,null,now-4_000,1))
  assertFalse(WeatherFetchPolicy.shouldFetch(now,null,now-9_000,2))
  assertTrue(WeatherFetchPolicy.shouldFetch(now,null,now-10_000,2))
  // Quick tries used up: a source that keeps failing is asked every fifteen minutes.
  assertFalse(WeatherFetchPolicy.shouldFetch(now,null,now-14*60000,RetryPolicy.QUICK+1))
  assertTrue(WeatherFetchPolicy.shouldFetch(now,null,now-15*60000,RetryPolicy.QUICK+1))
  // No failure on record: nothing to wait for.
  assertTrue(WeatherFetchPolicy.shouldFetch(now,null,now-1,0))
  // A reading younger than thirty minutes is never replaced, whatever the failure count.
  assertFalse(WeatherFetchPolicy.shouldFetch(now,now-29*60000,0,0))
  assertFalse(WeatherFetchPolicy.shouldFetch(now,now-29*60000,now-60*60000,3))
 }
 @Test fun headerSaysCheckingOnlyWhileAReadingIsOnItsWay(){
  assertTrue(WeatherFetchPolicy.expecting(now,null,0))
  assertTrue(WeatherFetchPolicy.expecting(now,now-10*60*60000,RetryPolicy.QUICK-1))
  assertFalse(WeatherFetchPolicy.expecting(now,now-10*60*60000,RetryPolicy.QUICK))
  assertFalse(WeatherFetchPolicy.expecting(now,now-60000,0))
  assertEquals("Checking the weather…",WeatherPanel.from(null,now,checking=true).condition)
  assertEquals("Weather unavailable",WeatherPanel.from(null,now).condition)
  val lastNight=WeatherData(9.0,3,false,now-10*60*60000,now-10*60*60000)
  assertEquals("Checking the weather…",WeatherPanel.from(lastNight,now,checking=true).condition)
  assertEquals("—",WeatherPanel.from(lastNight,now,checking=true).temperature)
  // A reading that can be shown is shown, checking or not.
  val recent=WeatherData(9.0,3,true,now-20*60000,now-20*60000)
  assertEquals("Overcast",WeatherPanel.from(recent,now,checking=true).condition)
 }
 @Test fun aShortOutageKeepsWhatIsShownAndALastingOneDoesNot(){
  val outage=OutageGrace(180_000L,120_000L)
  // Home polls about once a minute; the list goes only once the failures have gone on for three minutes.
  assertFalse(outage.failed(1_000));assertFalse(outage.failed(6_000));assertFalse(outage.failed(16_000))
  assertFalse(outage.failed(61_000));assertFalse(outage.failed(121_000));assertTrue(outage.failed(181_000))
  outage.succeeded()
  assertFalse(outage.failed(200_000))
 }
 @Test fun failuresBeforeStandbyDoNotCountAfterIt(){
  val outage=OutageGrace(180_000L,120_000L)
  assertFalse(outage.failed(1_000));assertFalse(outage.failed(61_000))
  // Ten hours later the TV wakes and the first tries fail again: a new outage, not a ten-hour one.
  val wake=10*3_600_000L
  assertFalse(outage.failed(wake));assertFalse(outage.failed(wake+5_000));assertFalse(outage.failed(wake+15_000))
  assertFalse(outage.failed(wake+60_000));assertFalse(outage.failed(wake+120_000));assertTrue(outage.failed(wake+180_000))
  // A clock that runs backwards starts afresh instead of counting a negative gap.
  assertFalse(outage.failed(5_000))
 }
 @Test fun keptStillsAreBoundedAndNamedWithoutTheirSource(){
  val name=ArtDisk.fileName("plex-art://server/12345")
  assertEquals(name,ArtDisk.fileName("plex-art://server/12345"))
  assertNotEquals(name,ArtDisk.fileName("plex-art://server/12346"))
  assertTrue(name.matches(Regex("[0-9a-f]{32}\\.jpg")))
  val files=(1..30).map{"f$it" to it*1000L}
  val extra=ArtDisk.surplus(files,ArtDisk.MAX_FILES)
  assertEquals((1..6).map{"f$it"}.toSet(),extra.toSet())
  assertTrue(ArtDisk.surplus(files.take(ArtDisk.MAX_FILES)).isEmpty())
  assertTrue(ArtDisk.fresh(0));assertTrue(ArtDisk.fresh(ArtDisk.FRESH_MS-1));assertFalse(ArtDisk.fresh(ArtDisk.FRESH_MS))
  assertTrue(ArtDisk.usable(ArtDisk.FRESH_MS));assertFalse(ArtDisk.usable(ArtDisk.KEEP_MS))
  // A file dated in the future (the TV's clock was wrong) is neither shown nor trusted.
  assertFalse(ArtDisk.usable(-1));assertFalse(ArtDisk.fresh(-1))
 }
}
