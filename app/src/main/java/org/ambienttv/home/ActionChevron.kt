package org.ambienttv.home

import android.graphics.*
import android.graphics.drawable.Drawable

/** Decorative action affordance; the enclosing tile remains the single remote target. */
internal class ActionChevron:Drawable(){
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE;style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
 override fun draw(canvas:Canvas){val b=bounds;paint.strokeWidth=b.width()*.12f;val path=Path().apply{moveTo(b.left+b.width()*.35f,b.top+b.height()*.2f);lineTo(b.left+b.width()*.7f,b.exactCenterY());lineTo(b.left+b.width()*.35f,b.top+b.height()*.8f)};canvas.drawPath(path,paint)}
 override fun setTint(color:Int){paint.color=color;invalidateSelf()}
 override fun setAlpha(alpha:Int){paint.alpha=alpha;invalidateSelf()}
 override fun setColorFilter(filter:ColorFilter?){paint.colorFilter=filter;invalidateSelf()}
 @Deprecated("Deprecated in Android") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
