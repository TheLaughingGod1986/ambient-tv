package org.ambienttv.home
import android.content.Context
import android.graphics.*
import android.view.View
internal class HomeWeatherView(c:Context):View(c) {
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
 private var panel=WeatherStore.panel(c)
 fun update(value:WeatherPanel){if(panel!=value){panel=value;contentDescription="${AppPreferences.weatherLabel(context)}, ${value.temperature}, ${value.condition}"+(value.range?.let{", $it"} ?: "");invalidate()}}
 override fun onDraw(canvas:Canvas){
  super.onDraw(canvas);canvas.save();val unit=height/110f;canvas.scale(unit,unit)
  if(!AppPreferences.weatherEnabled(context)){
   paint.color=Color.WHITE;paint.typeface=Typeface.DEFAULT;paint.textSize=18f;canvas.drawText("AMBIENT TV",0f,24f,paint)
   paint.textSize=23f;canvas.drawText("Your photos. Your apps.",0f,73f,paint);canvas.restore();return
  }
  // Place, temperature and conditions sit high in the view so the small credit clears the page heading below.
  paint.color=Color.WHITE;paint.typeface=Typeface.DEFAULT;paint.textSize=18f;canvas.drawText(AppPreferences.weatherLabel(context).uppercase(),0f,20f,paint)
  paint.textSize=40f;canvas.drawText(panel.temperature,0f,64f,paint)
  WeatherRenderer.drawConditionIcon(canvas,panel,155f,50f,.45f,paint)
  val range=panel.range
  paint.color=Color.WHITE;paint.textSize=23f
  var label=panel.condition.take(100);while(label.isNotEmpty() && paint.measureText(label+"…")>385f)label=label.dropLast(1)
  canvas.drawText(if(label==panel.condition)label else "$label…",210f,if(range==null)58f else 46f,paint)
  if(range!=null){paint.color=Color.rgb(204,216,228);paint.textSize=17f;canvas.drawText(range,210f,70f,paint)}
  paint.color=Color.LTGRAY;paint.textSize=11f;canvas.drawText("open-meteo.com · CC BY 4.0",0f,86f,paint);canvas.restore()
 }
}
