package org.ambienttv.home
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import java.io.ByteArrayOutputStream

data class WatchItem(val title:String,val owner:String,val intentUri:String,val art:String,val position:Long,val duration:Long,val engagedAtMillis:Long=0,val providerRowId:Long=-1,val nextEpisode:Boolean=false) {
 val stableKey get()=owner+"|"+if(providerRowId>=0)"row:$providerRowId"else intentUri
 fun open(c:Context) {
  // Programme launches must never silently fall back to reopening an app or its last video.
  try {
   val requested=Intent.parseUri(intentUri,Intent.URI_INTENT_SCHEME)
   if(requested.component!=null && requested.component!!.packageName!=owner)throw IllegalArgumentException()
   requested.setPackage(owner)
   val resolved=requested.resolveActivity(c.packageManager)
   if(!WatchNextPolicy.sameOwner(owner,resolved?.packageName))throw IllegalArgumentException()
   c.startActivity(requested)
  }catch(_:Exception){android.widget.Toast.makeText(c,"This programme link is unavailable. Open the app to continue.",android.widget.Toast.LENGTH_LONG).show()}

 }
}
object WatchNextReader {
 private val providerCache=LastGoodSource<WatchItem>()
 @Volatile private var directPlexCache=emptyList<WatchItem>()
 internal fun clearDirectPlex(){directPlexCache=emptyList()}
 internal fun merge(native:List<WatchItem>,plex:List<WatchItem>,preferDirectPlex:Boolean=plex.isNotEmpty()):List<WatchItem> {
  val rows=if(preferDirectPlex)native.filter{it.owner!="com.plexapp.android"}+plex else native
  val sorted=rows.distinctBy{it.stableKey}.sortedByDescending{it.engagedAtMillis}
  // Keep Plex's authoritative next/resume sequence within its positions in the mixed row.
  val orderedPlex=plex.distinctBy{it.stableKey}.iterator()
  return if(plex.isEmpty())sorted else sorted.map{if(it.owner=="com.plexapp.android" && orderedPlex.hasNext())orderedPlex.next() else it}
 }
 fun read(c:Context,forcePlex:Boolean=false,onProvider:((List<WatchItem>)->Unit)?=null):List<WatchItem> {
  val native=readProvider(c)
  // Publish native changes before waiting for an independent Plex network request.
  onProvider?.invoke(merge(native,directPlexCache,PlexFeed.hasSession(c)))
  val direct=try{PlexFeed.read(c,forcePlex)}catch(_:Exception){directPlexCache}
  directPlexCache=direct
  return merge(native,direct,PlexFeed.hasSession(c))
 }
 private fun readProvider(c:Context):List<WatchItem> {
  if(Build.VERSION.SDK_INT<26)return emptyList()
  return try {
   val out=mutableListOf<WatchItem>();var rows=0;var continueRows=0;var youtubeRows=0;var youtubeNewest=0L
   val seen=mutableSetOf<String>()
   c.contentResolver.query(android.media.tv.TvContract.WatchNextPrograms.CONTENT_URI,null,null,null,"last_engagement_time_utc_millis DESC")?.use {cursor ->
    fun str(name:String):String {val i=cursor.getColumnIndex(name);return if(i>=0)cursor.getString(i).orEmpty() else ""}
    fun number(name:String):Long {val i=cursor.getColumnIndex(name);return if(i>=0 && !cursor.isNull(i))cursor.getLong(i) else -1}
    rows=cursor.count
    while(cursor.moveToNext()) {
     if(str("package_name")=="com.google.android.youtube.tv"){youtubeRows++;youtubeNewest=maxOf(youtubeNewest,number("last_engagement_time_utc_millis"))}
     if(number("watch_next_type")!=0L)continue
     continueRows++;val owner=str("package_name")
     if(owner !in setOf("com.plexapp.android","com.google.android.youtube.tv"))continue
     val position=number("last_playback_position_millis");val duration=number("duration_millis")
     if(owner=="com.plexapp.android" && !ResumePolicy.isUnfinished(position,duration))continue
     if(owner=="com.google.android.youtube.tv" && HomeDismissalPolicy.completed(position,duration))continue
     val title=str("title");val link=str("intent_uri")
     if(title.isBlank() || link.isBlank() || c.packageManager.getLeanbackLaunchIntentForPackage(owner)==null)continue
     val item=WatchItem(title,owner,link,str("poster_art_uri"),position,duration,number("last_engagement_time_utc_millis").coerceAtLeast(0),number("_id"))
     if(out.count{it.owner==owner}<12 && seen.add(item.stableKey))out.add(item)
    }
   } ?: throw IllegalStateException("Watch Next provider unavailable")
   val snapshot=providerCache.update(out)
   c.getSharedPreferences("feed-audit",0).edit().putInt("youtubeRows",youtubeRows).putLong("youtubeNewest",youtubeNewest).putInt("rows",rows).putInt("continueRows",continueRows).putBoolean("querySucceeded",true).putLong("providerReadAt",System.currentTimeMillis()).apply()
   snapshot
  }catch(e:Exception){
   c.getSharedPreferences("feed-audit",0).edit().putString("errorType",e.javaClass.simpleName).putBoolean("querySucceeded",false).apply()
   providerCache.update(null)
  }
 }
 fun art(c:Context,value:String):Bitmap? {
  return try {
   val uri=Uri.parse(value)
   if(uri.scheme=="plex-art")return PlexFeed.art(c,uri)
   var connection:java.net.HttpURLConnection?=null
   val input=when(uri.scheme){
    "content","android.resource"->c.contentResolver.openInputStream(uri)
    "https","http"->{
     connection=java.net.URL(value).openConnection() as java.net.HttpURLConnection
     connection.connectTimeout=3000;connection.readTimeout=3000;connection.instanceFollowRedirects=false
     if(connection.responseCode!=200){connection.disconnect();return null};connection.inputStream
    }
    else->return null
   }
   val bytes=try {input?.use {input ->val out=ByteArrayOutputStream();val buf=ByteArray(8192);while(true){val n=input.read(buf);if(n<0)break;require(out.size()+n<=6*1024*1024);out.write(buf,0,n)};out.toByteArray()}}finally{connection?.disconnect()} ?: return null
   val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
   BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply {inSampleSize=BitmapPolicy.sampleSize(bounds.outWidth,bounds.outHeight,512,320)})
  }catch(_:Exception){null}
 }
}
