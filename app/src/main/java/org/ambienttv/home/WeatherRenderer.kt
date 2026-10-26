package org.ambienttv.home

import android.content.Context
import android.graphics.*
import java.io.File
import java.security.MessageDigest
import kotlin.math.min

object WeatherRenderer {
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
 private val normalTypeface=Typeface.create("sans-serif",Typeface.NORMAL)
 private val boldTypeface=Typeface.create("sans-serif",Typeface.BOLD)
 private val bolt=Path().apply {
  val x=WeatherLayout.ICON_X;val y=WeatherLayout.ICON_Y
  moveTo(x+2,y+28);lineTo(x-12,y+53);lineTo(x+3,y+53);lineTo(x-3,y+73);lineTo(x+24,y+43);lineTo(x+9,y+43);close()
 }
 @Synchronized fun compose(c: Context, source: File): File {
  val sources=TriptychPolicy.group(Photos.currentFiles(c),source).ifEmpty { listOf(source) }
  return composeSources(c,sources,WeatherStore.panel(c))
 }
 /** Shared renderer for synthetic device verification; callers pass only their own files. */
 @Synchronized internal fun composeSources(c: Context,sources: List<File>,panel: WeatherPanel,fullHeight:Boolean=false): File {
  require(sources.size in 1..3)
  val key=MessageDigest.getInstance("SHA-256").digest(("triptych-v6|dream=$fullHeight|style=${if(fullHeight)2 else 1}|${sources.joinToString { it.name }}|${if(fullHeight) "" else panel}").toByteArray()).take(16).joinToString("") { "%02x".format(it) }
  val directory=File(c.cacheDir,"weather-frames").apply { mkdirs() }
  val output=File(directory,"$key.jpg")
  if(output.isFile && output.length()>0) { output.setLastModified(System.currentTimeMillis()); return output }
  var frame: Bitmap?=null
  try {
   frame=Bitmap.createBitmap(WeatherLayout.WIDTH,WeatherLayout.HEIGHT,Bitmap.Config.ARGB_8888)
   val canvas=Canvas(frame)
   canvas.drawColor(Color.rgb(13,19,25))
   val gap=16f; val margin=48f; val top=if(fullHeight)DreamPolicy.TOP.toFloat() else WeatherLayout.PHOTO_TOP; val bottom=if(fullHeight)DreamPolicy.BOTTOM.toFloat() else WeatherLayout.PHOTO_BOTTOM
   val cellWidth=(1920f-2*margin-gap*(sources.size-1))/sources.size
   // Decode and recycle one source at a time, keeping peak pixel memory bounded.
   var rendered=0
   sources.forEachIndexed { index,file ->
    val left=margin+index*(cellWidth+gap)
    val cell=RectF(left,top,left+cellWidth,bottom)
    paint.color=Color.rgb(21,29,37); paint.style=Paint.Style.FILL
    canvas.drawRoundRect(cell,10f,10f,paint)
    var photo: Bitmap?=null
    try {
     val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
     BitmapFactory.decodeFile(file.path,bounds)
     val sample=BitmapPolicy.sampleSize(bounds.outWidth,bounds.outHeight)
     photo=BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply { inSampleSize=sample })
     if(photo!=null) {
      if(fullHeight) {
       paint.color=MutedPalette.muted(sampleColor(photo),8)
       canvas.drawRoundRect(cell,10f,10f,paint)
      }
      val fitted=ContainGeometry.fit(photo.width,photo.height,cell.width().toDouble(),cell.height().toDouble())
      val width=fitted.width.toFloat(); val height=fitted.height.toFloat()
      canvas.drawBitmap(photo,null,RectF(cell.centerX()-width/2,cell.centerY()-height/2,cell.centerX()+width/2,cell.centerY()+height/2),paint)
      rendered++
     }
    } catch(_: Exception) { /* An unreadable tile must not hide the other photographs or weather. */ }
    finally { photo?.recycle() }
   }
   if(fullHeight) require(rendered>0)
   if(!fullHeight && AppPreferences.weatherEnabled(c)) drawPanel(canvas,panel,location=AppPreferences.weatherLabel(c))
   val temporary=File(directory,"$key.tmp")
   try {
    temporary.outputStream().use { require(frame.compress(Bitmap.CompressFormat.JPEG,90,it)) }
    require(temporary.renameTo(output))
   } finally { temporary.delete() }
   var retained=0L
   directory.listFiles()?.filter { it.extension=="jpg" }?.sortedByDescending { it.lastModified() }?.forEachIndexed { index,file ->
    retained+=file.length()
    if(index>=24 || retained>64L*1024*1024) { if(file!=output) file.delete() }
   }
   return output
  } finally { frame?.recycle() }
 }
 internal fun sampleColor(bitmap:Bitmap):Int {
  val colors=IntArray(25)
  for(y in 0..4) for(x in 0..4) colors[y*5+x]=bitmap.getPixel(((x+.5f)*bitmap.width/5).toInt().coerceAtMost(bitmap.width-1),((y+.5f)*bitmap.height/5).toInt().coerceAtMost(bitmap.height-1))
  return MutedPalette.average(colors)
 }
 /** Also used by the synthetic-frame instrumentation check. No image assets or icon fonts. */
 fun drawPanel(canvas: Canvas, panel: WeatherPanel,paint:Paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),location:String="Weather") {
  paint.color=Color.argb(224,10,22,32); paint.style=Paint.Style.FILL
  paint.typeface=normalTypeface; paint.color=Color.WHITE
  fun text(value: String,size: Float,y: Float,bold: Boolean=false,x: Float=WeatherLayout.TEXT_X) {
   paint.textSize=size; paint.typeface=if(bold) boldTypeface else normalTypeface
   canvas.drawText(value,x,y,paint)
  }
  text(location.uppercase(),WeatherLayout.LOCATION_SIZE,WeatherLayout.LOCATION_Y)
  text(panel.temperature,WeatherLayout.TEMP_SIZE,WeatherLayout.TEMP_Y,true,WeatherLayout.TEMP_X)
  text(panel.condition,WeatherLayout.CONDITION_SIZE,WeatherLayout.CONDITION_Y,x=WeatherLayout.CONDITION_X)
  paint.color=Color.rgb(183,205,216); text(WeatherLayout.CREDIT,WeatherLayout.CREDIT_SIZE,WeatherLayout.CREDIT_Y)
  canvas.save()
  try { canvas.scale(.4f,.4f,WeatherLayout.ICON_X,WeatherLayout.ICON_Y); icon(canvas,panel,paint) } finally { canvas.restore() }
 }
 fun drawConditionIcon(canvas:Canvas,panel:WeatherPanel,x:Float,y:Float,scale:Float,paint:Paint) {
  canvas.save();canvas.translate(x-WeatherLayout.ICON_X,y-WeatherLayout.ICON_Y);canvas.scale(scale,scale,WeatherLayout.ICON_X,WeatherLayout.ICON_Y);icon(canvas,panel,paint);canvas.restore()
 }
 private fun icon(canvas: Canvas,panel: WeatherPanel,paint:Paint) {
  val x=WeatherLayout.ICON_X; val y=WeatherLayout.ICON_Y
  paint.style=Paint.Style.FILL; paint.color=Color.rgb(225,237,245)
  if(panel.code==null) { paint.textSize=58f; canvas.drawText("—",x-22,y+15,paint); return }
  if(panel.code in 0..2) {
   paint.color=if(panel.isDay) Color.rgb(255,215,115) else Color.rgb(210,227,248)
   canvas.drawCircle(x,y-12,27f,paint)
   if(!panel.isDay) { paint.color=Color.rgb(10,22,32); canvas.drawCircle(x+14,y-23,25f,paint) }
  }
  if(panel.code!=0) {
   paint.color=Color.rgb(225,237,245)
   canvas.drawRoundRect(x-48,y,x+49,y+32,15f,15f,paint)
   canvas.drawCircle(x-22,y+1,25f,paint); canvas.drawCircle(x+10,y-8,31f,paint); canvas.drawCircle(x+31,y+6,23f,paint)
  }
  if(panel.code in 51..86) {
   paint.color=Color.rgb(141,214,250); paint.strokeWidth=6f; paint.strokeCap=Paint.Cap.ROUND
   if(panel.code in 71..77 || panel.code in 85..86) { for(i in -1..1) canvas.drawCircle(x+i*24f,y+49,4f,paint) }
   else { for(i in -1..1) canvas.drawLine(x+i*24f,y+43,x+i*24f-6,y+56,paint) }
  }
  if(panel.code in 95..99 && panel.code!=98) {
   paint.color=Color.rgb(255,215,115)
   canvas.drawPath(bolt,paint)
  }
 }
 @Synchronized fun clear(c: Context) { File(c.cacheDir,"weather-frames").listFiles()?.forEach { it.delete() } }
}
