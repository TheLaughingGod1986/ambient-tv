package org.ambienttv.home
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
object PhotoDecoding {
 fun load(file:File,maxWidth:Int=1920,maxHeight:Int=1080):Bitmap? {
  val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true};BitmapFactory.decodeFile(file.path,bounds)
  val sample=BitmapPolicy.sampleSize(bounds.outWidth,bounds.outHeight,maxWidth,maxHeight)
  val decoded=BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply {inSampleSize=sample}) ?: return null
  val orientation=try{android.media.ExifInterface(file.path).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1)}catch(_:Exception){1}
  val matrix=android.graphics.Matrix()
  when(orientation){2->matrix.setScale(-1f,1f);3->matrix.setRotate(180f);4->matrix.setScale(1f,-1f)
   5->{matrix.setRotate(90f);matrix.postScale(-1f,1f)};6->matrix.setRotate(90f)
   7->{matrix.setRotate(270f);matrix.postScale(-1f,1f)};8->matrix.setRotate(270f);else->return decoded}
  return Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true).also{if(it!==decoded)decoded.recycle()}
 }
}
