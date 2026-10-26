package org.ambienttv.home
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
class PlexProtocolTest {
 @Test fun onlyRealUnfinishedItemsWithServerArtQualify(){
  val body=JSONObject("""{"MediaContainer":{"Metadata":[{"ratingKey":"12","title":"A","viewOffset":10000,"duration":90000,"thumb":"/library/metadata/12/thumb/1","lastViewedAt":4},{"ratingKey":"13","title":"Done","viewOffset":89000,"duration":90000,"thumb":"/library/metadata/13/thumb/1"},{"ratingKey":"14","title":"Unstarted","viewOffset":0,"duration":90000,"thumb":"/library/metadata/14/thumb/1"}]}}""")
  val items=PlexProtocol.parseHub("server-1",body);assertEquals(1,items.size);assertEquals(10000L,items[0].position);assertEquals("A",items[0].title)
 }
 @Test fun actualSeriesTitleAndLastViewedOrderArePreserved(){
  val body=JSONObject("""{"MediaContainer":{"Metadata":[{"ratingKey":"1","title":"Old","viewOffset":10000,"duration":90000,"thumb":"/library/1","lastViewedAt":2},{"ratingKey":"2","title":"Episode","grandparentTitle":"Series","viewOffset":10000,"duration":90000,"thumb":"/library/2","lastViewedAt":8}]}}""")
  val items=PlexProtocol.parseHub("server",body);assertEquals("Series · Episode",items[1].title);assertEquals(listOf("1","2"),items.map{it.ratingKey})
 }
 @Test fun acceptsHubEnvelopeAndRejectsExternalOrTokenBearingArtwork(){
  val body=JSONObject("""{"MediaContainer":{"Hub":[{"Metadata":[{"ratingKey":"1","title":"Safe","viewOffset":10000,"duration":90000,"thumb":"/library/1"},{"ratingKey":"2","title":"External","viewOffset":10000,"duration":90000,"thumb":"https://elsewhere/2"},{"ratingKey":"3","title":"Query","viewOffset":10000,"duration":90000,"thumb":"/library/3?token=secret"}]}]}}""")
  assertEquals(listOf("Safe"),PlexProtocol.parseHub("server",body).map{it.title})
 }
 @Test fun usesNewLibraryDetailRouteWithEncodedKeyAndNoAutoplay(){assertEquals("plex://libraries/server-1/unmatched/%2Flibrary%2Fmetadata%2F123",PlexProtocol.detailUri("server-1","123"))}
 @Test(expected=IllegalArgumentException::class)fun rejectsMalformedMetadataKey(){PlexProtocol.detailUri("server","../123")}
 @Test fun authoritativeContinueHubIncludesOnlyUnwatchedNextEpisodes(){
  val body=JSONObject("""{"MediaContainer":{"Metadata":[{"ratingKey":"21","type":"episode","title":"Next","duration":90000,"thumb":"/library/21"},{"ratingKey":"22","type":"movie","title":"Unstarted movie","duration":90000,"thumb":"/library/22"},{"ratingKey":"23","type":"episode","title":"Watched","viewCount":1,"duration":90000,"thumb":"/library/23"},{"ratingKey":"24","type":"episode","title":"Finished","viewOffset":89000,"duration":90000,"thumb":"/library/24"},{"ratingKey":"25","type":"episode","title":"Resume","viewOffset":10000,"duration":90000,"thumb":"/library/25","lastViewedAt":999}]}}""")
  val items=PlexProtocol.parseHub("server",body,allowNextEpisodes=true)
  assertEquals(listOf("21","25"),items.map{it.ratingKey})
  assertTrue(items[0].nextEpisode);assertEquals(0L,items[0].position)
  assertFalse(items[1].nextEpisode);assertEquals(10000L,items[1].position)
  assertEquals(listOf("25"),PlexProtocol.parseHub("server",body).map{it.ratingKey})
 }
}
