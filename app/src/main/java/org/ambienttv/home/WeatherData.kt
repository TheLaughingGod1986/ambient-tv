package org.ambienttv.home

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/** Public area forecast only. No NAS data or device location is sent to the weather API. */
data class WeatherData(val temperatureC: Double, val code: Int, val isDay: Boolean, val observedAt: Long, val fetchedAt: Long) {
 companion object {
  const val TTL = 30 * 60 * 1000L
  const val MAX_AGE = 6 * 60 * 60 * 1000L
  fun parse(raw: String, now: Long): WeatherData {
   val json=JSONObject(raw)
   require(json.getJSONObject("current_units").getString("temperature_2m")=="°C")
   val current=json.getJSONObject("current")
   val temp=current.getDouble("temperature_2m")
   val time=current.getLong("time") * 1000
   require(temp.isFinite() && temp in -80.0..60.0)
   require(time in (now-24*60*60*1000)..(now+15*60*1000))
   val day=current.getInt("is_day")
   require(day==0 || day==1)
   return WeatherData(temp,current.getInt("weather_code"),day==1,time,now)
  }
  fun stored(json: String): WeatherData {
   val j=JSONObject(json)
   return WeatherData(j.getDouble("temperatureC"),j.getInt("code"),j.getBoolean("day"),j.getLong("observedAt"),j.getLong("fetchedAt"))
  }
 }
 fun json()=JSONObject().put("temperatureC",temperatureC).put("code",code).put("day",isDay).put("observedAt",observedAt).put("fetchedAt",fetchedAt).toString()
 fun condition(): String=when(code) {
  0 -> if(isDay) "Clear sky" else "Clear night"
  1 -> "Mainly clear"; 2 -> "Partly cloudy"; 3 -> "Overcast"
  45,48 -> "Fog"; 51,53,55 -> "Drizzle"; 56,57 -> "Freezing drizzle"
  61 -> "Light rain"; 63 -> "Rain"; 65 -> "Heavy rain"; 66,67 -> "Freezing rain"
  71 -> "Light snow"; 73,75,77 -> "Snow"; 80,81,82 -> "Rain showers"
  85,86 -> "Snow showers"; 95,97 -> "Thunderstorm"; 96,99 -> "Thunderstorm with hail"
  else -> "Conditions unavailable"
 }
}

data class WeatherPanel(val temperature: String, val condition: String, val freshness: String, val code: Int?, val isDay: Boolean) {
 companion object {
  fun from(data: WeatherData?, now: Long, failed: Boolean=false, zone:TimeZone=TimeZone.getDefault()): WeatherPanel {
   if(data==null || now-data.observedAt>90*60*1000L || data.observedAt>now+900000)
    return WeatherPanel("—", "Weather unavailable", "Photos continue offline",null,true)
   val time=SimpleDateFormat("HH:mm",Locale.getDefault()).apply { timeZone=zone }.format(Date(data.observedAt))
   val stale=now-data.observedAt>90*60*1000
   val suffix=if(stale) " · stale" else if(failed) " · offline" else ""
   return WeatherPanel("${data.temperatureC.roundToInt()}°C",data.condition(),"Updated $time$suffix",data.code.takeUnless { data.condition()=="Conditions unavailable" },data.isDay)
  }
 }
}

/** Coordinates are in a fixed 1920×1080 frame: in the header, clear of three content rows and the clock at the right. */
object WeatherLayout {
 const val WIDTH=1920; const val HEIGHT=1080
 const val LEFT=96f; const val TOP=54f; const val RIGHT=1000f; const val BOTTOM=118f
 const val TEXT_X=96f; const val TEMP_X=350f; const val CONDITION_X=560f
 const val LOCATION_Y=78f; const val TEMP_Y=82f; const val CONDITION_Y=80f
 const val FRESHNESS_Y=106f; const val CREDIT_Y=106f
 const val ICON_X=510f; const val ICON_Y=64f
 const val CREDIT="open-meteo.com · CC BY 4.0"
 const val LOCATION_SIZE=20f; const val TEMP_SIZE=36f; const val CONDITION_SIZE=22f
 const val FRESHNESS_SIZE=17f; const val CREDIT_SIZE=14f
 const val PHOTO_TOP=140f
 const val PHOTO_BOTTOM=694f

}

object WeatherFetchPolicy {
 fun shouldFetch(now: Long, fetchedAt: Long?, attemptedAt: Long): Boolean {
  if(fetchedAt!=null && now-fetchedAt in 0 until WeatherData.TTL) return false
  return attemptedAt<=0 || now-attemptedAt !in 0 until 15*60*1000L
 }
}
