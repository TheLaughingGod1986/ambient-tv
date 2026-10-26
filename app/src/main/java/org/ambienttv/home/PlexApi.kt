package org.ambienttv.home
import android.content.Context
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

internal class PlexHttpFailure(val status:Int):Exception()
internal data class PlexPin(val id:Long,val code:String,val expiresAt:Long)
internal object PlexApi {
 private const val CLOUD="https://plex.tv/api/v2"
 fun client(c:Context):String{val p=c.getSharedPreferences("plex-config",0);return p.getString("client",null) ?: UUID.randomUUID().toString().also{p.edit().putString("client",it).commit()}}
 fun vault(c:Context)=Vault(c,"plex-session","ambient-plex-session")
 fun request(c:Context,url:String,token:String?=null,body:String?=null,maxBytes:Int=2*1024*1024):ByteArray {
  val conn=URL(url).openConnection() as HttpURLConnection
  conn.connectTimeout=5000;conn.readTimeout=5000;conn.instanceFollowRedirects=false
  conn.setRequestProperty("Accept","application/json");conn.setRequestProperty("X-Plex-Client-Identifier",client(c));conn.setRequestProperty("X-Plex-Product","Ambient TV");conn.setRequestProperty("X-Plex-Version","0.13");conn.setRequestProperty("X-Plex-Platform","Android");conn.setRequestProperty("X-Plex-Device","TV")
  if(token!=null)conn.setRequestProperty("X-Plex-Token",token)
  try{
   if(body!=null){conn.requestMethod="POST";conn.doOutput=true;conn.setRequestProperty("Content-Type","application/x-www-form-urlencoded");conn.outputStream.use{it.write(body.toByteArray(Charsets.UTF_8))}}
   if(conn.responseCode !in 200..299)throw PlexHttpFailure(conn.responseCode)
   return conn.inputStream.use{input->val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=maxBytes);out.write(buffer,0,n)};out.toByteArray()}
  }finally{conn.disconnect()}
 }
 fun createPin(c:Context):PlexPin{
  val j=JSONObject(String(request(c,"$CLOUD/pins",body="strong=false"),Charsets.UTF_8))
  val code=j.getString("code");require(code.matches(Regex("[A-Za-z0-9]{4,12}")))
  return PlexPin(j.getLong("id"),code,System.currentTimeMillis()+j.optLong("expiresIn",900).coerceIn(30,1800)*1000)
 }
 fun poll(c:Context,pin:PlexPin):String?{
  val j=JSONObject(String(request(c,"$CLOUD/pins/${pin.id}?code="+URLEncoder.encode(pin.code,"UTF-8")),Charsets.UTF_8))
  return if(j.isNull("authToken"))null else j.optString("authToken").takeIf{it.isNotBlank()}
 }
 fun resources(c:Context,token:String)=JSONArray(String(request(c,"$CLOUD/resources?includeHttps=1&includeRelay=1",token),Charsets.UTF_8))
}
