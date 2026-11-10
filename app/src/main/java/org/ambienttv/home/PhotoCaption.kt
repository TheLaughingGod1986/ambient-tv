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
 private fun utc(seconds:Long)=java.util.GregorianCalendar(TimeZone.getTimeZone("UTC")).apply{timeInMillis=seconds*1000}
 /** How old the chosen person was when the picture was taken, or null without a birthday or before it. */
 fun age(taken:Long,birth:Long?):String? {
  if(birth==null || taken<=0 || taken<birth)return null
  val days=((taken-birth)/86400).toInt()
  if(days<1)return "Newborn"
  if(days<14)return if(days==1)"1 day old" else "$days days old"
  if(days<56)return "${days/7} weeks old"
  val a=utc(birth);val b=utc(taken)
  var months=(b.get(java.util.Calendar.YEAR)-a.get(java.util.Calendar.YEAR))*12+b.get(java.util.Calendar.MONTH)-a.get(java.util.Calendar.MONTH)
  if(b.get(java.util.Calendar.DAY_OF_MONTH)<a.get(java.util.Calendar.DAY_OF_MONTH))months--
  return if(months<2)"${days/7} weeks old" else if(months<24)"$months months old" else if(months/12==1)"1 year old" else "${months/12} years old"
 }
 /** Whole years between the capture day and today when both fall on the same day of the year; null otherwise. */
 fun yearsAgoToday(taken:Long,now:Long,zone:TimeZone=TimeZone.getTimeZone("Europe/London")):Int? {
  if(taken<=0)return null
  val then=utc(taken);val today=java.util.GregorianCalendar(zone).apply{timeInMillis=now}
  if(then.get(java.util.Calendar.MONTH)!=today.get(java.util.Calendar.MONTH) || then.get(java.util.Calendar.DAY_OF_MONTH)!=today.get(java.util.Calendar.DAY_OF_MONTH))return null
  return (today.get(java.util.Calendar.YEAR)-then.get(java.util.Calendar.YEAR)).takeIf{it>=1}
 }
 /** Home caption: "2 years ago today" on an anniversary, otherwise the month, then the age when known. */
 fun headline(taken:Long,now:Long,birth:Long?,locale:Locale=Locale.getDefault()):String? =
  lines(taken,now,birth,locale)?.let{listOfNotNull(it.first,it.second).joinToString("  ·  ")}
 /** The same caption as two lines for the screensaver: when it was taken, then the age if known. */
 fun lines(taken:Long,now:Long,birth:Long?,locale:Locale=Locale.getDefault()):Pair<String,String?>? {
  val whenTaken=yearsAgoToday(taken,now)?.let{if(it==1)"A year ago today" else "$it years ago today"} ?: monthYear(taken,locale) ?: return null
  return whenTaken to age(taken,birth)
 }
 /** Viewer caption: the full date, then the age when known. */
 fun detail(taken:Long,birth:Long?,locale:Locale=Locale.getDefault()):String? {
  val date=fullDate(taken,locale) ?: return null
  return listOfNotNull(date,age(taken,birth)).joinToString("  ·  ")
 }
 /** The day's extra photo when nothing is favourited yet: stable for a day, never one already shown. */
 fun extra(available:List<String>,shown:List<String?>,day:Long):String? {
  val rest=available.distinct().filter{it !in shown}.sorted()
  return if(rest.isEmpty())null else rest[Math.floorMod(day,rest.size.toLong()).toInt()]
 }
}
