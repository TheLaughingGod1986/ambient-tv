package org.ambienttv.home
import java.util.Calendar
import java.util.TimeZone

/** The NAS cron timezone is explicit, independent of the London date used for photo rotation. */
object PhotoSyncPolicy {
 private fun calendar(now:Long,zone:TimeZone)=Calendar.getInstance(zone).apply{timeInMillis=now}
 fun open(now:Long,zone:TimeZone=TimeZone.getTimeZone("UTC")):Boolean {val c=calendar(now,zone);return c.get(Calendar.HOUR_OF_DAY)*60+c.get(Calendar.MINUTE) in 75 until 345}
 fun next(now:Long,success:Long,retry:Boolean=false,zone:TimeZone=TimeZone.getTimeZone("UTC")):Long {
  val c=calendar(now,zone).apply{set(Calendar.HOUR_OF_DAY,1);set(Calendar.MINUTE,15);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}
  val start=c.timeInMillis
  if(now<start)return start
  if(open(now,zone) && success<start){val next=now+if(retry)15*60000L else 1000L;if(open(next,zone))return next}
  c.add(Calendar.DATE,1);return c.timeInMillis
 }
}

object RecentPhotoHistory {
 fun read(text:String):Map<Long,List<String>> = text.split(';').mapNotNull{row->val pair=row.split('=',limit=2);val day=pair.firstOrNull()?.toLongOrNull();if(day==null || pair.size!=2)null else day to pair[1].split(',').filter{it.matches(Regex("[A-Za-z0-9._-]{1,80}"))}.take(4)}.takeLast(8).toMap()
 fun remember(rows:Map<Long,List<String>>,day:Long,ids:List<String?>):Map<Long,List<String>> = (rows+(day to ids.filterNotNull().filter{it.isNotBlank()}.distinct().take(4))).filterKeys{it in day-7..day}.toSortedMap()
 fun encode(rows:Map<Long,List<String>>)=rows.entries.joinToString(";"){(day,ids)->"$day="+ids.joinToString(",")}
}
