package org.ambienttv.home

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

class ApiFailure(val code: Int) : Exception()
class SafeFailure(val reason: String) : Exception()
class HttpFailure(val status: Int, val detail: String = "") : Exception()
class Synology(val base: String, private val sid: String = "") {
 companion object {
  fun validateBase(raw: String): String {
   val uri = URI(raw.trim().trimEnd('/'))
   require(uri.scheme in listOf("https", "http") && !uri.host.isNullOrEmpty() && uri.userInfo == null && uri.query == null && uri.fragment == null && uri.path.orEmpty().isEmpty())
   // HTTP is limited to private IPv4 literals; the UI also requires explicit consent.
   require(uri.scheme == "https" || ConnectionPolicy.isPrivateIpv4(uri.host))
   return uri.toString()
  }
 }
 private fun connection(params: Map<String, String>, get: Boolean=false,path:String="entry.cgi"): HttpURLConnection {
  val all = if(sid.isEmpty() || get) params else params + ("_sid" to sid)
  val body = all.entries.joinToString("&") { URLEncoder.encode(it.key,"UTF-8") + "=" + URLEncoder.encode(it.value,"UTF-8") }
  val conn = URL("$base/webapi/${SynologyApiPolicy.path(path)}" + if(get) "?$body" else "").openConnection() as HttpURLConnection
  conn.requestMethod = if(get) "GET" else "POST"; conn.doOutput = !get; conn.instanceFollowRedirects = false
  conn.connectTimeout = 5000; conn.readTimeout = 8000
  if(sid.isNotEmpty()) conn.setRequestProperty("Cookie", "id=$sid")
  if(!get) {
   conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
   conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
  }
  return conn
 }

