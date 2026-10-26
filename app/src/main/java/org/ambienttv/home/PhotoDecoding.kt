package org.ambienttv.home
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
object PhotoDecoding {
 fun load(file:File,maxWidth:Int=1920,maxHeight:Int=1080):Bitmap? {
  val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true};BitmapFactory.decodeFile(file.path,bounds)
  val sample=BitmapPolicy.sampleSize(bounds.outWidth,bounds.outHeight,maxWidth,maxHeight)
  return BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply {inSampleSize=sample})
 }
}
