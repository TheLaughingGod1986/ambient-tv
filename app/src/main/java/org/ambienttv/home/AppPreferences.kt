package org.ambienttv.home

import android.content.Context
import java.util.Locale

/** User choices stay on the TV. No household configuration is built into the APK. */
internal object AppPreferences {
 private fun prefs(c:Context)=c.getSharedPreferences("setup",Context.MODE_PRIVATE)
 fun saved(c:Context,key:String)=prefs(c).getString(key,"").orEmpty()
 fun person(c:Context)=saved(c,"person")
 fun savePerson(c:Context,name:String){require(name.isNotBlank());prefs(c).edit().putString("person",name.trim()).commit()}
 fun demo(c:Context)=prefs(c).getBoolean("demo",true)
 fun setDemo(c:Context,enabled:Boolean){prefs(c).edit().putBoolean("demo",enabled).commit()}
 fun weatherEnabled(c:Context)=prefs(c).getBoolean("weatherEnabled",false)
 fun weatherLabel(c:Context)=if(weatherEnabled(c))saved(c,"weatherLabel").take(24) else "Weather is off"
 fun saveWeather(c:Context,enabled:Boolean,label:String,latitude:String,longitude:String){
  if(enabled)WeatherLocation.endpoint(latitude,longitude).also{require(label.trim().length in 1..24)}
  prefs(c).edit().putBoolean("weatherEnabled",enabled).putString("weatherLabel",label.trim().take(24))
   .putString("latitude",latitude.trim()).putString("longitude",longitude.trim()).commit()
 }
 fun weatherEndpoint(c:Context):String? = if(!weatherEnabled(c))null else try{WeatherLocation.endpoint(saved(c,"latitude"),saved(c,"longitude"))}catch(_:IllegalArgumentException){null}
}

internal object WeatherLocation {
 fun endpoint(latitude:String,longitude:String):String {
  val lat=latitude.trim().toDouble();val lon=longitude.trim().toDouble()
  require(lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0)
  return String.format(Locale.ROOT,"https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,is_day&temperature_unit=celsius&timeformat=unixtime&timezone=auto&forecast_days=1",lat,lon)
 }
}

/** Literal private addresses only: no DNS lookup or TLS validation bypass. */
internal object ConnectionPolicy {
 fun isPrivateIpv4(host:String):Boolean {
  val parts=host.split('.')
  if(parts.size!=4 || parts.any{it.isEmpty() || it.any{c->c !in '0'..'9'} || (it.length>1 && it.startsWith('0'))})return false
  val octets=parts.map{it.toIntOrNull() ?: return false}
  if(octets.any{it !in 0..255})return false
  return octets[0]==10 || (octets[0]==172 && octets[1] in 16..31) || (octets[0]==192 && octets[1]==168)
 }
}
