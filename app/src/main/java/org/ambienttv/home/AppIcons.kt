package org.ambienttv.home

import android.content.Context
import android.graphics.drawable.Drawable
import java.util.concurrent.ConcurrentHashMap

/**
 * Launcher icons decoded at most once per process. Every caller receives its own drawable,
 * so bounds and tints set by one view never leak into another.
 */
internal object AppIcons {
 private const val LIMIT=256
 private val states=ConcurrentHashMap<String,Drawable.ConstantState>()
 fun has(key:String)=states.containsKey(key)
 fun state(key:String):Drawable.ConstantState? = states[key]
 fun cached(c:Context,key:String):Drawable? = states[key]?.newDrawable(c.resources)?.mutate()
 /** Asks PackageManager on a miss, so keep bulk loads on a worker thread. */
 fun load(c:Context,pkg:String):Drawable? {
  cached(c,pkg)?.let{return it}
  // Declared non-null by the platform, but treated as optional: a missing package must never crash Home.
  val icon:Drawable?=try{c.packageManager.getApplicationIcon(pkg)}catch(_:Exception){null}
  if(icon!=null)remember(pkg,icon)
  return icon
 }
 /** Bounded: once full, further icons are simply not kept (callers then load them as before). */
 fun remember(key:String,icon:Drawable):Drawable.ConstantState? = icon.constantState?.also{if(states.size<LIMIT || states.containsKey(key))states[key]=it}
 fun clear(){states.clear()}
}