 fun call(api:String,method:String,params:Map<String,String> = emptyMap(),version:Int=1):JSONObject {
  try{return request(api,method,params,version,"entry.cgi")}
  catch(e:ApiFailure){
   if(api=="SYNO.API.Info" || e.code !in listOf(102,104))throw e
   val info=request("SYNO.API.Info","query",mapOf("query" to api),1,"entry.cgi").optJSONObject(api) ?: throw e
   val path=SynologyApiPolicy.path(info.getString("path"))
   val supported=SynologyApiPolicy.version(version,info.getInt("minVersion"),info.getInt("maxVersion"))
   if(path=="entry.cgi" && supported==version)throw e
   return request(api,method,params,supported,path)
  }
 }
 private fun request(api:String,method:String,params:Map<String,String>,version:Int,path:String):JSONObject {
  val conn=connection(params+mapOf("api" to api,"method" to method,"version" to "$version"),path=path)
  try {
   if(conn.responseCode != 200) throw HttpFailure(conn.responseCode)
   val bytes = conn.inputStream.use { it.readBytesBounded(2 * 1024 * 1024) }
   val json = JSONObject(String(bytes, Charsets.UTF_8))
   if(!json.optBoolean("success")) throw ApiFailure(json.optJSONObject("error")?.optInt("code") ?: -1)
   return json.getJSONObject("data")
  } finally { conn.disconnect() }
 }
 fun login(user: String, password: String, otp: String): String {
  val params = mutableMapOf("account" to user, "passwd" to password, "session" to "SynologyPhotos", "format" to "sid")
  if(otp.isNotBlank()) params["otp_code"] = otp
  return call("SYNO.API.Auth", "login", params, 6).getString("sid")
 }
 fun personId(name: String): Int {
  require(name.isNotBlank()) { "Enter a person name in Photos settings" }
  val matches = mutableListOf<Int>()
  for(offset in 0 until 2000 step 100) {
   val list = call("SYNO.Foto.Browse.Person", "list", mapOf("offset" to "$offset", "limit" to "100")).getJSONArray("list")
   for(i in 0 until list.length()) {
    val p = list.getJSONObject(i)
    if(p.optString("name") == name) matches.add(p.getInt("id"))
   }
   if(list.length() < 100) break
   require(offset < 1900) { "Too many people" }
  }
  require(matches.size == 1) { "Person selection missing or ambiguous" }
  return matches.single()
 }
 fun newest(person: Int): List<JSONObject> = photoPage(person,0,PhotoPoolPolicy.LIMIT,false)
 fun photoCatalog(person:Int):List<JSONObject> {
  val items=mutableListOf<JSONObject>()
  // Person timelines on some Photos versions ignore sort_direction. Read bounded
  // metadata pages and order their actual dates locally, never guess an archive age.
  for(offset in 0 until 10000 step 500) {
   val list=call("SYNO.Foto.Browse.Item","list",mapOf("person_id" to "$person","offset" to "$offset","limit" to "500","type" to "photo","additional" to "[\"thumbnail\",\"person\"]"),7).getJSONArray("list")
   for(i in 0 until list.length()) {
    val item=list.getJSONObject(i)
    require(item.optString("type") in listOf("photo","live") && item.has("time"))
    val evidence=item.optJSONObject("additional")?.opt("person")
    if(evidence !is org.json.JSONArray)throw SafeFailure("Person membership unavailable: shape=${evidence?.javaClass?.simpleName ?: "missing"}")
    if((0 until evidence.length()).any{evidence.optJSONObject(it)?.optInt("id",-1)==person})items.add(item)
   }
   if(list.length()<500)return items.distinctBy{it.getInt("id")}.sortedByDescending{it.getLong("time")}
  }
  throw SafeFailure("Photo catalog exceeds the bounded 10000-photo scan; previous photos kept")
 }
 fun photoPage(person:Int,offset:Int,limit:Int,oldestFirst:Boolean):List<JSONObject> {
  require(offset in 0..1000000 && limit in 1..PhotoPoolPolicy.LIMIT)
  val list = call("SYNO.Foto.Browse.Item", "list", mapOf("person_id" to "$person", "offset" to "$offset", "limit" to limit.toString(), "type" to "photo", "sort_by" to JSONObject.quote("takentime"), "sort_direction" to JSONObject.quote(if(oldestFirst)"asc" else "desc"), "additional" to "[\"thumbnail\",\"person\"]"), 1).getJSONArray("list")
  val items = (0 until list.length()).map { list.getJSONObject(it) }
  if(!items.all { it.optString("type") in listOf("photo", "live") && it.has("time") }) throw SafeFailure("Invalid photo item type or missing date")
  require(items.zipWithNext().all { (a,b) -> if(oldestFirst)a.getLong("time") <= b.getLong("time") else a.getLong("time") >= b.getLong("time") }) { "NAS did not return requested date order" }
  return items.distinctBy {it.getInt("id")}.take(limit)
 }
 fun thumbnail(item: JSONObject): ByteArray {
  val thumb=item.optJSONObject("additional")?.optJSONObject("thumbnail")
  val unit=thumb?.optInt("unit_id",item.getInt("id")) ?: item.getInt("id")
  val size=if(thumb?.optString("xl")=="ready") "xl" else if(thumb?.optString("m")=="ready") "m" else "xl"
  val params = mutableMapOf("api" to "SYNO.Foto.Thumbnail", "method" to "get", "version" to "1", "mode" to "download", "id" to unit.toString(), "type" to "unit", "size" to size)
  thumb?.optString("cache_key")?.takeIf { it.isNotEmpty() }?.let { params["cache_key"] = it }
  val conn = connection(params, get=true)
  try {
   if(conn.responseCode != 200) throw HttpFailure(conn.responseCode,"thumbnailPresent=${thumb!=null}, cacheKeyPresent=${params.containsKey("cache_key")}, unitMatches=${unit==item.getInt("id")}, xlReady=${thumb?.optString("xl")=="ready"}, mReady=${thumb?.optString("m")=="ready"}")
   val bytes=conn.inputStream.use { it.readBytesBounded(6 * 1024 * 1024) }
   if(bytes.firstOrNull()?.toInt()==123) {
    val json=JSONObject(String(bytes,Charsets.UTF_8))
    if(!json.optBoolean("success")) throw ApiFailure(json.optJSONObject("error")?.optInt("code") ?: -1)
   }
   return bytes
  }
  finally { conn.disconnect() }
 }
}
private fun java.io.InputStream.readBytesBounded(max: Int): ByteArray {
 val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(16384)
 while(true) { val n = read(buffer); if(n < 0) break; require(out.size() + n <= max); out.write(buffer,0,n) }
 return out.toByteArray()
}
