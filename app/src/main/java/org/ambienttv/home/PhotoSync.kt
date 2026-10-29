package org.ambienttv.home
import android.app.job.*
import android.content.*
import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.*

object PhotoSync {
 const val JOB_ID=2401
 fun zone(c:Context)=TimeZone.getTimeZone(c.getSharedPreferences("photo-sync",0).getString("zone",TimeZone.getDefault().id))
 fun setZone(c:Context,id:String){require(id in TimeZone.getAvailableIDs());c.getSharedPreferences("photo-sync",0).edit().putString("zone",id).apply();schedule(c,true)}
 fun enabled(c:Context)=c.getSharedPreferences("photo-sync",0).getBoolean("overnight",false)
 fun setEnabled(c:Context,value:Boolean){c.getSharedPreferences("photo-sync",0).edit().putBoolean("overnight",value).apply();schedule(c,true)}
 fun schedule(c:Context,replace:Boolean=false,retry:Boolean=false){
  val jobs=c.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
  if(!enabled(c)){jobs.cancel(JOB_ID);return}
  if(!replace && jobs.allPendingJobs.any{it.id==JOB_ID})return
  if(Vault(c).read()==null){jobs.cancel(JOB_ID);return}
  val now=System.currentTimeMillis();val next=PhotoSyncPolicy.next(now,c.getSharedPreferences("state",0).getLong("success",0),retry,zone(c))
  jobs.schedule(job(c,now,next))
 }
 internal fun job(c:Context,now:Long,next:Long)=JobInfo.Builder(JOB_ID,ComponentName(c,PhotoSyncJob::class.java)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency((next-now).coerceAtLeast(1000)).setPersisted(true).build()
 fun summary(c:Context):String {
  val success=c.getSharedPreferences("state",0).getLong("success",0)
  val date=if(success<=0)"Never"else SimpleDateFormat("EEE, d MMM HH:mm",Locale.getDefault()).apply{timeZone=TimeZone.getTimeZone("Europe/London")}.format(Date(success))
  return "Last synced: $date\n${Photos.currentFiles(c).size} cached photos"+if(enabled(c))"\nNAS available overnight · 01:00–06:00 (${zone(c).id})\nAutomatic sync from 01:15 NAS time, when Android permits"else ""
 }
 fun waiting(c:Context)="Using ${Photos.currentFiles(c).size} cached photos · NAS available overnight (01:00–06:00 NAS time)."
}

class PhotoSyncJob:JobService(){
 companion object {@Volatile internal var completions=0;private set}
 private var active:JobParameters?=null
 override fun onStartJob(params:JobParameters):Boolean {
  active=params
  val now=System.currentTimeMillis()
  val next=PhotoSyncPolicy.next(now,getSharedPreferences("state",0).getLong("success",0),zone=PhotoSync.zone(this))
  if(!PhotoSync.enabled(this) || next>now+60000){active=null;completions++;Handler(Looper.getMainLooper()).post{PhotoSync.schedule(applicationContext,true)};return false}
  Photos.refresh(applicationContext,true){Handler(Looper.getMainLooper()).post{if(active===params){active=null;completions++;jobFinished(params,false);PhotoSync.schedule(applicationContext,true,true)}}}
  return true
 }
 override fun onStopJob(params:JobParameters):Boolean {if(active===params)active=null;return true}
}
