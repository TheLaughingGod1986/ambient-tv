package org.ambienttv.home

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object WeatherStore {
 private val worker=Executors.newSingleThreadExecutor()
 @Volatile private var running=false
 private fun prefs(c: Context)=c.getSharedPreferences("weather",Context.MODE_PRIVATE)
 fun read(c: Context): WeatherData? = try { prefs(c).getString("data",null)?.let { WeatherData.stored(it) } } catch(_: Exception) { null }
 fun panel(c: Context, now: Long=System.currentTimeMillis()): WeatherPanel {
  if(!AppPreferences.weatherEnabled(c))return WeatherPanel("—","Weather is off","Enable in photo settings",null,true)
  val data=read(c)
  return WeatherPanel.from(data,now,prefs(c).getLong("failedAt",0)>(data?.fetchedAt ?: 0))
 }
 @Synchronized fun refresh(c: Context) {
  val endpoint=AppPreferences.weatherEndpoint(c) ?: return
  val now=System.currentTimeMillis(); val prefs=prefs(c)
  val data=read(c)
  if(running || !WeatherFetchPolicy.shouldFetch(now,data?.fetchedAt,prefs.getLong("attemptAt",0))) return
  running=true; prefs.edit().putLong("attemptAt",now).apply()
  worker.execute {
   var connection: HttpURLConnection?=null
   try {
    connection=URL(endpoint).openConnection() as HttpURLConnection
    connection.connectTimeout=4000; connection.readTimeout=4000; connection.instanceFollowRedirects=false
    require(connection.responseCode==200)
    val bytes=connection.inputStream.use { input ->
     val out=java.io.ByteArrayOutputStream(); val buffer=ByteArray(4096)
     while(true) { val n=input.read(buffer); if(n<0) break; require(out.size()+n<=32768); out.write(buffer,0,n) }; out.toByteArray()
    }
    val current=WeatherData.parse(String(bytes,Charsets.UTF_8),System.currentTimeMillis())
    prefs.edit().putString("data",current.json()).remove("failedAt").apply()
   } catch(_: Exception) { prefs.edit().putLong("failedAt",System.currentTimeMillis()).apply() }
   finally { connection?.disconnect(); running=false }
  }
 }
 fun clear(c: Context) { prefs(c).edit().clear().apply() }
}
