package org.ambienttv.home

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

/** Deliberate maintenance actions, away from the photo-first home screen. */
class MaintenanceActivity:Activity(){
 private val worker=Executors.newSingleThreadExecutor()
 private lateinit var status:TextView
 private var busy=false
 override fun onCreate(state:Bundle?){
  super.onCreate(state)
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(48,28,48,28);setBackgroundColor(Color.rgb(8,22,38))}
  fun label(value:String,size:Float)=TextView(this).apply{text=value;textSize=size;setTextColor(Color.WHITE);setPadding(0,8,0,8)}
  root.addView(label("Connection status & preferences",28f));status=label("",19f);root.addView(status)
  fun button(label:String,action:()->Unit){root.addView(Button(this).apply{text=label;textSize=20f;minHeight=60;setOnClickListener{action()}})}
  button("Refresh Continue Watching now"){refresh()}
  root.addView(Switch(this).apply{text="Compact sidebar · show labels when focused";textSize=20f;setTextColor(Color.WHITE);setPadding(0,12,0,12);isChecked=getSharedPreferences("home-ui",0).getBoolean("compactSidebar",false);setOnCheckedChangeListener{_,checked->getSharedPreferences("home-ui",0).edit().putBoolean("compactSidebar",checked).apply()}})
  button("Default photo framing"){PhotoFraming.choose(this){showStatus()}}
  button("Export preferences"){document(Intent.ACTION_CREATE_DOCUMENT,1)}
  button("Restore preferences"){document(Intent.ACTION_OPEN_DOCUMENT,2)}
  root.addView(label("Backup includes screensaver layout, photo framing and favourite IDs. It excludes accounts, passwords, server addresses, photos and watch history. Reconnect services separately on another TV.",17f))
  button("Back"){finish()}
  setContentView(ScrollView(this).apply{addView(root)});root.getChildAt(2).requestFocus();showStatus()
 }
 private fun time(value:Long)=if(value<=0)"Not checked yet"else DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(value))
 private fun showStatus(){
  val p=getSharedPreferences("feed-audit",0);val plex=getSharedPreferences("plex-status",0)
  status.text="Plex: ${PlexFeed.status(this)}\nLast successful Plex refresh: ${time(plex.getLong("successAt",0))}\n\nYouTube / Android TV feed: ${if(p.getBoolean("querySucceeded",false))"Readable"else "Unavailable or not yet checked"}\nLast feed check: ${time(p.getLong("providerReadAt",0))}\nLatest published YouTube activity: ${time(p.getLong("youtubeNewest",0))}\n${p.getInt("youtubeRows",0)} YouTube records published. This is the TV app’s feed, not direct access to YouTube History. A successful refresh cannot recover records the app has not published."
 }
 private fun refresh(){if(busy)return;busy=true;status.text="Checking sources…"
  worker.execute{try{WatchNextReader.read(applicationContext,true)}finally{runOnUiThread{busy=false;if(!isFinishing && !isDestroyed)showStatus()}}}
 }
 private fun document(action:String,request:Int){
  val intent=Intent(action).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/json";if(request==1)putExtra(Intent.EXTRA_TITLE,"ambient-tv-preferences.json")}
  val resolved=intent.resolveActivity(packageManager)
  if(resolved==null || resolved.packageName.contains("frameworkpackagestubs")){localBackup(request);return}
  try{startActivityForResult(intent,request)}catch(_:Exception){localBackup(request)}
 }
 private fun localBackup(request:Int){
  val file=java.io.File(getExternalFilesDir(null) ?: filesDir,"ambient-tv-preferences.json")
  if(request==2 && !file.isFile){notice("No local preferences backup found. Export one first.");return}
  AlertDialog.Builder(this).setTitle(if(request==1)"Save a local backup?"else "Read local backup?")
   .setMessage("This TV has no usable document picker. Use the local file at ${file.absolutePath}? It survives app updates, but uninstalling or clearing app data deletes it. Copy it elsewhere for recovery."+(if(request==1 && file.exists())" This replaces the previous local backup."else ""))
   .setPositiveButton("Continue"){_,_->onActivityResult(request,RESULT_OK,Intent().setData(android.net.Uri.fromFile(file)))}.setNegativeButton("Cancel",null).show()
 }
 @Deprecated("Android Activity callback")
 override fun onActivityResult(request:Int,result:Int,data:Intent?){super.onActivityResult(request,result,data);if(result!=RESULT_OK)return;val uri=data?.data ?: return
  if(request==1){val encoded=BackupPolicy.encode(PreferenceBackup.snapshot(this));worker.execute{try{contentResolver.openOutputStream(uri,"wt")?.use{it.write(encoded.toByteArray(Charsets.UTF_8))} ?: error("No output");notice("Preferences exported") }catch(_:Exception){notice("Could not write the backup")}}}
  else if(request==2)worker.execute{try{
   val bytes=contentResolver.openInputStream(uri)?.use{input->val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(4096);while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=BackupPolicy.MAX_BYTES);out.write(buffer,0,n)};out.toByteArray()} ?: error("No input")
   val snapshot=BackupPolicy.decode(String(bytes,Charsets.UTF_8));runOnUiThread{if(!isFinishing && !isDestroyed)AlertDialog.Builder(this).setTitle("Restore preferences?").setMessage("Replace screensaver layout, ${snapshot.favourites.size} favourites and ${snapshot.overrides.size} photo framing overrides? Sign-ins and watch history stay unchanged.").setPositiveButton("Restore"){_,_->try{PreferenceBackup.restore(this,snapshot);notice("Preferences restored")}catch(_:Exception){notice("Restore failed; previous preferences were retained where possible")}}.setNegativeButton("Cancel",null).show()}
  }catch(_:Exception){notice("Invalid or unsupported backup. No preferences changed.")}}
 }
 private fun notice(message:String){runOnUiThread{if(!isFinishing && !isDestroyed)Toast.makeText(this,message,Toast.LENGTH_LONG).show()}}
 override fun onDestroy(){worker.shutdownNow();super.onDestroy()}
}
