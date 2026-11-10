package org.ambienttv.home

import java.util.Calendar
import java.util.TimeZone

/** Optional evening dimming of Home and the screensaver, by the TV's own clock. Off unless the owner enables it. */
internal object DimPolicy {
 const val START_HOUR=21;const val END_HOUR=7
 /** Black overlay strength, 0-255. Enough to calm a bright panel in a dark room while photos stay readable. */
 const val ALPHA=96
 fun active(now:Long,enabled:Boolean,zone:TimeZone=TimeZone.getDefault()):Boolean {
  if(!enabled)return false
  val hour=Calendar.getInstance(zone).apply{timeInMillis=now}.get(Calendar.HOUR_OF_DAY)
  return hour>=START_HOUR || hour<END_HOUR
 }
}
