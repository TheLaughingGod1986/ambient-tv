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
 // Revisiting a library page within five minutes reuses its listing. Cleared with the session.
 private val catalogPages=mutableMapOf<String,Pair<Long,PlexCatalogPage>>()
 private val artwork=BoundedTtlCache<ArtSource,ByteArray>(4*1024*1024,900000){it.size}
 private data class ArtSource(val server:Server,val thumb:String,val poster:Boolean)
 @Volatile private var artSources=emptyMap<String,ArtSource>()
 private fun publishArtwork(){
  val next=mutableMapOf<String,ArtSource>()
  catalogThumbs.forEach{(key,thumb)->servers[key.substringBefore(':')]?.let{next[key]=ArtSource(it,thumb,true)}}
  cached.forEach{item->servers[item.server]?.let{
   next[item.server+":"+item.ratingKey]=ArtSource(it,item.thumb,false)
   // The library pages show the series or film poster for an in-progress item, under its own key.
   if(item.poster.isNotBlank())next[item.server+":p"+item.ratingKey]=ArtSource(it,item.poster,true)
  }}
  artSources=next.toMap()
 }
 private var discoveredAt=0L
 private var refreshedAt=0L
 fun hasSession(c:Context)=PlexApi.vault(c).read()!=null
 fun status(c:Context)=c.getSharedPreferences("plex-status",0).getString("message","Connect Plex to add its Continue Watching items.")!!
 private fun status(c:Context,s:String){c.getSharedPreferences("plex-status",0).edit().putString("message",s).apply()}
 private fun resetSessionCache(){artSources=emptyMap();servers.clear();catalogThumbs.clear();catalogPages.clear();animeTags.clear();artwork.clear();cached=emptyList();discoveredAt=0;refreshedAt=0;WatchNextReader.clearDirectPlex()}
 @Synchronized fun acceptSession(c:Context,token:String){resetSessionCache();HomeDismissals.rotatePlexScope(c);PlexApi.vault(c).save("https://plex.tv",token)}
 @Synchronized fun clear(c:Context){PlexApi.vault(c).clear();resetSessionCache();status(c,"Plex disconnected from Ambient TV.")}
 @Synchronized fun read(c:Context,force:Boolean=false):List<WatchItem>{
  val token=PlexApi.vault(c).read()?.second ?: return emptyList()
  if(!force && ResumeFeedPolicy.fresh(android.os.SystemClock.elapsedRealtime(),refreshedAt,60000) && servers.isNotEmpty())return watchItems()
  // Reuse verified connections; refreshing progress should not rediscover every server.
  if(servers.isNotEmpty() && ResumeFeedPolicy.fresh(android.os.SystemClock.elapsedRealtime(),discoveredAt,900000)){
   val next=mutableListOf<PlexResume>();var allReached=true
   for(server in servers.values.toList())try{
    val body=JSONObject(String(PlexApi.request(c,server.base+"/hubs/continueWatching?count=12",server.token),Charsets.UTF_8))
    next.addAll(PlexProtocol.parseHub(server.id,body,allowNextEpisodes=true))
   }catch(_:Exception){allReached=false;break}
   if(allReached){
    cached=next.distinctBy{it.server+":"+it.ratingKey}.take(12);refreshedAt=android.os.SystemClock.elapsedRealtime()
    c.getSharedPreferences("plex-status",0).edit().putLong("successAt",System.currentTimeMillis()).apply()
    status(c,"Connected to Plex · ${cached.size} continue watching items");publishArtwork();return watchItems()
   }
  }
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
   if(connected>0){discoveredAt=android.os.SystemClock.elapsedRealtime();c.getSharedPreferences("plex-status",0).edit().putLong("successAt",System.currentTimeMillis()).apply();cached=next.distinctBy{it.server+":"+it.ratingKey}.take(12);refreshedAt=android.os.SystemClock.elapsedRealtime();status(c,"Connected to Plex · ${cached.size} continue watching items")}
   else{status(c,"Signed in to Plex, but no server could be reached securely. Check that your Plex server is available.");if(android.os.SystemClock.elapsedRealtime()-refreshedAt>600000)cached=emptyList()}
  }catch(e:PlexHttpFailure){if(e.status==401)resetSessionCache();status(c,if(e.status==401)"Plex sign-in needs reconnecting." else "Plex could not refresh right now.");if(android.os.SystemClock.elapsedRealtime()-refreshedAt>600000)cached=emptyList()}
  catch(_:Exception){status(c,"Could not reach Plex. Your existing Plex app login is unchanged.");if(android.os.SystemClock.elapsedRealtime()-refreshedAt>600000)cached=emptyList()}
  publishArtwork()
  return watchItems()
 }
 private fun watchItems()=cached.map{WatchItem(it.title,"com.plexapp.android",PlexProtocol.detailUri(it.server,it.ratingKey),"plex-art://${it.server}/${it.ratingKey}",it.position,it.duration,ResumeFeedPolicy.plexMillis(it.engagedAt),nextEpisode=it.nextEpisode)}
 // Cache-local lock only: memory callbacks must never wait for Plex network operations.
 fun trimArtwork(){artwork.clear()}
 // Never acquire the catalog/feed monitor here: it can hold slow network calls.
 fun art(c:Context,uri:android.net.Uri):Bitmap?{
  val snapshot=artSources
  val source=snapshot[uri.host+":"+uri.lastPathSegment] ?: return null
  return try{
   val key=source
   val now=android.os.SystemClock.elapsedRealtime()
   val bytes=artwork.get(key,now) ?: loadArt(c,source).also{artwork.put(key,it,android.os.SystemClock.elapsedRealtime())}
   val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
   if(snapshot!==artSources && artSources[uri.host+":"+uri.lastPathSegment]!=source)return null
   BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=BitmapPolicy.sampleSize(bounds.outWidth,bounds.outHeight,if(source.poster)384 else 768,if(source.poster)640 else 768)})
  }catch(_:Exception){null}
 }
 /**
  * Server artwork as stored, or a copy resized by the same server when the stored file is too
  * large to hold or is in a format this TV cannot read. Still one bounded request per attempt.
  */
 private fun loadArt(c:Context,source:ArtSource):ByteArray{
  val server=source.server
  val original=try{PlexApi.request(c,server.base+source.thumb,server.token,maxBytes=6*1024*1024)}catch(_:IllegalArgumentException){null}
  if(original!=null && readable(original))return original
  val resized=PlexProtocol.resizedArtPath(source.thumb,if(source.poster)384 else 768,if(source.poster)576 else 432)
  return PlexApi.request(c,server.base+resized,server.token,maxBytes=2*1024*1024)
 }
 private fun readable(bytes:ByteArray):Boolean{
  val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
  return bounds.outWidth in 1..20000 && bounds.outHeight in 1..20000
 }
 // Whether the server's own tags mark a film or series as anime; asked once per title and kept for the session.
 private val animeTags=mutableMapOf<String,Boolean>()
 /** In-progress items for one library page, most recently watched first. Empty when nothing qualifies. */
 @Synchronized fun continueFor(c:Context,category:String):List<PlexResume>{
  require(category in listOf("Movies","TV Shows","Anime"))
  read(c)
  return cached.filter{item->
   val tagged=category=="Anime" && !PlexCatalogPolicy.namedAnime(item.section) && item.type in listOf("movie","episode") && animeTagged(c,item)
   PlexResumePolicy.inCategory(item,category,tagged)
  }.sortedByDescending{it.engagedAt}
 }
 private fun animeTagged(c:Context,item:PlexResume):Boolean{
  val key=item.showKey.ifBlank{item.ratingKey};val server=servers[item.server] ?: return false
  return animeTags.getOrPut(item.server+":"+key){try{
   val entry=JSONObject(String(PlexApi.request(c,server.base+"/library/metadata/$key",server.token),Charsets.UTF_8)).optJSONObject("MediaContainer")?.optJSONArray("Metadata")?.optJSONObject(0)
   fun tags(name:String)=entry?.optJSONArray(name)?.let{a->(0 until a.length()).mapNotNull{a.optJSONObject(it)?.optString("tag")}} ?: emptyList()
   PlexCatalogPolicy.animeMetadata(tags("Genre"),tags("Country"))
  }catch(_:Exception){return false}} // a failed lookup is not remembered, so it is asked again next time
 }
 @Synchronized fun catalog(c:Context,category:String,page:Int,refresh:Boolean=false,size:Int=PlexCatalogPolicy.PAGE_SIZE):PlexCatalogPage{
  require(category in listOf("Movies","TV Shows","Anime") && page in 0..9 && size in 1..PlexCatalogPolicy.PAGE_SIZE)
  val cacheKey="$category:$page:$size"
  // Refresh forgets every kept page of this library, not only the one shown: neighbouring
  // pages kept from before could otherwise skip or repeat a title once the list has shifted.
  if(refresh)catalogPages.keys.removeAll{it.startsWith("$category:")}
  else catalogPages[cacheKey]?.let{(at,saved)->
   if(servers.isNotEmpty() && ResumeFeedPolicy.fresh(android.os.SystemClock.elapsedRealtime(),at,300000)){publishCatalog(saved.items);return saved}
  }
  read(c)
  if(servers.isEmpty())throw IllegalStateException("Connect Plex or check your server connection.")
  val count=(page+1)*size;val all=mutableListOf<PlexPoster>();val names=mutableListOf<String>();var total=0;var scanned=0
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
  val items=PlexCatalogPolicy.page(all,page,size)
  publishCatalog(items)
  c.getSharedPreferences("plex-catalog-audit",0).edit().putString("category",category).putInt("page",page).putInt("count",items.size).putString("sections",org.json.JSONArray(names).toString()).apply()
  val result=PlexCatalogPage(items,page<9 && total>(page+1)*size,names)
  // An empty answer is never kept: a library that has just gained items should show them.
  if(items.isNotEmpty())catalogPages[cacheKey]=android.os.SystemClock.elapsedRealtime() to result else catalogPages.remove(cacheKey)
  return result
 }
 private fun publishCatalog(items:List<PlexPoster>){
  catalogThumbs.clear();items.forEach{if(it.thumb.isNotBlank())catalogThumbs[it.server+":"+it.key]=it.thumb}
  publishArtwork()
 }

}
