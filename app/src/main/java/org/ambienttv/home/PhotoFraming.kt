package org.ambienttv.home

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import kotlin.math.*

/** Explicit local presentation choices; originals and provider metadata are never changed. */
object FramingPolicy {
 val modes=listOf("auto","full","top","center","bottom")
 val labels=listOf("Automatic cinematic","Full photo","Crop toward top","Crop toward centre","Crop toward bottom")
 fun place(w:Int,h:Int,face:FaceRegion?,mode:String):HeroPlacement {
  require(w>0 && h>0 && mode in modes)
  if(mode=="auto")return HeroComposition.place(w,h,face)
  val scale=if(mode=="full")min(1920f/w,1080f/h)else max(1320f/w,1080f/h)
  val width=w*scale;val height=h*scale
  val y=when(mode){"top"->0f;"bottom"->1080f-height;else->(1080f-height)/2}
  return HeroPlacement(0f,y,width,height)
 }
}
object PhotoFraming {
 private fun prefs(c:Context)=c.getSharedPreferences("photo-framing",0)
 fun mode(c:Context,key:String?=null):String {
  val p=prefs(c);return (key?.let{p.getString("photo:$it",null)} ?: p.getString("default","auto")).takeIf{it in FramingPolicy.modes} ?: "auto"
 }
 fun choose(c:Activity,key:String?=null,changed:()->Unit={}) {
  val options=if(key==null)FramingPolicy.labels else FramingPolicy.labels+"Use global setting"
  AlertDialog.Builder(c).setTitle(if(key==null)"Default photo framing" else "Framing for this photo")
   .setSingleChoiceItems(options.toTypedArray(),if(key!=null && !prefs(c).contains("photo:$key"))5 else FramingPolicy.modes.indexOf(mode(c,key))){dialog,index->
    val edit=prefs(c).edit();val name=key?.let{"photo:$it"} ?: "default"
    if(index==5)edit.remove(name)else edit.putString(name,FramingPolicy.modes[index]);edit.apply();changed();dialog.dismiss()
   }.setNegativeButton("Cancel",null).show()
 }
}
