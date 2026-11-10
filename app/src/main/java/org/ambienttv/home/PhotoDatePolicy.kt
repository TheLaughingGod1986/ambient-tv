package org.ambienttv.home

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * Optional lower bound on the capture date of photos taken from the selected person.
 * Face matching on a NAS can file older pictures of other people under the same name;
 * a date such as a child's birthday keeps those out without changing anything on the NAS.
 */
internal object PhotoDatePolicy {
 private val shape=Regex("""(\d{4})-(\d{2})-(\d{2})""")
 /** Seconds at the start of that day, or null when [text] is blank. Rejects anything but a real YYYY-MM-DD date. */
 fun parse(text:String):Long? {
  val value=text.trim()
  if(value.isEmpty())return null
  val match=requireNotNull(shape.matchEntire(value)){"Use YYYY-MM-DD"}
  val (year,month,day)=match.destructured.toList().map{it.toInt()}
  require(year in 1900..2200)
  val calendar=GregorianCalendar(TimeZone.getTimeZone("UTC")).apply{isLenient=false;clear();set(year,month-1,day)}
  return try{calendar.timeInMillis/1000}catch(_:IllegalArgumentException){throw IllegalArgumentException("Not a calendar date")}
 }
 fun keep(takenAt:Long,earliest:Long?)=earliest==null || takenAt>=earliest
}
