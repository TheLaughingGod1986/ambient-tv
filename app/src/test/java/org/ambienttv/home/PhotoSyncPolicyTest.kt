package org.ambienttv.home
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
class PhotoSyncPolicyTest {
 private fun t(s:String)=Instant.parse(s).toEpochMilli()
 @Test fun noDaytimeOrEdgeOfShutdownRequests(){for(s in listOf("2026-10-29T01:14:59Z","2026-10-29T05:45:00Z","2026-10-29T12:00:00Z"))assertFalse(PhotoSyncPolicy.open(t(s)));assertTrue(PhotoSyncPolicy.open(t("2026-10-29T01:15:00Z")))}
 @Test fun scheduleAndRetriesStayInsideUtcWindowAcrossDst(){for(day in listOf("2026-03-29","2026-10-25")){assertEquals(t("${day}T01:15:00Z"),PhotoSyncPolicy.next(t("${day}T00:30:00Z"),0));assertEquals(t("${day}T03:15:00Z"),PhotoSyncPolicy.next(t("${day}T03:00:00Z"),0,true))}}
 @Test fun successfulNightWaitsForTomorrow(){assertEquals(t("2026-10-30T01:15:00Z"),PhotoSyncPolicy.next(t("2026-10-29T03:00:00Z"),t("2026-10-29T02:00:00Z")))}
 @Test fun nasLocalWindowFollowsSummerTime(){val z=java.util.TimeZone.getTimeZone("Europe/Dublin");assertEquals(t("2026-07-01T00:15:00Z"),PhotoSyncPolicy.next(t("2026-07-01T00:00:00Z"),0,zone=z));assertTrue(PhotoSyncPolicy.open(t("2026-07-01T00:30:00Z"),z));assertFalse(PhotoSyncPolicy.open(t("2026-07-01T05:00:00Z"),z))}
 @Test fun historyIsBoundedAndRoundTrips(){var h=emptyMap<Long,List<String>>();for(d in 1L..20)h=RecentPhotoHistory.remember(h,d,listOf("$d.jpg"));assertEquals(8,h.size);assertFalse(h.containsKey(1));assertEquals(h,RecentPhotoHistory.read(RecentPhotoHistory.encode(h)))}
}
