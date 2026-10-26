package org.ambienttv.home
import android.app.Activity
import android.os.Bundle
import android.widget.*
import android.graphics.Color
import android.view.KeyEvent
import java.util.concurrent.Executors

/** Cached-photo viewer; navigation never downloads originals or modifies the NAS. */
class PhotoViewerActivity:Activity() {
 private var bitmap:android.graphics.Bitmap?=null
 private val worker=Executors.newSingleThreadExecutor()
 private var version=0
 private var index=0
 private lateinit var image:ImageView
 private lateinit var button:Button
 private lateinit var caption:TextView
 private var files=emptyList<java.io.File>()
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  window.decorView.systemUiVisibility=android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
  val name=intent.getStringExtra("photo").orEmpty()
  if(!ImagePolicy.valid(name)) {finish();return}
  files=Photos.currentFiles(this);index=files.indexOfFirst{it.name==name};if(index<0){finish();return}
  val root=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(5,15,24))}
  image=ImageView(this).apply {scaleType=ImageView.ScaleType.FIT_CENTER};root.addView(image,LinearLayout.LayoutParams(-1,0,1f))
  caption=TextView(this).apply{setTextColor(Color.WHITE);gravity=android.view.Gravity.CENTER};root.addView(caption)
  button=Button(this);root.addView(button);setContentView(root);button.requestFocus()
  button.setOnClickListener {val key=files[index].name.substringAfter('-');val prefs=getSharedPreferences("favourites",0);val ids=(prefs.getStringSet("ids",emptySet()) ?: emptySet()).toMutableSet();if(!ids.add(key))ids.remove(key);prefs.edit().putStringSet("ids",ids).apply();refreshLabel()}
  showPhoto()
 }
 private fun refreshLabel(){val key=files[index].name.substringAfter('-');button.text=if(key in (getSharedPreferences("favourites",0).getStringSet("ids",emptySet()) ?: emptySet()))"Remove from favourites" else "Add to favourites";caption.text="${index+1} / ${files.size}  ·  Left / right to browse  ·  Back to photos"}
 private fun showPhoto(){
  refreshLabel();val request=++version;val file=files[index]
  worker.execute{val result=try{PhotoDecoding.load(file)}catch(_:Exception){null};runOnUiThread{if(isFinishing || isDestroyed || request!=version)result?.recycle() else if(result==null){Toast.makeText(this,"This cached photo is unavailable",Toast.LENGTH_SHORT).show()}else{image.setImageBitmap(result);bitmap?.recycle();bitmap=result}}}
 }
 override fun onKeyDown(keyCode:Int,event:KeyEvent):Boolean {
  if(keyCode==KeyEvent.KEYCODE_DPAD_LEFT || keyCode==KeyEvent.KEYCODE_DPAD_RIGHT){if(event.repeatCount==0){index=(index+if(keyCode==KeyEvent.KEYCODE_DPAD_LEFT)-1 else 1).coerceIn(0,files.lastIndex);showPhoto()};return true}
  return super.onKeyDown(keyCode,event)
 }
 override fun onDestroy(){version++;worker.shutdownNow();if(::image.isInitialized)image.setImageDrawable(null);bitmap?.recycle();super.onDestroy()}
}
