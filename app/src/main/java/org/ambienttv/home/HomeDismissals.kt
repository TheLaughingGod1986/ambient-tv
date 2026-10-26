package org.ambienttv.home

import android.content.Context
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.UUID

/** Content identity never includes a provider row ID or playback position. */
object HomeDismissalPolicy {
 fun identity(owner:String,link:String):String {
  val decoded=try{URLDecoder.decode(link,"UTF-8")}catch(_:Exception){link}
  val video=if(owner=="com.google.android.youtube.tv")Regex("(?:[?&]v=|youtu\\.be/|/shorts/|/embed/|(?:S\\.)?video_id=)([A-Za-z0-9_-]{11})(?:[^A-Za-z0-9_-]|$)").find(decoded)?.groupValues?.get(1) else null
  val normalized=video?.let{"video:$it"} ?: try {
   val uri=URI(link.substringBefore("#Intent;"));val query=uri.rawQuery?.split('&')?.filterNot{it.substringBefore('=').lowercase() in setOf("t","start","time_continue","position","offset") }?.sorted()?.joinToString("&")
   val extras=link.substringAfter("#Intent;","").split(';').filter{it.isNotBlank() && !it.matches(Regex("(?:i|l|S)\\.(?:position|start|start_time|start_time_ms)=.*"))}.sorted().joinToString(";")
   URI(uri.scheme,uri.authority,uri.path,query,null).toString()+if(extras.isEmpty())""else "#Intent;"+extras
  }catch(_:Exception){decoded.replace(Regex(";(?:i|l|S)\\.(?:position|start|start_time|start_time_ms)=[^;]*"),"")}
  return MessageDigest.getInstance("SHA-256").digest((owner+"|"+normalized).toByteArray()).joinToString(""){"%02x".format(it)}
 }
 fun completed(position:Long,duration:Long):Boolean = position>0 && duration>0 && (position>=duration || duration-position<=minOf(30000L,(duration/20).coerceAtLeast(1)))
}

/** Local launcher preferences only. No native history or server state is written. */
object HomeDismissals {
 private fun prefs(c:Context)=c.getSharedPreferences("home-hidden-videos",0)
 fun rotatePlexScope(c:Context){prefs(c).edit().putString("plexScope",UUID.randomUUID().toString()).apply()}
 private fun key(c:Context,item:WatchItem):String {
  val p=prefs(c)
  val scope=if(item.owner=="com.plexapp.android")p.getString("plexScope",null) ?: UUID.randomUUID().toString().also{p.edit().putString("plexScope",it).apply()} else "published-feed"
  return scope+":"+HomeDismissalPolicy.identity(item.owner,item.intentUri)
 }
 fun hidden(c:Context,item:WatchItem)=prefs(c).getStringSet("hidden",emptySet()).orEmpty().contains(key(c,item))
 fun dismiss(c:Context,item:WatchItem):String {
  val key=key(c,item);val p=prefs(c);val next=p.getStringSet("hidden",emptySet()).orEmpty().toMutableSet();next.add(key)
  p.edit().putStringSet("hidden",next).putString("undo",key).apply();return key
 }
 fun undo(c:Context,key:String?=null):Boolean {
  val p=prefs(c);val target=key ?: p.getString("undo",null) ?: return false
  val next=p.getStringSet("hidden",emptySet()).orEmpty().toMutableSet();val removed=next.remove(target)
  p.edit().putStringSet("hidden",next).remove("undo").apply();return removed
 }
 fun count(c:Context)=prefs(c).getStringSet("hidden",emptySet()).orEmpty().size
 fun restore(c:Context){prefs(c).edit().remove("hidden").remove("undo").apply()}
}

/** Consume a held OK's repeat and release so it can never also launch playback. */
internal class RemoteCardPress {
 private var pressed=false
 private var held=false
 fun down(repeat:Boolean):Boolean {if(!repeat){pressed=true;held=false;return true};return false}
 fun hold():Boolean {if(!pressed || held)return false;held=true;return true}
 fun up(cancelled:Boolean):Boolean {val click=pressed && !held && !cancelled;cancel();return click}
 fun cancel(){pressed=false;held=false}
}
