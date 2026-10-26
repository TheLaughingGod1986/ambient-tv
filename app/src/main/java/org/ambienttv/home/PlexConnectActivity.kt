package org.ambienttv.home
import android.app.Activity
import android.os.*
import android.graphics.Color
import android.view.*
import android.widget.*
import java.util.concurrent.Executors

class PlexConnectActivity:Activity(){
 private val worker=Executors.newSingleThreadExecutor();private val handler=Handler(Looper.getMainLooper())
 private lateinit var message:TextView;private lateinit var code:TextView
 private var pin:PlexPin?=null;private var busy=false;private var active=false
 private val poll=object:Runnable{override fun run(){checkPin()}}
 private val controls=mutableListOf<TextView>()
 private var uiScale=1f
 private fun px(value:Int)=(value*uiScale).toInt()
 private fun shape(color:Int,focus:Boolean=false)=android.graphics.drawable.GradientDrawable().apply{setColor(color);cornerRadius=px(20).toFloat();setStroke(px(if(focus)3 else 1),if(focus)Color.WHITE else Color.rgb(49,76,98))}
 override fun onCreate(state:Bundle?){
  super.onCreate(state)
  window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SECURE)
  window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
  uiScale=resources.displayMetrics.widthPixels/1920f
  val root=FrameLayout(this).apply{setBackgroundColor(Color.rgb(5,19,32))}
  fun place(view:View,x:Int,y:Int,w:Int,h:Int){root.addView(view,FrameLayout.LayoutParams(px(w),px(h)).apply{leftMargin=px(x);topMargin=px(y)})}
  fun label(value:String,size:Int)=TextView(this).apply{text=value;setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size*uiScale);setTextColor(Color.WHITE);includeFontPadding=true;gravity=Gravity.CENTER_VERTICAL}
  place(label("Connect Plex",58).apply{typeface=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL)},96,64,1728,90)
  place(label("Bring Plex and YouTube together in Continue Watching.",32).apply{setTextColor(Color.rgb(184,202,218))},96,164,1728,60)
  place(View(this).apply{background=shape(Color.rgb(12,33,50))},96,270,1008,460)
  place(label("On your phone or computer, open",30).apply{gravity=Gravity.CENTER},132,304,936,58)
  place(label("plex.tv/link",54).apply{gravity=Gravity.CENTER;typeface=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL)},132,372,936,76)
  code=label("— — — —",128).apply{gravity=Gravity.CENTER;letterSpacing=.10f;typeface=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL);maxLines=1}
  place(code,132,472,936,166)
  place(label("Approve Ambient TV. No password on the TV.",28).apply{gravity=Gravity.CENTER;setTextColor(Color.rgb(184,202,218))},132,652,936,54)
  place(label("After approving, check the connection.",30),1200,240,624,54)
  fun button(title:String,y:Int,action:()->Unit):TextView{
   val view=label(title,32).apply{
    id=View.generateViewId();isFocusable=true;isClickable=true;gravity=Gravity.CENTER;minHeight=px(128);setPadding(px(24),px(18),px(24),px(18));maxLines=1
    background=shape(Color.rgb(17,43,63));contentDescription=title
    setOnFocusChangeListener{_,focused->background=shape(if(focused)Color.rgb(227,240,250)else Color.rgb(17,43,63),focused);setTextColor(if(focused)Color.rgb(6,22,36)else Color.WHITE);reportControls()}
    setOnClickListener{action()}
   }
   controls.add(view);place(view,1200,y,624,128);return view
  }
  val check=button("Check connection",306){if(pin!=null)checkPin() else if(PlexApi.vault(this).read()!=null)refresh() else createPin()}
  val fresh=button("New code",458){createPin()}
  val back=button("Back to Home",610){startActivity(android.content.Intent(this,HomeActivity::class.java));finish()}
  val local=CheckBox(this).apply{
   id=View.generateViewId();text="Allow local HTTP if a secure server connection is unavailable";setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,29*uiScale);includeFontPadding=true;setTextColor(Color.rgb(197,211,224));minHeight=px(112);gravity=Gravity.CENTER_VERTICAL
   setPadding(px(24),px(12),px(24),px(12));compoundDrawablePadding=px(20);buttonTintList=android.content.res.ColorStateList.valueOf(Color.rgb(187,215,237));background=shape(Color.rgb(9,28,43))
   isChecked=getSharedPreferences("plex-config",0).getBoolean("localHttp",false)
   setOnCheckedChangeListener{_,checked->getSharedPreferences("plex-config",0).edit().putBoolean("localHttp",checked).apply()}
   setOnFocusChangeListener{_,focused->background=shape(Color.rgb(9,28,43),focused);reportControls()}
  }
  controls.add(local);place(local,96,790,1728,112)
  message=label("Select New code, then approve the connection on your phone.",29).apply{setTextColor(Color.rgb(184,202,218));maxLines=3;gravity=Gravity.TOP}
  place(message,96,938,1728,98)
  for(i in controls.indices){val view=controls[i];view.nextFocusUpId=controls[(i-1).coerceAtLeast(0)].id;view.nextFocusDownId=controls[(i+1).coerceAtMost(controls.lastIndex)].id;view.nextFocusLeftId=view.id;view.nextFocusRightId=view.id}
  val scroll=ScrollView(this).apply{setBackgroundColor(Color.rgb(5,19,32));isFillViewport=true;isFocusable=false}
  scroll.addView(root,FrameLayout.LayoutParams(-1,px(1080)));setContentView(scroll)
  getSharedPreferences("plex-pair-state",0).edit().putBoolean("codeReady",false).apply()
  check.requestFocus();root.post{reportControls()}
  if(PlexApi.vault(this).read()!=null){code.text="Connected";message.text=PlexFeed.status(this)}
 }
 private fun reportControls(){
  if(controls.isEmpty())return
  val rows=org.json.JSONArray()
  controls.forEach{view->val rect=android.graphics.Rect();view.getGlobalVisibleRect(rect);rows.put(org.json.JSONObject().put("label",view.text.toString()).put("width",view.width).put("height",view.height).put("visibleHeight",rect.height()).put("textSize",view.textSize).put("focused",view.isFocused).put("labelFits",view.paint.measureText(view.text.toString())<=view.width-view.paddingLeft-view.paddingRight).put("top",rect.top).put("bottom",rect.bottom))}
  getSharedPreferences("plex-pair-layout",0).edit().putString("controls",rows.toString()).putFloat("density",resources.displayMetrics.density).apply()
 }
 override fun onResume(){super.onResume();active=true;handler.postDelayed(poll,2000)}
 override fun onPause(){active=false;handler.removeCallbacks(poll);super.onPause()}
 override fun onDestroy(){active=false;handler.removeCallbacksAndMessages(null);worker.shutdownNow();super.onDestroy()}
 private fun createPin(){
  if(busy)return;window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SECURE);busy=true;pin=null;getSharedPreferences("plex-pair-state",0).edit().clear().putBoolean("codeReady",false).apply();code.text="";message.text="Getting a sign-in code…"
  worker.execute{try{val value=PlexApi.createPin(this);handler.post{busy=false;if(!isFinishing){pin=value;getSharedPreferences("plex-pair-state",0).edit().putBoolean("codeReady",true).putLong("expiresAt",value.expiresAt).apply();code.text=value.code;message.text="Approve Ambient TV at plex.tv/link. This code expires in a few minutes.";if(active)handler.postDelayed(poll,2000)}}}catch(e:Exception){handler.post{busy=false;window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);getSharedPreferences("plex-pair-state",0).edit().putBoolean("codeReady",false).putString("errorType",e.javaClass.simpleName).putInt("httpStatus",if(e is PlexHttpFailure)e.status else 0).apply();message.text=if(e is PlexHttpFailure)"Plex could not issue a code (HTTP ${e.status}). Try again." else "Could not reach Plex securely. Try again."}}}
 }
 private fun checkPin(){
  if(!active || isFinishing)return
  val current=pin
  if(current==null || busy){handler.postDelayed(poll,2000);return}
  if(System.currentTimeMillis()>=current.expiresAt){pin=null;window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);getSharedPreferences("plex-pair-state",0).edit().putBoolean("codeReady",false).apply();code.text="";message.text="Code expired. Get a new sign-in code.";return}
  busy=true
  worker.execute{try{val token=PlexApi.poll(this,current)
   if(token!=null){PlexFeed.acceptSession(this,token);pin=null;val items=PlexFeed.read(this,true);handler.post{busy=false;code.text="Connected";message.text=PlexFeed.status(this)+". Return to Ambient TV to see the mixed row."}}
   else handler.post{busy=false;if(active)handler.postDelayed(poll,2500)}
  }catch(_:Exception){handler.post{busy=false;if(active)handler.postDelayed(poll,5000)}}}
 }
 private fun refresh(){if(busy)return;busy=true;message.text="Refreshing Plex Continue Watching…";worker.execute{PlexFeed.read(this,true);handler.post{busy=false;message.text=PlexFeed.status(this)}}}
}
