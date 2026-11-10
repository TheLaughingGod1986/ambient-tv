package org.ambienttv.home

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Real capture dates for captions. The NAS reports wall-clock time, so no zone shift is applied. */
internal object PhotoCaption {
 private fun format(pattern:String,seconds:Long,locale:Locale):String? =
  if(seconds<=0)null else SimpleDateFormat(pattern,locale).apply{timeZone=TimeZone.getTimeZone("UTC")}.format(Date(seconds*1000))
 fun monthYear(seconds:Long,locale:Locale=Locale.getDefault())=format("MMMM yyyy",seconds,locale)
 fun fullDate(seconds:Long,locale:Locale=Locale.getDefault())=format("d MMMM yyyy",seconds,locale)
 /** A cached file is named generation-id.jpg; [times] maps that id to capture seconds. Unknown stays unknown. */
 fun seconds(fileName:String,times:org.json.JSONObject)=times.optLong(fileName.substringAfter('-').substringBefore('.'),0)
 /** The day's extra photo when nothing is favourited yet: stable for a day, never one already shown. */
 fun extra(available:List<String>,shown:List<String?>,day:Long):String? {
  val rest=available.distinct().filter{it !in shown}.sorted()
  return if(rest.isEmpty())null else rest[Math.floorMod(day,rest.size.toLong()).toInt()]
 }
}
