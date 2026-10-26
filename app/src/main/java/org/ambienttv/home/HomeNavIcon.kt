package org.ambienttv.home
import android.graphics.*
import android.graphics.drawable.Drawable
internal class HomeNavIcon(private val name:String):Drawable(){
 private val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{strokeWidth=1.7f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
 override fun draw(c:Canvas){c.save();c.translate(bounds.left.toFloat(),bounds.top.toFloat());c.scale(bounds.width()/24f,bounds.height()/24f);p.style=Paint.Style.STROKE
  fun path(vararg xy:Float){val q=Path();q.moveTo(xy[0],xy[1]);for(i in 2 until xy.size step 2)q.lineTo(xy[i],xy[i+1]);c.drawPath(q,p)}
  when(name){
   "Home"->{p.style=Paint.Style.FILL;val q=Path();q.moveTo(2f,11f);q.lineTo(12f,2f);q.lineTo(22f,11f);q.lineTo(19f,11f);q.lineTo(19f,21f);q.lineTo(14f,21f);q.lineTo(14f,14f);q.lineTo(10f,14f);q.lineTo(10f,21f);q.lineTo(5f,21f);q.lineTo(5f,11f);q.close();c.drawPath(q,p)}
   "TV"->{c.drawRect(2f,4f,22f,17f,p);path(8f,21f,16f,21f);path(12f,17f,12f,21f)}
   "Movies"->{c.drawRect(3f,3f,21f,21f,p);path(8f,3f,8f,21f);path(16f,3f,16f,21f);for(y in listOf(8f,16f)){path(3f,y,8f,y);path(16f,y,21f,y)}}
   "Apps"->for(x in listOf(3f,14f))for(y in listOf(3f,14f))c.drawRect(x,y,x+7,y+7,p)
   "Photos"->{c.drawRoundRect(2f,3f,22f,21f,1f,1f,p);c.drawCircle(16f,8f,1.4f,p);path(3f,18f,9f,10f,14f,16f,17f,13f,21f,18f)}
   "Recent","All Photos"->{c.drawRect(7f,3f,21f,17f,p);path(3f,7f,3f,21f,17f,21f);path(9f,14f,13f,9f,19f,14f)}
   "Favourites"->{val q=Path();for(i in 0..9){val angle=-Math.PI/2+i*Math.PI/5;val radius=if(i%2==0)10f else 4.8f;val x=12+radius*kotlin.math.cos(angle).toFloat();val y=12+radius*kotlin.math.sin(angle).toFloat();if(i==0)q.moveTo(x,y)else q.lineTo(x,y)};q.close();c.drawPath(q,p)}
   "Library"->{path(5f,22f,5f,3f,19f,3f,19f,22f,12f,17f,5f,22f)}
   "Search"->{c.drawCircle(10f,10f,7f,p);path(15f,15f,22f,22f)}
   else->{c.drawCircle(12f,12f,7f,p);c.drawCircle(12f,12f,2.8f,p);for(i in 0..7){val a=i*Math.PI/4;path((12+7*kotlin.math.cos(a)).toFloat(),(12+7*kotlin.math.sin(a)).toFloat(),(12+10*kotlin.math.cos(a)).toFloat(),(12+10*kotlin.math.sin(a)).toFloat())}}
  };c.restore()
 }
 override fun setAlpha(a:Int){p.alpha=a};override fun setColorFilter(f:ColorFilter?){p.colorFilter=f};override fun getOpacity()=PixelFormat.TRANSLUCENT
 override fun setTint(color:Int){p.color=color;invalidateSelf()}
}
