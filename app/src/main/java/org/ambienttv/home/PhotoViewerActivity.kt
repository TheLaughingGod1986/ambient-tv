package org.ambienttv.home
import android.app.Activity
import android.os.Bundle
import android.widget.*
import android.graphics.Color
import android.view.KeyEvent

/** Cached-photo viewer; navigation never downloads originals or modifies the NAS. */
class PhotoViewerActivity:Activity() {
 private var bitmap:android.graphics.Bitmap?=null
 private val worker=java.util.concurrent.ThreadPoolExecutor(1,1,0L,java.util.concurrent.TimeUnit.MILLISECONDS,java.util.concurrent.LinkedBlockingQueue<Runnable>())
 @Volatile private var version=0
 private val handler=android.os.Handler(android.os.Looper.getMainLooper())
 private lateinit var controls:LinearLayout
 private var revealKey=-1
 private val hideControls=Runnable{if(!isFinishing && ::controls.isInitialized)controls.visibility=android.view.View.INVISIBLE}
 private fun showControls(){controls.visibility=android.view.View.VISIBLE;handler.removeCallbacks(hideControls);handler.postDelayed(hideControls,5000)}
 private var index=0
 private lateinit var image:ImageView
 private lateinit var button:Button
 private lateinit var caption:TextView
 internal fun controlsVisibleForTest()=controls.visibility==android.view.View.VISIBLE
 internal fun indexForTest()=index
 private var files=emptyList<java.io.File>()
 private var times=org.json.JSONObject()
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  window.decorView.systemUiVisibility=android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
  val name=intent.getStringExtra("photo").orEmpty()
  if(!ImagePolicy.valid(name)) {finish();return}
  times=try{org.json.JSONObject(getSharedPreferences("state",0).getString("photoTimes","{}").orEmpty())}catch(_:Exception){org.json.JSONObject()}
  files=Photos.currentFiles(this);index=files.indexOfFirst{it.name==name};if(index<0){finish();return}
  // Sizes follow the 1920x1080 design grid used by Home, not the display density.
  val unit=minOf(resources.displayMetrics.widthPixels/1920f,resources.displayMetrics.heightPixels/1080f)
  fun px(value:Int)=(value*unit).toInt()
  val frame=FrameLayout(this).apply{setBackgroundColor(Color.rgb(5,15,24))}
  image=ImageView(this).apply{scaleType=ImageView.ScaleType.FIT_CENTER};frame.addView(image,FrameLayout.LayoutParams(-1,-1))
  // A short fade keeps the photo visible: the caption sits bottom-left, two compact actions bottom-right.
  val root=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL;gravity=android.view.Gravity.BOTTOM;setPadding(px(64),px(96),px(64),px(44))
   background=android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,intArrayOf(Color.argb(240,3,10,18),Color.argb(170,3,10,18),Color.TRANSPARENT))
  };controls=root
  frame.addView(root,FrameLayout.LayoutParams(-1,-2,android.view.Gravity.BOTTOM))
  caption=TextView(this).apply{setTextColor(Color.rgb(240,247,252));setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,26*unit);setLineSpacing(px(8).toFloat(),1f)}
  root.addView(caption,LinearLayout.LayoutParams(0,-2,1f))
  val actions=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};root.addView(actions,LinearLayout.LayoutParams(px(460),-2))
  button=Button(this);actions.addView(button)
  actions.addView(Button(this).apply{text="Photo framing for screensaver";setOnClickListener{PhotoFraming.choose(this@PhotoViewerActivity,files[index].name.substringAfter('-'))}})
  // Native buttons retain their actions, with the same navy and pale focus language as Home.
  for(i in 0 until actions.childCount){val control=actions.getChildAt(i) as Button
   control.isAllCaps=false;control.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,24*unit);control.minHeight=0;control.minimumHeight=0;control.minWidth=0;control.minimumWidth=0;control.setPadding(px(20),0,px(20),0);control.stateListAnimator=null
   fun style(focused:Boolean){control.setTextColor(if(focused)Color.rgb(9,22,36)else Color.rgb(240,247,252));control.background=android.graphics.drawable.GradientDrawable().apply{setColor(if(focused)Color.rgb(228,239,248)else Color.argb(235,17,36,51));cornerRadius=14*unit;setStroke(maxOf(1,px(if(focused)4 else 1)),if(focused)Color.WHITE else Color.rgb(82,121,171))}}
   style(false);control.setOnFocusChangeListener{_,focused->style(focused)}
   control.layoutParams=LinearLayout.LayoutParams(-1,px(64)).apply{topMargin=if(i==0)0 else px(14)}
  }
  setContentView(frame);button.requestFocus();showControls()
  button.setOnClickListener {val key=files[index].name.substringAfter('-');val prefs=getSharedPreferences("favourites",0);val ids=(prefs.getStringSet("ids",emptySet()) ?: emptySet()).toMutableSet();if(!ids.add(key))ids.remove(key);prefs.edit().putStringSet("ids",ids).apply();refreshLabel()}
  showPhoto()
 }
 private fun refreshLabel(){val key=files[index].name.substringAfter('-');button.text=if(key in (getSharedPreferences("favourites",0).getStringSet("ids",emptySet()) ?: emptySet()))"Remove from favourites" else "Add to favourites";val taken=PhotoCaption.fullDate(PhotoCaption.seconds(files[index].name,times));caption.text="${index+1} / ${files.size}"+(if(taken!=null)"  ·  $taken" else "")+"\nLeft / right to browse  ·  Back to photos"}
 private fun showPhoto(){
  refreshLabel();val request=++version;val file=files[index]
  worker.queue.clear()
  worker.execute{if(request!=version)return@execute;val result=try{PhotoDecoding.load(file)}catch(_:Exception){null};runOnUiThread{if(isFinishing || isDestroyed || request!=version)result?.recycle() else if(result==null){Toast.makeText(this,"This cached photo is unavailable",Toast.LENGTH_SHORT).show()}else{image.setImageBitmap(result);bitmap?.recycle();bitmap=result}}}
 }
 override fun dispatchKeyEvent(event:KeyEvent):Boolean {
  val key=event.keyCode
  if(event.action==KeyEvent.ACTION_UP && key==revealKey){revealKey=-1;return true}
  if(event.action==KeyEvent.ACTION_DOWN && key!=KeyEvent.KEYCODE_BACK){
   val hidden=controls.visibility!=android.view.View.VISIBLE;showControls()
   if(hidden && key in listOf(KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN)){revealKey=key;return true}
  }
  return super.dispatchKeyEvent(event)
 }
 override fun onPause(){handler.removeCallbacks(hideControls);super.onPause()}
 override fun onResume(){super.onResume();if(::controls.isInitialized)showControls()}
 override fun onKeyDown(keyCode:Int,event:KeyEvent):Boolean {
  if(keyCode==KeyEvent.KEYCODE_DPAD_LEFT || keyCode==KeyEvent.KEYCODE_DPAD_RIGHT){if(event.repeatCount==0){index=(index+if(keyCode==KeyEvent.KEYCODE_DPAD_LEFT)-1 else 1).coerceIn(0,files.lastIndex);showPhoto()};return true}
  return super.onKeyDown(keyCode,event)
 }
 override fun onDestroy(){handler.removeCallbacksAndMessages(null);version++;worker.shutdownNow();if(::image.isInitialized)image.setImageDrawable(null);bitmap?.recycle();super.onDestroy()}
}
