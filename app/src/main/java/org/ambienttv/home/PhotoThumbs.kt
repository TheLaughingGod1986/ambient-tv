package org.ambienttv.home

import android.graphics.Bitmap
import android.os.SystemClock
import java.io.File

/** A decoded picture, its tiny soft fill, and the face found in it (Home photos only). */
internal class ThumbEntry(val photo:Bitmap,val fill:Bitmap?,val face:FaceRegion?=null)

/**
 * Decoded pictures kept across page changes, so returning to a page does not decode the
 * same images again. Bounded by bytes and dropped under memory pressure. Entries are
 * shared: no view recycles them, the garbage collector reclaims them once they are evicted
 * and no longer shown.
 */
internal open class ThumbStore(limit:Int,ttl:Long) {
 private val cache=BoundedTtlCache<String,ThumbEntry>(limit,ttl){it.photo.byteCount+(it.fill?.byteCount ?: 0)}
 fun get(key:String):ThumbEntry? = cache.get(key,SystemClock.elapsedRealtime())
 fun put(key:String,entry:ThumbEntry){cache.put(key,entry,SystemClock.elapsedRealtime())}
 fun clear(){cache.clear()}
}

/** Home and gallery thumbnails of cached photo files. A file never changes under its key, so entries do not expire. */
internal object PhotoThumbs:ThumbStore(16*1024*1024,Long.MAX_VALUE) {
 /** File identity plus the decode bounds: a refreshed or differently sized photo never matches. */
 fun key(file:File,maxWidth:Int,maxHeight:Int)="${file.path}:${file.length()}:${file.lastModified()}:$maxWidth:$maxHeight"
}

/** Continue Watching stills and Plex posters. Short-lived, because the picture behind a link can change. */
internal object ArtThumbs:ThumbStore(8*1024*1024,600000)
