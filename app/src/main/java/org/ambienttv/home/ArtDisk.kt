package org.ambienttv.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.security.MessageDigest

/**
 * Continue Watching stills kept in the app's private cache folder, so Home can draw the row before the network
 * is back after standby. Small JPEG copies only, a bounded number of them, named by a hash of their source.
 * Android may empty the folder at any time; nothing else depends on it.
 */
internal object ArtDisk {
 const val MAX_FILES=24
 /** A kept still younger than this is shown without asking the network again. */
 const val FRESH_MS=24*60*60*1000L
 /** Older than this it is not shown at all. */
 const val KEEP_MS=30L*24*60*60*1000L
 class Kept(val bitmap:Bitmap,val fresh:Boolean)
 private fun dir(c:Context)=File(c.cacheDir,"row-art")
 internal fun fileName(key:String)=MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8)).take(16).joinToString(""){"%02x".format(it.toInt() and 0xff)}+".jpg"
 /** Names to delete so that only the [max] most recently written stills remain. */
 internal fun surplus(files:List<Pair<String,Long>>,max:Int=MAX_FILES)=files.sortedByDescending{it.second}.drop(max).map{it.first}
 internal fun usable(age:Long)=age in 0 until KEEP_MS
 internal fun fresh(age:Long)=age in 0 until FRESH_MS
 fun read(c:Context,key:String,now:Long=System.currentTimeMillis()):Kept? = try {
  val file=File(dir(c),fileName(key));val age=now-file.lastModified()
  if(key.isBlank() || !file.isFile || !usable(age))null else BitmapFactory.decodeFile(file.path)?.let{Kept(it,fresh(age))}
 }catch(_:Exception){null}
 @Synchronized fun write(c:Context,key:String,bitmap:Bitmap) {
  if(key.isBlank())return
  try {
   val folder=dir(c);folder.mkdirs()
   val target=File(folder,fileName(key));val temp=File(folder,target.name+".tmp")
   val written=temp.outputStream().use{bitmap.compress(Bitmap.CompressFormat.JPEG,85,it)}
   if(!written || !temp.renameTo(target))temp.delete()
   val files=folder.listFiles() ?: return
   val extra=surplus(files.map{it.name to it.lastModified()}).toSet()
   files.filter{it.name in extra}.forEach{it.delete()}
  }catch(_:Exception){}
 }
 @Synchronized fun clear(c:Context){try{dir(c).listFiles()?.forEach{it.delete()}}catch(_:Exception){}}
}
