package org.ambienttv.home
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.net.URL
import java.net.InetAddress

/** Account and server tokens originate only from this app's separately approved PIN flow. */
internal object PlexFeed {
 private data class Server(val id:String,val base:String,val token:String)
 private val servers=mutableMapOf<String,Server>()
 private var cached=emptyList<PlexResume>()
 private val catalogThumbs=mutableMapOf<String,String>()
 private val artwork=BoundedTtlCache<String,ByteArray>(4*1024*1024,120000){it.size}
 private var refreshedAt=0L
 fun hasSession(c:Context)=PlexApi.vault(c).read()!=null
 fun status(c:Context)=c.getSharedPreferences("plex-status",0).getString("message","Connect Plex to add its Continue Watching items.")!!
 private fun status(c:Context,s:String){c.getSharedPreferences("plex-status",0).edit().putString("message",s).apply()}
 private fun resetSessionCache(){servers.clear();catalogThumbs.clear();artwork.clear();cached=emptyList();refreshedAt=0;WatchNextReader.clearDirectPlex()}
 @Synchronized fun acceptSession(c:Context,token:String){resetSessionCache();HomeDismissals.rotatePlexScope(c);PlexApi.vault(c).save("https://plex.tv",token)}
 @Synchronized fun clear(c:Context){PlexApi.vault(c).clear();resetSessionCache();status(c,"Plex disconnected from Ambient TV.")}
 @Synchronized fun read(c:Context,force:Boolean=false):List<WatchItem>{
  val token=PlexApi.vault(c).read()?.second ?: return emptyList()
  if(!force && ResumeFeedPolicy.fresh(android.os.SystemClock.elapsedRealtime(),refreshedAt,60000) && servers.isNotEmpty())return watchItems()
  try{
   val resources=PlexApi.resources(c,token);val next=mutableListOf<PlexResume>();var connected=0
   val deadline=android.os.SystemClock.elapsedRealtime()+25000
   for(i in 0 until resources.length()){
    if(connected>=3 || android.os.SystemClock.elapsedRealtime()>deadline)break
    val resource=resources.optJSONObject(i) ?: continue
    if(!resource.optString("provides").split(',').contains("server"))continue
    val id=resource.optString("clientIdentifier");if(!id.matches(Regex("[A-Za-z0-9_-]{1,128}")))continue
    val access=resource.optString("accessToken");if(access.isBlank())continue
    val connections=resource.optJSONArray("connections") ?: continue
    val candidates=(0 until connections.length()).mapNotNull{connections.optJSONObject(it)}.sortedWith(compareBy<JSONObject>{it.optBoolean("relay")}.thenBy{!it.optBoolean("local")}.thenBy{!it.optString("uri").startsWith("https:")})
    for(connection in candidates.take(3)){
     if(android.os.SystemClock.elapsedRealtime()>deadline)break
     val base=connection.optString("uri").trimEnd('/')
     val url=try{URL(base)}catch(_:Exception){continue}
     if(url.userInfo!=null || url.query!=null || url.ref!=null || url.path.isNotEmpty())continue
     if(url.protocol!="https"){
      if(url.protocol!="http" || !c.getSharedPreferences("plex-config",0).getBoolean("localHttp",false))continue
      if(!try{InetAddress.getByName(url.host).isSiteLocalAddress}catch(_:Exception){false})continue
     }
     try{
      val body=JSONObject(String(PlexApi.request(c,"$base/hubs/continueWatching?count=12",access),Charsets.UTF_8))
      val items=PlexProtocol.parseHub(id,body,allowNextEpisodes=true)
      servers[id]=Server(id,base,access);next.addAll(items);connected++;break
     }catch(_:Exception){/* Try the next advertised connection; never log its token or URL. */}
    }
   }
   if(connected>0){cached=next.distinctBy{it.server+":"+it.ratingKey}.take(12);refreshedAt=android.os.SystemClock.elapsedRealtime();status(c,"Connected to Plex · ${cached.size} continue watching items")}
   else{status(c,"Signed in to Plex, but no server could be reached securely. Check that your Plex server is available.");if(android.os.SystemClock.elapsedRealtime()-refreshedAt>600000)cached=emptyList()}
  }catch(e:PlexHttpFailure){if(e.status==401)resetSessionCache();status(c,if(e.status==401)"Plex sign-in needs reconnecting." else "Plex could not refresh right now.");if(android.os.SystemClock.elapsedRealtime()-refreshedAt>600000)cached=emptyList()}
  catch(_:Exception){status(c,"Could not reach Plex. Your existing Plex app login is unchanged.");if(android.os.SystemClock.elapsedRealtime()-refreshedAt>600000)cached=emptyList()}
  return watchItems()
 }
 private fun watchItems()=cached.map{WatchItem(it.title,"com.plexapp.android",PlexProtocol.detailUri(it.server,it.ratingKey),"plex-art://${it.server}/${it.ratingKey}",it.position,it.duration,ResumeFeedPolicy.plexMillis(it.engagedAt),nextEpisode=it.nextEpisode)}
 // Cache-local lock only: memory callbacks must never wait for Plex network operations.
 fun trimArtwork(){artwork.clear()}
 @Synchronized fun art(c:Context,uri:android.net.Uri):Bitmap?{
  val item=cached.firstOrNull{it.server==uri.host && it.ratingKey==uri.lastPathSegment}
  val thumb=item?.thumb ?: catalogThumbs[uri.host+":"+uri.lastPathSegment] ?: return null
  val server=servers[uri.host] ?: return null
  return try{
   val key=server.id+":"+thumb
   val now=android.os.SystemClock.elapsedRealtime()
   val bytes=artwork.get(key,now) ?: PlexApi.request(c,server.base+thumb,server.token,maxBytes=6*1024*1024).also{artwork.put(key,it,android.os.SystemClock.elapsedRealtime())}
   val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
   BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=BitmapPolicy.sampleSize(bounds.outWidth,bounds.outHeight,if(item==null)384 else 512,if(item==null)640 else 320)})
  }catch(_:Exception){null}
 }
 @Synchronized fun catalog(c:Context,category:String,page:Int):PlexCatalogPage{
  require(category in listOf("Movies","TV Shows","Anime") && page in 0..9)
  read(c)
  if(servers.isEmpty())throw IllegalStateException("Connect Plex or check your server connection.")
  val count=(page+1)*PlexCatalogPolicy.PAGE_SIZE;val all=mutableListOf<PlexPoster>();val names=mutableListOf<String>();var total=0;var scanned=0
  for(server in servers.values.toList()){
   val sections=JSONObject(String(PlexApi.request(c,server.base+"/library/sections",server.token),Charsets.UTF_8)).optJSONObject("MediaContainer")?.optJSONArray("Directory") ?: continue
   for(i in 0 until minOf(sections.length(),16)){
    if(scanned>=8)break
    val section=sections.optJSONObject(i) ?: continue;val key=section.optString("key");val title=section.optString("title");val type=section.optString("type")
    if(!key.matches(Regex("[0-9]{1,12}")) || type !in listOf("movie","show"))continue
    var matches=PlexCatalogPolicy.sectionMatches(category,type,title);var genre="";var matchLabel=title
    if(category=="Anime" && !matches){
     val genres=try{JSONObject(String(PlexApi.request(c,server.base+"/library/sections/$key/genre",server.token),Charsets.UTF_8)).optJSONObject("MediaContainer")?.optJSONArray("Directory")}catch(_:Exception){null}
     if(genres!=null)for(g in 0 until genres.length()){
      val row=genres.optJSONObject(g) ?: continue
      if(row.optString("title").equals("Anime",true) && row.optString("key").matches(Regex("[0-9]{1,12}"))){genre="&genre="+row.optString("key");matches=true;break}
     }
     if(!matches && genres!=null){
      var animation="";for(g in 0 until genres.length()){val row=genres.optJSONObject(g) ?: continue;if(row.optString("title").equals("Animation",true) && row.optString("key").matches(Regex("[0-9]{1,12}")))animation=row.optString("key")}
      if(animation.isNotBlank()){
       val countries=try{JSONObject(String(PlexApi.request(c,server.base+"/library/sections/$key/country",server.token),Charsets.UTF_8)).optJSONObject("MediaContainer")?.optJSONArray("Directory")}catch(_:Exception){null}
       if(countries!=null)for(j in 0 until countries.length()){val row=countries.optJSONObject(j) ?: continue;if(row.optString("title").equals("Japan",true) && row.optString("key").matches(Regex("[0-9]{1,12}"))){genre="&genre=$animation&country="+row.optString("key");matches=true;matchLabel=title+" · Japanese animation";break}}
      }
     }
    }
    if(!matches)continue
    scanned++;names.add(matchLabel)
    val url=server.base+"/library/sections/$key/all?type="+(if(type=="movie")1 else 2)+"&sort=addedAt%3Adesc&X-Plex-Container-Start=0&X-Plex-Container-Size=$count"+genre
    val body=JSONObject(String(PlexApi.request(c,url,server.token),Charsets.UTF_8))
    val parsed=PlexCatalogPolicy.parse(server.id,body).filter{!matchLabel.endsWith("Japanese animation") || it.animeEvidence};all.addAll(parsed)
    total+=body.optJSONObject("MediaContainer")?.optInt("totalSize",parsed.size) ?: parsed.size
   }
  }
  val items=PlexCatalogPolicy.page(all,page)
  catalogThumbs.clear();items.forEach{if(it.thumb.isNotBlank())catalogThumbs[it.server+":"+it.key]=it.thumb}
  c.getSharedPreferences("plex-catalog-audit",0).edit().putString("category",category).putInt("page",page).putInt("count",items.size).putString("sections",org.json.JSONArray(names).toString()).apply()
  return PlexCatalogPage(items,page<9 && total>(page+1)*PlexCatalogPolicy.PAGE_SIZE,names)
 }

}
