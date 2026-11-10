package org.ambienttv.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View

/**
 * Sits between the page and the expanded compact sidebar: solid navy behind the labels,
 * clear by its right edge, so page text never shows through the navigation.
 * Only its alpha animates, which needs no layout pass and no off-screen buffer.
 */
internal class SidebarScrim(c:Context):View(c) {
 private val paint=Paint()
 override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) {
  super.onSizeChanged(w,h,oldw,oldh)
  paint.shader=LinearGradient(0f,0f,w.toFloat(),0f,
   intArrayOf(Color.rgb(3,13,25),Color.rgb(3,13,25),Color.argb(205,3,13,25),Color.argb(0,3,13,25)),
   floatArrayOf(0f,.48f,.7f,1f),Shader.TileMode.CLAMP)
 }
 override fun onDraw(canvas:Canvas){canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),paint)}
 override fun hasOverlappingRendering()=false
}
