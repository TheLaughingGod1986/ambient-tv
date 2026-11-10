package org.ambienttv.home
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
class PlexResumePolicyTest {
 private fun item(type:String,section:String="",next:Boolean=false,position:Long=600000,duration:Long=1800000,show:String="",season:Int=-1,episode:Int=-1)=
  PlexResume("s","1","Title","/library/1",if(next)0 else position,duration,5,next,type,show,season,episode,"/library/p",section)
 @Test fun eachLibraryPageTakesItsOwnKindAndAnimeLibrariesStayOnAnime(){
  assertTrue(PlexResumePolicy.inCategory(item("movie","Films"),"Movies"));assertFalse(PlexResumePolicy.inCategory(item("episode","TV"),"Movies"))
  assertTrue(PlexResumePolicy.inCategory(item("episode","TV"),"TV Shows"));assertFalse(PlexResumePolicy.inCategory(item("movie","Films"),"TV Shows"))
  assertFalse(PlexResumePolicy.inCategory(item("episode","Anime"),"TV Shows"));assertTrue(PlexResumePolicy.inCategory(item("episode","Anime"),"Anime"))
  assertFalse(PlexResumePolicy.inCategory(item("episode","TV"),"Anime"));assertTrue(PlexResumePolicy.inCategory(item("episode","TV"),"Anime",animeTagged=true))
  assertTrue(PlexResumePolicy.inCategory(item("movie","Films"),"Anime",animeTagged=true))
  assertFalse(PlexResumePolicy.inCategory(item("clip","Anime"),"Anime"));assertFalse(PlexResumePolicy.inCategory(item("movie"),"Photos"))
 }
 @Test fun describesProgressAndNextEpisodesWithoutGuessing(){
  assertEquals("S2 E5 · 20 min left",PlexResumePolicy.detail(item("episode",season=2,episode=5)))
  assertEquals("S2 E6 · Next episode",PlexResumePolicy.detail(item("episode",next=true,season=2,episode=6)))
  assertEquals("20 min left",PlexResumePolicy.detail(item("movie")))
  assertEquals("20 min left",PlexResumePolicy.detail(item("episode")))
  assertEquals("",PlexResumePolicy.detail(item("movie",duration=0)))
  assertEquals("Show",PlexResumePolicy.name(item("episode",show="Show")));assertEquals("Title",PlexResumePolicy.name(item("movie")))
 }
 @Test fun progressIsAShareOfTheWholeAndZeroForANextEpisode(){
  assertEquals(1f/3,PlexResumePolicy.progress(item("movie")),.0001f)
  assertEquals(0f,PlexResumePolicy.progress(item("episode",next=true)),0f);assertEquals(0f,PlexResumePolicy.progress(item("movie",duration=0)),0f)
 }
 @Test fun hubItemsCarryTheirKindPlaceAndSeriesPoster(){
  val body=JSONObject("""{"MediaContainer":{"Metadata":[
   {"ratingKey":"21","type":"episode","title":"Pilot","grandparentTitle":"Show","grandparentRatingKey":"7","parentIndex":1,"index":3,"viewOffset":60000,"duration":1500000,"thumb":"/library/metadata/21/thumb/1","grandparentThumb":"/library/metadata/7/thumb/2","librarySectionTitle":"TV Shows"},
   {"ratingKey":"30","type":"movie","title":"Film","viewOffset":60000,"duration":5400000,"thumb":"/library/metadata/30/thumb/1","librarySectionTitle":"Films"},
   {"ratingKey":"31","type":"episode","title":"Odd","viewOffset":60000,"duration":1500000,"thumb":"/library/metadata/31/thumb/1","grandparentThumb":"https://elsewhere.example/p.jpg"}]}}""")
  val items=PlexProtocol.parseHub("server",body)
  assertEquals(listOf("episode","movie","episode"),items.map{it.type})
  assertEquals("Show",items[0].show);assertEquals(1,items[0].season);assertEquals(3,items[0].episode);assertEquals("7",items[0].showKey)
  assertEquals("/library/metadata/7/thumb/2",items[0].poster);assertEquals("TV Shows",items[0].section)
  assertEquals("/library/metadata/30/thumb/1",items[1].poster)
  assertEquals("external artwork is never used as a poster","",items[2].poster)
 }
}
