package org.ambienttv.home

import android.app.ActivityManager
import android.content.Context

/**
 * One video app at a time. A TV has few hardware video decoders, and an app left paused behind Home can keep
 * one, so the next app's video fails to start or freezes. When Home opens an app it asks Android to close the
 * different app Home opened before it. Android closes only background processes: an app that is still doing
 * something for the viewer, such as playing music, is left alone. Android 14 and later ignore the request.
 */
internal object AppHandover {
 private const val STATE="home-ui"
 private const val LAST="lastOpenedApp"

 /** The app to close when Home opens [next] after [previous]: only a different app, and never Home itself. */
 fun toClose(previous:String?,next:String,self:String):String? =
  previous?.takeIf{it.isNotBlank() && next.isNotBlank() && it!=next && it!=self && next!=self}

 /** Call just before Home starts [next]. Remembers it, and closes the app left behind if the owner allows it. */
 fun opening(c:Context,next:String) {
  if(HomeActivity.previewFiles!=null || next.isBlank() || next==c.packageName)return
  val state=c.getSharedPreferences(STATE,0)
  val previous=state.getString(LAST,null)
  if(previous!=next)state.edit().putString(LAST,next).apply()
  val close=toClose(previous,next,c.packageName) ?: return
  if(!AppPreferences.closeLeftApp(c))return
  val manager=c.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
  // Off the main thread, so opening the app never waits for it.
  Thread({try{manager.killBackgroundProcesses(close)}catch(_:Exception){}},"app-handover").start()
 }
 internal fun lastForTest(c:Context)=c.getSharedPreferences(STATE,0).getString(LAST,null)
}
