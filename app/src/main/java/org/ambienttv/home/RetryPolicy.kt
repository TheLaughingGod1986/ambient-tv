package org.ambienttv.home

/**
 * Waits between tries after a failure. A TV shows Home several seconds before its network is back after
 * standby, so the first tries come quickly; once they are used up the caller falls back to its slow schedule.
 */
internal object RetryPolicy {
 private val STEPS=longArrayOf(4_000L,10_000L,25_000L,60_000L,120_000L)
 /** How many quick tries follow a first failure. */
 val QUICK=STEPS.size
 /** The wait before the next try after [failures] failures in a row, or null once the quick tries are used up. */
 fun delay(failures:Int):Long? = if(failures in 1..STEPS.size)STEPS[failures-1] else null
}

/**
 * Tells a short outage from a lasting one. Failures that follow each other closely are one outage; a long gap
 * between them (Home was left, or the TV slept) starts a new one, so the first failures after standby never
 * count as a long outage.
 */
internal class OutageGrace(private val grace:Long,private val gap:Long) {
 private var since=0L
 private var last=0L
 /** Records a failure at [now] and returns true once this outage has lasted for the whole grace period. */
 @Synchronized fun failed(now:Long):Boolean {
  if(since==0L || now<last || now-last>gap)since=now
  last=now
  return now-since>=grace
 }
 @Synchronized fun succeeded(){since=0L;last=0L}
}
