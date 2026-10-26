package org.ambienttv.home
import android.content.Context
import android.graphics.*
import android.view.View
internal class HomeWeatherView(c:Context):View(c) {
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
 private var panel=WeatherStore.panel(c)
 fun update(value:WeatherPanel){if(panel!=value){panel=value;contentDescription="${AppPreferences.weatherLabel(context)}, ${value.temperature}, ${value.condition}";invalidate()}}
 override fun onDraw(canvas:Canvas){
  super.onDraw(canvas);canvas.save();canvas.scale(width/600f,height/110f)
  if(!AppPreferences.weatherEnabled(context)){
   paint.color=Color.WHITE;paint.typeface=Typeface.DEFAULT;paint.textSize=18f;canvas.drawText("AMBIENT TV",0f,24f,paint)
   paint.textSize=23f;canvas.drawText("Your photos. Your apps.",0f,73f,paint);canvas.restore();return
  }
  paint.color=Color.WHITE;paint.typeface=Typeface.DEFAULT;paint.textSize=18f;canvas.drawText(AppPreferences.weatherLabel(context).uppercase(),0f,24f,paint)
  paint.textSize=42f;canvas.drawText(panel.temperature,0f,78f,paint)
  WeatherRenderer.drawConditionIcon(canvas,panel,155f,63f,.45f,paint)
  paint.color=Color.WHITE;paint.textSize=23f
  var label=panel.condition.take(100);while(label.isNotEmpty() && paint.measureText(label+"…")>385f)label=label.dropLast(1)
  canvas.drawText(if(label==panel.condition)label else "$label…",210f,73f,paint)
  paint.color=Color.LTGRAY;paint.textSize=12f;canvas.drawText("open-meteo.com · CC BY 4.0",0f,104f,paint);canvas.restore()
 }
}
