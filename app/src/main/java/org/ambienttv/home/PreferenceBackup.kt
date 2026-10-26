package org.ambienttv.home

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Strict versioned allowlist. No addresses, accounts, session tokens, images or history. */
data class PreferenceSnapshot(val style:String,val columns:Int,val framing:String,val overrides:Map<String,String>,val favourites:Set<String>)
object BackupPolicy {
 const val MAX_BYTES=262144
 private fun id(value:String)=value.matches(Regex("[A-Za-z0-9_-][A-Za-z0-9_.-]{0,100}")) && !value.contains("..")
 private fun keys(o:JSONObject,allowed:Set<String>){require(o.keys().asSequence().toSet()==allowed){"Unsupported backup fields"}}
 fun encode(s:PreferenceSnapshot):String=JSONObject().put("format","ambient-tv-preferences").put("version",1)
  .put("style",s.style).put("columns",s.columns).put("framing",s.framing)
  .put("photoOverrides",JSONObject(s.overrides)).put("favourites",JSONArray(s.favourites.sorted())).toString(2)
 fun decode(text:String):PreferenceSnapshot {
  require(text.toByteArray(Charsets.UTF_8).size<=MAX_BYTES){"Backup is too large"}
  val o=JSONObject(text);keys(o,setOf("format","version","style","columns","framing","photoOverrides","favourites"))
  require(o.getString("format")=="ambient-tv-preferences" && o.get("version")==1){"Unsupported backup version"}
  val style=o.getString("style");val columns=o.get("columns");val framing=o.getString("framing")
  require(style in listOf("hero","classic") && columns is Int && columns in 0..3 && framing in FramingPolicy.modes){"Invalid display preferences"}
  val overrides=o.getJSONObject("photoOverrides");require(overrides.length()<=1000)
  val map=overrides.keys().asSequence().associateWith{key->require(id(key));overrides.getString(key).also{require(it in FramingPolicy.modes)}}
  val list=o.getJSONArray("favourites");require(list.length()<=5000)
  val favourites=(0 until list.length()).map{list.getString(it).also{value->require(id(value))}}.toSet()
  return PreferenceSnapshot(style,columns,framing,map,favourites)
 }
}
object PreferenceBackup {
 fun snapshot(c:Context):PreferenceSnapshot {
  val d=c.getSharedPreferences("dream",0);val f=c.getSharedPreferences("photo-framing",0)
  val overrides=f.all.filterKeys{it.startsWith("photo:")}.mapKeys{it.key.removePrefix("photo:")}.mapValues{it.value as String}
  return PreferenceSnapshot(d.getString("style","hero") ?: "hero",d.getInt("columns",0),PhotoFraming.mode(c),overrides,c.getSharedPreferences("favourites",0).getStringSet("ids",emptySet()).orEmpty().toSet())
 }
 fun restore(c:Context,s:PreferenceSnapshot){
  val validated=BackupPolicy.decode(BackupPolicy.encode(s));val old=snapshot(c)
  fun write(v:PreferenceSnapshot){
   check(c.getSharedPreferences("dream",0).edit().putString("style",v.style).putInt("columns",v.columns).commit())
   val f=c.getSharedPreferences("photo-framing",0).edit().clear().putString("default",v.framing)
   v.overrides.forEach{(key,value)->f.putString("photo:$key",value)};check(f.commit())
   check(c.getSharedPreferences("favourites",0).edit().putStringSet("ids",v.favourites).commit())
  }
  try{write(validated)}catch(e:Exception){try{write(old)}catch(_:Exception){};throw e}
 }
}
