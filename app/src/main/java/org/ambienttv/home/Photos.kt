package org.ambienttv.home

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.util.concurrent.Executors
import tv.projectivy.plugin.wallpaperprovider.api.*

object Photos {
 private val worker = Executors.newSingleThreadExecutor()
 @Volatile private var running = false
 @Volatile private var lastAttempt = 0L
 const val UUID = "d170c0ec-f8a8-434d-9c5d-8090a556b827"
 fun dir(c: Context) = File(c.filesDir,"photos").apply { mkdirs() }
 fun files(c: Context) = dir(c).listFiles()?.filter { it.name.matches(Regex("[0-9]+-[0-9]+\\.jpg")) }?.sortedBy { it.name } ?: emptyList()
 fun status(c: Context): String {
  if(AppPreferences.demo(c))return "Demo photos · no account needed"
  val saved=c.getSharedPreferences("state",0).getString("status","Sign in to connect Personal Space.")!!
  return if(saved.startsWith("Connected") && !hasCompleteCache(c)) "Cached photos need to reload. Select Refresh photos." else saved
 }
 @Synchronized fun refresh(c: Context, force: Boolean = false, done: ((String)->Unit)? = null) {
  if(AppPreferences.demo(c)) { done?.invoke("Demo photos ready. Connect your NAS to use your own photos."); return }
  if(running) { done?.invoke("A refresh is already running."); return }
  val state = c.getSharedPreferences("state",0)
  val now = System.currentTimeMillis()
  if(!force && !RefreshPolicy.shouldFetch(now,maxOf(lastAttempt,state.getLong("attemptAt",0)),state.getLong("success",0),hasCompleteCache(c) && state.getInt("mixVersion",0)==1)) return
  running = true; lastAttempt = now
  state.edit().putLong("attemptAt",now).apply()
  worker.execute {
   var message = "NAS unavailable. Keeping the previous photos."
   val created = mutableListOf<File>()
   var committed = false
   var stage = "session"
   try {
    val session = Vault(c).read() ?: throw ApiFailure(119)
    val api = Synology(session.first,session.second)
    stage = "person selection"
    val person = api.personId(AppPreferences.person(c))
    stage = "recent photos and older memories"
    val excluded=state.getStringSet("excludedPhotoIds",emptySet()) ?: emptySet()
    val catalog=api.photoCatalog(person).filter{it.getInt("id").toString() !in excluded}
    require(catalog.isNotEmpty())
    val recent = catalog.take(PhotoPoolPolicy.LIMIT)
    val older=catalog.asReversed().filter{item->recent.take(PhotoMixPolicy.RECENT).none{it.getInt("id")==item.getInt("id")}}
    val archiveOffset=if(older.isEmpty())0 else Math.floorMod(state.getInt("archiveOffset",0),older.size)
    val archive=if(older.isEmpty())emptyList() else (0 until minOf(PhotoPoolPolicy.LIMIT,older.size)).map{older[(archiveOffset+it)%older.size]}
    val mixedIds=PhotoMixPolicy.select(recent.map{it.getInt("id")},archive.map{it.getInt("id")})
    // Keep today's visible memories in the authorised cache through background refreshes.
    val daily=c.getSharedPreferences("home-daily",0)
    val pinned=if(daily.getLong("row-day",Long.MIN_VALUE)==HomePhotoPolicy.localDay(now,java.util.TimeZone.getTimeZone("Europe/London")))daily.getString("row-ids","").orEmpty().split(',').mapNotNull{it.toIntOrNull()}.filter{id->catalog.any{it.getInt("id")==id}}else emptyList()
    val selectedIds=(pinned+mixedIds).distinct().take(PhotoPoolPolicy.LIMIT)
    val byId=catalog.associateBy{it.getInt("id")}
    val items=selectedIds.map{byId.getValue(it)}
    val nextArchiveOffset=if(older.isEmpty())0 else (archiveOffset+PhotoMixPolicy.MEMORIES)%older.size
    require(items.isNotEmpty())
    val deadline = android.os.SystemClock.elapsedRealtime() + 120000
    var sourceBytes=0L
    for((index,item) in items.withIndex()) {
     if(index>0 && index%PhotoPoolPolicy.BATCH==0) Thread.sleep(250)
     require(android.os.SystemClock.elapsedRealtime() < deadline)
     stage = "thumbnail download"
     val bytes = api.thumbnail(item)
     stage = "thumbnail decoding"
     val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
     BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
     val sample=BitmapPolicy.sampleSize(bounds.outWidth,bounds.outHeight)
     val bitmap = BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("Invalid image")
     val file = File(dir(c),"$now-${item.getInt("id")}.jpg")
     val temporary=File(dir(c),file.name+".tmp")
     try {
      temporary.outputStream().use { require(bitmap.compress(Bitmap.CompressFormat.JPEG,88,it)) }
      require(temporary.length()>0 && temporary.renameTo(file))
      created.add(file);sourceBytes+=file.length();require(sourceBytes<=PhotoPoolPolicy.NEW_SET_BYTES)
     } finally { temporary.delete(); bitmap.recycle() }
    }
    require(created.sumOf { it.length() } <= PhotoPoolPolicy.NEW_SET_BYTES)
    // Publish a complete generation before pruning any previous files.
    require(state.edit().remove("diagnostic").putLong("success",now).putString("generation", "$now-").putInt("generationCount",created.size).putString("generationOrder",org.json.JSONArray(created.map {it.name}).toString()).putInt("poolLimit",PhotoPoolPolicy.LIMIT).putInt("archiveOffset",nextArchiveOffset).putInt("mixVersion",1).putString("photoTimes",org.json.JSONObject().apply{items.forEach{put(it.getInt("id").toString(),it.optLong("time"))}}.toString()).putInt("catalogCount",catalog.size).putLong("newestPhotoTime",recent.first().getLong("time")).putLong("oldestSelectedTime",items.minOf{it.getLong("time")}).commit())
    committed = true
    // Old launcher URI reads can safely alias into the committed set after pruning.
    val old = files(c).filter { it !in created }
    old.filter { now - it.lastModified() > 7200000 }.forEach { it.delete() }
    old.sortedByDescending { it.lastModified() }.drop(12).forEach { it.delete() }
    var retainedBytes=files(c).sumOf {it.length()}
    files(c).filter {it !in created}.sortedBy {it.lastModified()}.forEach {if(retainedBytes>PhotoPoolPolicy.TOTAL_SOURCE_BYTES){retainedBytes-=it.length();it.delete()}}
    message = "Connected • ${created.size} photos • Recent & memories • Personal Space"
    c.sendBroadcast(Intent(WallpaperProviderContract.ACTION_WALLPAPER_PROVIDER_UPDATED).setPackage("com.spocky.projengmenu")
     .putExtra(WallpaperProviderContract.EXTRA_PROVIDER_ID,UUID).putExtra(WallpaperProviderContract.EXTRA_UPDATE_REASON,2))
   } catch(e: ApiFailure) {
    message = when(e.code) { 106,107,119 -> "Session expired. Sign in again. Previous photos remain available."
     403,404,406 -> "Complete sign-in or MFA in settings."
     else -> "Synology rejected the request (code ${e.code}). Previous photos remain available." }
   } catch(e: Exception) {
    val source=e.stackTrace.firstOrNull { it.className.startsWith("org.ambienttv.home.") }
    state.edit().putString("diagnostic", "$stage: ${e.javaClass.simpleName} at ${source?.fileName}:${source?.lineNumber}" + if(e is SafeFailure) " ${e.reason}" else "").commit()
    message=when(e) {
     is HttpFailure -> "NAS returned HTTP ${e.status} during $stage. Your sign-in is saved."
     is javax.net.ssl.SSLException -> "TLS certificate check failed. Use a trusted HTTPS NAS address."
     is java.net.SocketTimeoutException -> "NAS timed out during $stage. Try Refresh photos."
     is java.net.ConnectException, is java.net.UnknownHostException -> "Cannot reach the NAS during $stage. Check its address and network."
     is org.json.JSONException -> "NAS returned an unexpected response during $stage."
     is IllegalArgumentException -> "Could not validate $stage. Your sign-in is saved; no need to enter it again."
     else -> "Could not complete $stage. Your sign-in is saved."
    }
   }
   finally {
    if(!committed) created.forEach { it.delete() }
    state.edit().putString("status",message).commit(); running = false; done?.invoke(message)
   }
  }
 }
 fun hasCompleteCache(c: Context): Boolean {
  val expected=c.getSharedPreferences("state",0).getInt("generationCount",0)
  return ImagePolicy.completeGeneration(expected,currentFiles(c).size)
 }
 fun currentFiles(c: Context): List<File> {
  if(AppPreferences.demo(c))return DemoPhotos.files(c)
  val generation=c.getSharedPreferences("state",0).getString("generation","missing")!!
  val saved=try {val array=org.json.JSONArray(c.getSharedPreferences("state",0).getString("generationOrder","[]"));(0 until array.length()).map {array.getString(it)}}catch(_:Exception){emptyList()}
  return PhotoPoolPolicy.ordered(files(c).map {it.name},generation,saved).map {File(dir(c),it)}
 }
 fun current(c: Context): List<Wallpaper> {
  val weatherBucket=System.currentTimeMillis()/60000
  return TriptychPolicy.anchors(currentFiles(c)).map {
   Wallpaper("content://org.ambienttv.home.photos/${it.name}?render=6&w=$weatherBucket", WallpaperType.IMAGE, WallpaperDisplayMode.FIT_CENTER)
  }
 }
 @Synchronized fun clear(c: Context) { require(!running); Vault(c).clear(); files(c).forEach { it.delete() }; c.getSharedPreferences("state",0).edit().clear().commit(); lastAttempt=0; WeatherRenderer.clear(c); WeatherStore.clear(c) }
}
