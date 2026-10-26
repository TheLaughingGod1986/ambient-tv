package org.ambienttv.home
import org.json.JSONObject

data class PlexPoster(val server:String,val key:String,val title:String,val thumb:String,val addedAt:Long,val animeEvidence:Boolean=false)
data class PlexCatalogPage(val items:List<PlexPoster>,val hasMore:Boolean,val sections:List<String>)
object PlexCatalogPolicy {
 const val PAGE_SIZE=10
 fun page(items:List<PlexPoster>,index:Int):List<PlexPoster> {
  require(index in 0..9)
  return items.distinctBy{it.server+":"+it.key}
   .sortedWith(compareByDescending<PlexPoster>{it.addedAt}.thenBy{it.server}.thenBy{it.key})
   .drop(index*PAGE_SIZE).take(PAGE_SIZE)
 }

 fun namedAnime(name:String)=Regex("(^|[^a-z])anime([^a-z]|$)|アニメ",RegexOption.IGNORE_CASE).containsMatchIn(name)
 fun animeMetadata(genres:List<String>,countries:List<String>)=genres.any{it.equals("Anime",true)} || (genres.any{it.equals("Animation",true)} && countries.any{it.equals("Japan",true)})
 fun sectionMatches(category:String,type:String,title:String)=when(category){"Movies"->type=="movie" && !namedAnime(title);"TV Shows"->type=="show" && !namedAnime(title);"Anime"->type in listOf("movie","show") && namedAnime(title);else->false}
 fun parse(server:String,body:JSONObject):List<PlexPoster>{
  val items=body.optJSONObject("MediaContainer")?.optJSONArray("Metadata") ?: return emptyList()
  return (0 until items.length()).mapNotNull{i->val j=items.optJSONObject(i) ?: return@mapNotNull null;val key=j.optString("ratingKey");val title=j.optString("title").trim();if(!key.matches(Regex("[0-9]{1,16}")) || title.isBlank())return@mapNotNull null
   val raw=j.optString("thumb");val thumb=raw.takeIf{it.startsWith("/library/") && !it.contains("..") && !it.contains('?')}.orEmpty()
   val genres=j.optJSONArray("Genre");val countries=j.optJSONArray("Country")
   fun tags(a:org.json.JSONArray?)=if(a==null)emptyList() else (0 until a.length()).mapNotNull{a.optJSONObject(it)?.optString("tag")}
   PlexPoster(server,key,title,thumb,j.optLong("addedAt",0),animeMetadata(tags(genres),tags(countries)))
  }
 }
}
