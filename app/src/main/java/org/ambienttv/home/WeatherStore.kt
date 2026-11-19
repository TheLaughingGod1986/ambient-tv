package org.ambienttv.home

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object WeatherStore {
 private val worker=Executors.newSingleThreadExecutor()
 @Volatile private var running=false
 /** Emulator checks only: a loopback address in place of the weather service. */
 @Volatile internal var endpointForTest: String?=null
 private fun prefs(c: Context)=c.getSharedPreferences("weather",Context.MODE_PRIVATE)
 fun read(c: Context): WeatherData? = try { prefs(c).getString("data",null)?.let { WeatherData.stored(it) } } catch(_: Exception) { null }
 private fun lastTryFailed(c: Context)=prefs(c).getLong("failedAt",0)>(read(c)?.fetchedAt ?: 0)
 fun panel(c: Context, now: Long=System.currentTimeMillis()): WeatherPanel {
  if(!AppPreferences.weatherEnabled(c))return WeatherPanel("—","Weather is off","Enable in photo settings",null,true)
  val data=read(c); val prefs=prefs(c)
  val checking=running || WeatherFetchPolicy.expecting(now,data?.fetchedAt,prefs.getInt("failures",0))
  return WeatherPanel.from(data,now,prefs.getLong("failedAt",0)>(data?.fetchedAt ?: 0),checking=checking)
 }
 /** The wait before the next quick try after a failed one, or null when no quick try is due. */
 fun retryDelay(c: Context): Long? = if(lastTryFailed(c)) RetryPolicy.delay(prefs(c).getInt("failures",0)) else null
 /** The network has just come back: a reading that failed without it may be asked for again at once. */
 fun networkBack(c: Context) { if(lastTryFailed(c)) prefs(c).edit().remove("attemptAt").remove("failures").apply() }
 @Synchronized fun refresh(c: Context, done: (()->Unit)? = null) {
  val endpoint=endpointForTest ?: AppPreferences.weatherEndpoint(c) ?: return
  val now=System.currentTimeMillis(); val prefs=prefs(c)
  val data=read(c); val failures=prefs.getInt("failures",0)
  if(running || !WeatherFetchPolicy.shouldFetch(now,data?.fetchedAt,prefs.getLong("attemptAt",0),failures)) return
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
    prefs.edit().putString("data",current.json()).remove("failedAt").remove("failures").apply()
   } catch(_: Exception) { prefs.edit().putLong("failedAt",System.currentTimeMillis()).putInt("failures",(failures+1).coerceAtMost(1000)).apply() }
   finally { connection?.disconnect(); running=false; done?.invoke() }
  }
 }
 fun clear(c: Context) { prefs(c).edit().clear().apply() }
}
