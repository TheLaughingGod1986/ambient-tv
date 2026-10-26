package org.ambienttv.home

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

class PhotoProvider : ContentProvider() {
 override fun onCreate() = true
 override fun getType(uri: Uri): String { checkReader(context!!); return "image/jpeg" }
 override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
  checkReader(context!!)
  require(mode == "r" && uri.pathSegments.size == 1)
  val name = uri.lastPathSegment!!
  require(ImagePolicy.valid(name))
  val c=context!!
  WeatherStore.refresh(c)
  val sourceName=ImagePolicy.resolve(name,(Photos.files(c)+Photos.currentFiles(c)).map { it.name }.toSet(),Photos.currentFiles(c).map { it.name })
  val source=Photos.currentFiles(c).firstOrNull{it.name==sourceName} ?: File(Photos.dir(c),sourceName)
  val frame=try { WeatherRenderer.compose(c,source) } catch(_: Exception) { source }
  val result = ParcelFileDescriptor.open(frame,ParcelFileDescriptor.MODE_READ_ONLY)
  val state = context!!.getSharedPreferences("state",0)
  val groupToken=java.security.MessageDigest.getInstance("SHA-256").digest(sourceName.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
  state.edit().putString("lastGroupToken",groupToken).putInt("lastRenderVersion",6).putLong("lastImageRead",System.currentTimeMillis()).putBoolean("weatherFrameServed",frame!=source).putInt("imageReadCount",state.getInt("imageReadCount",0)+1).apply()
  return result
 }
 override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? { checkReader(context!!); return null }
 override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
 override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = throw UnsupportedOperationException()
 override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = throw UnsupportedOperationException()
}
