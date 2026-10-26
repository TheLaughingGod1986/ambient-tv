package org.ambienttv.home

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Process
import tv.projectivy.plugin.wallpaperprovider.api.*

fun checkReader(c: android.content.Context) {
 val uid = Binder.getCallingUid()
 if(uid == Process.myUid()) return
 val allowed = try { c.packageManager.getApplicationInfo("com.spocky.projengmenu",0).uid } catch(_: Exception) { -1 }
 if(uid != allowed) throw SecurityException("Launcher access only")
}
class WallpaperService : Service() {
 override fun onBind(intent: Intent): IBinder = object : IWallpaperProviderService.Stub() {
  override fun getWallpapers(event: Event?): List<Wallpaper> {
   checkReader(this@WallpaperService)
   if(event !is Event.TimeElapsed) return emptyList()
   Photos.refresh(applicationContext)
   WeatherStore.refresh(applicationContext)
   val items = Photos.current(applicationContext)
   getSharedPreferences("state",0).edit().putLong("lastLauncherRequest",System.currentTimeMillis()).putInt("servedCount",items.size).apply()
   return items
  }
  override fun getPreferences(): String { checkReader(this@WallpaperService); return "{}" }
  override fun setPreferences(params: String) { checkReader(this@WallpaperService) /* Sessions never exported or imported. */ }
 }
}
