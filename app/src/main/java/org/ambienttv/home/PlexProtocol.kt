package org.ambienttv.home
import org.json.JSONObject
import java.net.URLEncoder

data class PlexResume(val server:String,val ratingKey:String,val title:String,val thumb:String,val position:Long,val duration:Long,val engagedAt:Long,val nextEpisode:Boolean=false)
object PlexProtocol {
 fun parseHub(server:String,body:JSONObject,allowNextEpisodes:Boolean=false):List<PlexResume>{
  require(server.matches(Regex("[A-Za-z0-9_-]{1,128}")))
  val container=body.optJSONObject("MediaContainer") ?: return emptyList()
  val arrays=mutableListOf<org.json.JSONArray>();container.optJSONArray("Metadata")?.let{arrays.add(it)}
  container.optJSONArray("Hub")?.let{hubs->for(i in 0 until hubs.length())hubs.optJSONObject(i)?.optJSONArray("Metadata")?.let{arrays.add(it)}}
  val result=mutableListOf<PlexResume>()
  for(items in arrays)for(i in 0 until items.length()){
   val item=items.optJSONObject(i) ?: continue
   val key=item.optString("ratingKey");val position=item.optLong("viewOffset",-1);val duration=item.optLong("duration",-1)
   val nextEpisode=allowNextEpisodes && item.optString("type")=="episode" && position<=0 && duration>0 && item.optLong("viewCount",0)==0L
   if(!key.matches(Regex("[0-9]{1,16}")) || (!ResumePolicy.isUnfinished(position,duration) && !nextEpisode))continue
   val rawTitle=item.optString("title").trim();if(rawTitle.isEmpty())continue
   val parent=item.optString("grandparentTitle").trim()
   val title=if(parent.isNotEmpty())"$parent · $rawTitle" else rawTitle
   val thumb=item.optString("thumb").ifBlank{item.optString("grandparentThumb")}
   if(!thumb.startsWith("/library/") || thumb.contains("..") || thumb.contains('?'))continue
   result.add(PlexResume(server,key,title,thumb,if(nextEpisode)0 else position,duration,item.optLong("lastViewedAt",0),nextEpisode))
  }
  return result.distinctBy{it.server+":"+it.ratingKey}.take(12)
 }
 fun detailUri(server:String,key:String):String{
  require(server.matches(Regex("[A-Za-z0-9_-]{1,128}")) && key.matches(Regex("[0-9]{1,16}")))
  return "plex://libraries/$server/unmatched/"+URLEncoder.encode("/library/metadata/$key","UTF-8")
 }
}
