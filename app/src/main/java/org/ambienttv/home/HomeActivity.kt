package org.ambienttv.home

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.TypedValue
import android.view.*
import android.widget.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

/** Ambient TV launcher, with independent photo and resume-feed refresh. */
class HomeActivity:Activity() {
 companion object {internal var previewFiles:List<File>?=null;internal var previewWeather:WeatherPanel?=null;internal var previewResolution:Pair<Int,Int>?=null}
 private fun photoFiles()=previewFiles ?: Photos.currentFiles(this)
 internal fun pageForTest()=page
 internal fun focusForTest(key:String){check(focusViews[key]?.requestFocus()==true)}
 internal fun sidebarExpandedForTest()=sidebarExpanded==true
 internal fun focusKeyForTest()=focusViews.entries.firstOrNull{it.value.hasFocus()}?.key
 internal fun feedSnapshotForTest()=displayedWatchItems.toList()
 internal fun feedObservingForTest()=observingFeed
 internal fun showPageForTest(name:String)=showPage(name)
 internal fun secondaryReadyForTest():Boolean {
  fun loading(v:View):Boolean {
   if(v is TextView && v.text.toString().startsWith("Loading your Plex"))return true
   return v is ViewGroup && (0 until v.childCount).any{loading(v.getChildAt(it))}
  }
  return !loading(root)
 }

 internal fun readyForTest()=photoSlots.size==4 && focusViews.containsKey("footer:4")
 internal fun photoReadyForTest()=photoSlots.count{it.image.hasPhoto}>=minOf(3,photoFiles().size)
 internal fun refreshDecorForTest(){updateHeader();updateHomePhotos()}
 internal fun boundsForTest()=focusViews.mapValues{(_,view)->Rect().also{view.getDrawingRect(it);root.offsetDescendantRectToMyCoords(view,it)}}
 private val focusViews=linkedMapOf<String,View>()
 private val worker=Executors.newSingleThreadExecutor()
 private val feedWorker=Executors.newSingleThreadExecutor()
 private val feedState=FeedRefreshState()
 private var feedActive=false
 private var observingFeed=false
 private var displayedWatchItems=emptyList<WatchItem>()
 private val handler=Handler(Looper.getMainLooper())
 private lateinit var root:FrameLayout
 private var page="Home"
 private var resumedOnce=false

 private val photoSlots=mutableListOf<PhotoSlot>()
 private lateinit var mediaHost:FrameLayout
 private val mediaBitmaps=mutableListOf<Bitmap>()
 private var mediaEpoch=0
 private var mediaSignature=""
 private var currentWatchItems=emptyList<WatchItem>()
 private var availableWatchItems=emptyList<WatchItem>()
 private data class PhotoSlot(val image:OrientationPhotoView,val detail:TextView,val placeholder:ImageView)
 @Volatile private var epoch=0
 private val bitmaps=mutableListOf<Bitmap>()
 private var scale=1f
 private var nextId=1000
 private val titleColor=Color.rgb(240,247,252)
 private val tick=object:Runnable { override fun run() { if(!isFinishing) { updateHeader();if(page=="Home")updateHomePhotos();refreshPhotos();handler.postDelayed(this,60000-System.currentTimeMillis()%60000) } } }
 private fun refreshPhotos() {
  if(previewFiles!=null)return
  val before=getSharedPreferences("state",0).getString("generation","")
  Photos.refresh(applicationContext) {
   handler.post {
    if(!isFinishing && !isDestroyed && hasWindowFocus() && page=="Home" && before!=getSharedPreferences("state",0).getString("generation","")) {
     updateHomePhotos()
    }
   }
  }
 }
 private lateinit var backdrop:HomeBackdropView
 private lateinit var sidebarPanel:View
 private var compactLayout=false
 private var sidebarExpanded:Boolean?=null
 private var sidebarAnimation:android.animation.ValueAnimator?=null
 private val sidebarNames=listOf("Home","TV Shows","Movies","Anime","Apps","Photos","Search","Settings")
 private val sidebarUpdate=Runnable{updateSidebar()}
 private fun updateSidebar(){
  if(!::sidebarPanel.isInitialized)return
  val expanded=!compactLayout || focusViews.filterKeys{it.startsWith("nav:")}.values.any{it.hasFocus()}
  if(sidebarExpanded==expanded)return
  val first=sidebarExpanded==null;sidebarExpanded=expanded;sidebarAnimation?.cancel()
  val fullWidth=if(compactLayout)244 else 220;val navWidth=if(compactLayout)196 else 180
  val start=sidebarPanel.layoutParams.width;val end=p(if(expanded)fullWidth else 96)
  sidebarNames.forEachIndexed{i,label->(focusViews["nav:$i"] as? TextView)?.let{v->
   v.text=if(expanded)label else "";v.compoundDrawablePadding=if(expanded)p(14)else 0
   v.setPadding(p(17),p(8),p(12),p(8));v.bringToFront()
  }}
  fun frame(width:Int){val lp=sidebarPanel.layoutParams;lp.width=width;sidebarPanel.layoutParams=lp
   val amount=((width-p(96)).toFloat()/p(fullWidth-96).coerceAtLeast(1)).coerceIn(0f,1f)
   sidebarNames.indices.forEach{i->focusViews["nav:$i"]?.let{v->val n=v.layoutParams;n.width=(p(64)+(p(navWidth)-p(64))*amount).toInt();v.layoutParams=n}}
  }
  val motion=Settings.Global.getFloat(contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f
  if(first || !motion)frame(end) else sidebarAnimation=android.animation.ValueAnimator.ofInt(start,end).apply{duration=180;addUpdateListener{frame(it.animatedValue as Int)};start()}
 }
 private fun contentPx(value:Int)=p(if(compactLayout)(value*1760f/1644f).toInt()else value)
 private lateinit var header:HomeWeatherView
 private lateinit var clock:TextView
 private var mediaHeading:TextView?=null
 private val homePress=object:BroadcastReceiver(){override fun onReceive(context:Context?,intent:Intent?){
  // Android does not relaunch an already-visible Home task. Reset only our page
  // for an actual Home-key broadcast; dream dismissal never sends this reason.
  if(intent?.action==Intent.ACTION_CLOSE_SYSTEM_DIALOGS && intent.getStringExtra("reason")=="homekey"){if(page!="Home")showPage("Home");refreshFeedOnReturn()}
 }}
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
  val size=previewResolution ?: (resources.displayMetrics.widthPixels to resources.displayMetrics.heightPixels)
  scale=HomeLayout.scale(size.first,size.second)
  root=FrameLayout(this).apply { background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(4,14,24),Color.rgb(10,30,44),Color.rgb(5,15,24))) }
  setContentView(root);root.viewTreeObserver.addOnGlobalFocusChangeListener{_,_->handler.removeCallbacks(sidebarUpdate);handler.post(sidebarUpdate)};showPage("Home")
  registerHomePress()
  if(previewFiles==null && Build.VERSION.SDK_INT>=23 && checkSelfPermission("android.permission.READ_TV_LISTINGS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf("android.permission.READ_TV_LISTINGS"),42)
 }
 @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
 private fun registerHomePress(){
  val filter=IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
  // The flag overload is API33+. Android23-32 have only the legacy registration.
  if(Build.VERSION.SDK_INT>=33)registerReceiver(homePress,filter,Context.RECEIVER_NOT_EXPORTED)else registerReceiver(homePress,filter)
 }
 override fun onRequestPermissionsResult(code:Int,permissions:Array<out String>,results:IntArray){super.onRequestPermissionsResult(code,permissions,results);if(code==42 && page=="Home")showPage("Home")}
 override fun onNewIntent(intent:Intent) {super.onNewIntent(intent);setIntent(intent);if(intent.hasCategory(Intent.CATEGORY_HOME)){if(page!="Home")showPage("Home");refreshFeedOnReturn()}}
 override fun onResume() { super.onResume();if(compactLayout!=getSharedPreferences("home-ui",0).getBoolean("compactSidebar",false))showPage(page);handler.post(sidebarUpdate);feedActive=true;if(resumedOnce && page=="Home")updateHomePhotos();resumedOnce=true
  if(previewFiles==null && !observingFeed && Build.VERSION.SDK_INT>=26)try{contentResolver.registerContentObserver(android.media.tv.TvContract.WatchNextPrograms.CONTENT_URI,true,feedObserver);observingFeed=true}catch(_:Exception){}
  refreshFeedOnReturn();handler.removeCallbacks(feedPoll);handler.postDelayed(feedPoll,60000)
  handler.removeCallbacks(tick);handler.post(tick);if(previewFiles==null)WeatherStore.refresh(applicationContext)
 }
 override fun onPause() {feedActive=false;feedState.pause();handler.removeCallbacks(feedRead);handler.removeCallbacks(feedPoll);handler.removeCallbacks(feedReturnRetry);handler.removeCallbacks(feedReturnFinalRetry)
  if(observingFeed){contentResolver.unregisterContentObserver(feedObserver);observingFeed=false};handler.removeCallbacks(tick);super.onPause() }
 override fun onTrimMemory(level:Int) {
  super.onTrimMemory(level)
  if(level>=android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)PlexFeed.trimArtwork()
 }
 override fun onLowMemory(){super.onLowMemory();PlexFeed.trimArtwork()}
 override fun onDestroy() { unregisterReceiver(homePress);epoch++;handler.removeCallbacksAndMessages(null);worker.shutdownNow();feedWorker.shutdownNow();if(::root.isInitialized)clearPage();super.onDestroy() }
 private fun clearPage() {
  sidebarAnimation?.cancel();sidebarExpanded=null;epoch++;mediaEpoch++;focusViews.clear();photoSlots.clear();mediaSignature=""
  mediaBitmaps.forEach{if(!it.isRecycled)it.recycle()};mediaBitmaps.clear()
  fun detach(view:View){if(view is OrientationPhotoView)view.clear();if(view is ImageView)view.setImageDrawable(null);if(view is android.view.ViewGroup)for(i in 0 until view.childCount)detach(view.getChildAt(i))}
  detach(root);root.removeAllViews();bitmaps.forEach {if(!it.isRecycled)it.recycle()};bitmaps.clear()
 }
 private fun p(v:Int)=(v*scale).toInt()
 private fun place(view:View,x:Int,y:Int,w:Int,h:Int) { val content=x>=244;root.addView(view,FrameLayout.LayoutParams(if(content)contentPx(w)else p(w),p(h)).apply { leftMargin=if(content && compactLayout)p(128)+contentPx(x-244)else p(x);topMargin=p(y) }) }
 private fun text(value:String,size:Int=30):TextView=TextView(this).apply { text=value;setTextSize(TypedValue.COMPLEX_UNIT_PX,size*scale);setTextColor(titleColor);gravity=Gravity.CENTER_VERTICAL }
 private fun tile(label:String,action:()->Unit):TextView=text(label,26).apply {
  id=nextId++;isFocusable=true;isFocusableInTouchMode=true;isClickable=true;setPadding(p(20),p(10),p(16),p(10));contentDescription=label
  background=rounded(Color.rgb(17,36,51),false)
  setOnFocusChangeListener { _,focused -> background=rounded(if(focused)Color.rgb(228,239,248) else Color.rgb(17,36,51),focused);setTextColor(if(focused)Color.rgb(9,22,36) else titleColor);compoundDrawables.forEach{if(it is HomeNavIcon)it.setTint(if(focused)Color.rgb(9,22,36)else titleColor)} }
  setOnClickListener { action() }
 }
 private fun rounded(color:Int,focus:Boolean)=GradientDrawable().apply { setColor(color);cornerRadius=14*scale;setStroke(p(if(focus)3 else 1),if(focus)Color.WHITE else Color.rgb(43,66,82)) }
 private fun photoOutline(focused:Boolean)=GradientDrawable().apply{
  setColor(Color.TRANSPARENT);cornerRadius=14*scale
  setStroke(maxOf(1,p(if(focused)3 else 1)),if(focused)Color.rgb(255,226,173)else Color.argb(150,126,168,211))
 }
 private fun dockBackground(focused:Boolean)=GradientDrawable(GradientDrawable.Orientation.TL_BR,
  if(focused)intArrayOf(Color.rgb(235,244,253),Color.rgb(208,226,246))else intArrayOf(Color.rgb(25,61,105),Color.rgb(13,39,72))).apply{
  cornerRadius=14*scale;setStroke(maxOf(1,p(if(focused)3 else 1)),if(focused)Color.WHITE else Color.rgb(82,121,171))
 }
 private fun showPage(name:String) {
  if(name!=page && name in listOf("Movies","TV Shows","Anime")){catalogPageIndex=0;catalogFocusAfterLoad=null}
  compactLayout=getSharedPreferences("home-ui",0).getBoolean("compactSidebar",false);page=name;clearPage()
  backdrop=HomeBackdropView(this,name=="Home");place(backdrop,0,0,1920,1080)
  header=HomeWeatherView(this);place(header,244,38,600,110)
  clock=text("",38).apply { gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL };place(clock,1430,40,430,118);updateHeader()
  val names=sidebarNames
  var first:View?=null
  names.forEachIndexed { index,label ->
   val nav=tile(label) { when(label) { else->showPage(label) } }
   val icon=HomeNavIcon(when(label){"TV Shows"->"TV";"Anime"->"Movies";else->label});icon.setBounds(0,0,p(30),p(30));icon.setTint(titleColor)
   nav.setTextSize(TypedValue.COMPLEX_UNIT_PX,22*scale);nav.setPadding(p(16),p(8),p(12),p(8));nav.compoundDrawablePadding=p(14);nav.setCompoundDrawables(icon,null,null,null)
   nav.background=if(label==name)rounded(Color.rgb(228,239,248),false)else android.graphics.drawable.ColorDrawable(Color.TRANSPARENT);nav.setTextColor(if(label==name)Color.rgb(9,22,36)else titleColor);icon.setTint(if(label==name)Color.rgb(9,22,36)else titleColor)
   nav.setOnFocusChangeListener {_,focused->val active=focused || label==name;nav.background=if(active)rounded(Color.rgb(228,239,248),focused)else android.graphics.drawable.ColorDrawable(Color.TRANSPARENT);nav.setTextColor(if(active)Color.rgb(9,22,36)else titleColor);icon.setTint(if(active)Color.rgb(9,22,36)else titleColor)}
   focusViews["nav:$index"]=nav
   place(nav,24,182+index*84,180,66);if(label==name)first=nav
  }
  focusViews.filterKeys{it.startsWith("nav:")}.values.toList().let{nav->nav.forEachIndexed{i,v->v.nextFocusUpId=nav[maxOf(0,i-1)].id;v.nextFocusDownId=nav[minOf(nav.lastIndex,i+1)].id;v.nextFocusLeftId=v.id}}
  when(name) {
   "Home"->home()
   "Continue Watching"->{heading("Continue Watching");if(currentWatchItems.isEmpty())place(text("No continue watching items are available. Open Plex or YouTube from Home.",28),244,280,1500,100)else watchGrid()}
   "Apps"->apps()
   "Photos"->gallery(galleryFavourites)
   "Movies","TV Shows","Anime"->catalog(name,catalogPageIndex)
   "TV"->{
    heading("TV & inputs")
    val actions=mutableListOf<Pair<String,()->Unit>>("Open TV" to {openTv()})
    if(Build.VERSION.SDK_INT>=24)try {
     val manager=getSystemService(android.content.Context.TV_INPUT_SERVICE) as android.media.tv.TvInputManager
     manager.tvInputList.filter {it.isPassthroughInput}.forEach {input->
      val title=input.loadLabel(this)?.toString()?.takeIf {it.isNotBlank()} ?: "TV input"
      val intent=Intent(Intent.ACTION_VIEW,android.media.tv.TvContract.buildChannelUriForPassthroughInput(input.id))
      if(intent.resolveActivity(packageManager)!=null)actions.add(title to {safeStart(intent)})
     }
    }catch(_:Exception){}
    actions.add("Android input settings" to {safeStart(Intent("android.settings.TV_INPUT_SETTINGS"))});actionGrid(actions)
   }
   "Search"->searchPage()
   "Settings"->settingsPage()
  }
  sidebarPanel=View(this).apply{elevation=p(8).toFloat();isClickable=true;setBackgroundColor(Color.argb(232,3,13,25));importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO};place(sidebarPanel,0,0,96,1080)
  focusViews.filterKeys{it.startsWith("nav:")}.values.forEach{it.elevation=p(9).toFloat();it.bringToFront()}
  (first ?: focusViews[if(name=="TV")"nav:7"else"nav:0"])?.requestFocus();updateSidebar()
 }
 private fun heading(value:String) { place(text(value,46).apply{typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)},244,174,1500,65) }
 private fun updateHeader() {
  if(!::header.isInitialized) return
  header.update(previewWeather ?: WeatherStore.panel(this))
  val format=SimpleDateFormat("HH:mm  EEE, d MMM",Locale.getDefault()).apply { timeZone=TimeZone.getDefault() };clock.text=if(page=="Home") {
   val time=SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date())
   val date=SimpleDateFormat("EEE, d MMM",Locale.getDefault()).format(Date())
   android.text.SpannableString("$time\n$date").apply {
    setSpan(android.text.style.RelativeSizeSpan(1.22f),0,time.length,0)
    setSpan(android.text.style.StyleSpan(Typeface.BOLD),0,time.length,0)
    setSpan(android.text.style.RelativeSizeSpan(.72f),time.length+1,length,0)
   }
  }else format.format(Date())
 }
 private fun headingAction(value:String,y:Int,key:String,action:()->Unit):TextView {
  val view=text(value,if(key=="heading:0")42 else 36).apply {
   typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);id=nextId++;isFocusable=true;isFocusableInTouchMode=true;isClickable=true
   contentDescription=if(key=="heading:0")"Browse your photos" else "Browse Continue Watching"
   setOnClickListener{action()};setOnFocusChangeListener{_,focused->background=if(focused)rounded(Color.TRANSPARENT,true)else null}
  }
  focusViews[key]=view;place(view,244,y,if(key=="heading:0")550 else 650,if(key=="heading:0")52 else 62);return view
 }
 private fun home() {
  headingAction("Your Photos  ›",148,"heading:0"){showPage("Photos")}
  place(text("Daily family memories",22).apply{setTextColor(Color.rgb(218,228,239))},244,204,900,28)
  val titles=listOf("Recent","Browse photos","Favourites","All Photos")
  val icons=listOf("Recent","Photos","Favourites","All Photos")
  val actions=listOf<()->Unit>(
   {photoFiles().firstOrNull()?.let{openPhoto(it)} ?: safeStart(Intent(this,SettingsActivity::class.java))},
   {showPage("Photos")},{galleryPage(true)},{showPage("Photos")})
  for(i in titles.indices) {
   val card=ClippedPhotoCard(this,14*scale).apply{id=nextId++;isFocusable=true;isFocusableInTouchMode=true;isClickable=true;background=rounded(Color.rgb(17,35,49),false);foreground=photoOutline(false);clipToOutline=true;contentDescription=titles[i];setOnClickListener{actions[i]()};setOnFocusChangeListener{_,focus->foreground=photoOutline(focus)}}
   card.elevation=p(if(i==0)6 else 4).toFloat()
   val image=OrientationPhotoView(this,true);card.addView(image,FrameLayout.LayoutParams(-1,-1))
   val placeholder=ImageView(this).apply{setImageDrawable(HomeNavIcon(icons[i]).apply{setTint(Color.rgb(164,191,210))})};card.addView(placeholder,FrameLayout.LayoutParams(p(70),p(70),Gravity.CENTER).apply{bottomMargin=p(30)})
   card.addView(View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(Color.TRANSPARENT,Color.argb(242,4,10,18)))},FrameLayout.LayoutParams(-1,p(170),Gravity.BOTTOM))
   val caption=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(p(20),0,p(16),p(16))}
   val icon=ImageView(this).apply{setImageDrawable(HomeNavIcon(icons[i]).apply{setTint(Color.WHITE)})};caption.addView(icon,LinearLayout.LayoutParams(p(30),p(30)).apply{rightMargin=p(12)})
   val words=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
   words.addView(text(titles[i],26).apply{typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END})
   val detail=text("",18).apply{setTextColor(Color.rgb(204,216,228));maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END};words.addView(detail)
   caption.addView(words,LinearLayout.LayoutParams(0,-2,1f));card.addView(caption,FrameLayout.LayoutParams(-1,p(94),Gravity.BOTTOM))
   photoSlots.add(PhotoSlot(image,detail,placeholder));focusViews["photo:$i"]=card
   place(card,HomeLayout.photoX(i),HomeLayout.PHOTO_Y+if(i==0)0 else 12,HomeLayout.photoWidth(i),HomeLayout.PHOTO_H-if(i==0)0 else 12)
  }
  updateHomePhotos()
  mediaHeading=headingAction("Continue Watching  ›",HomeLayout.MEDIA_HEADING_Y,"heading:1"){showPage("Continue Watching")}
  mediaHost=FrameLayout(this);place(mediaHost,HomeLayout.LEFT,HomeLayout.MEDIA_Y,HomeLayout.RIGHT-HomeLayout.LEFT,HomeLayout.MEDIA_H)
  renderWatchItems(emptyList(),"loading")
  val footerActions=listOf<Pair<String,()->Unit>>(
   "Open Plex" to {launchPackage("com.plexapp.android")},"Open YouTube" to {launchPackage("com.google.android.youtube.tv")},
   "Open Synology Photos" to {launchPackage("com.synology.projectkailash")},"All apps" to {showPage("Apps")},"Screensaver" to {startScreensaver()})
  footerActions.forEachIndexed{i,(label,action)->
   val view=tile(label,action)
   val originalFocus=view.onFocusChangeListener
   view.background=dockBackground(false);view.setOnFocusChangeListener{v,focused->originalFocus?.onFocusChange(v,focused);view.background=dockBackground(focused)}
   focusViews["footer:$i"]=view;place(view,244+i*333,HomeLayout.DOCK_Y,HomeLayout.MEDIA_W,HomeLayout.DOCK_H)
   val icon=if(i<3)try{packageManager.getApplicationIcon(listOf("com.plexapp.android","com.google.android.youtube.tv","com.synology.projectkailash")[i])}catch(_:Exception){HomeNavIcon("Apps").apply{setTint(titleColor)}}else HomeNavIcon(if(i==3)"Apps" else "Photos").apply{setTint(titleColor)}
   val longest=maxOf(icon.intrinsicWidth,icon.intrinsicHeight,1);icon.setBounds(0,0,p((42f*icon.intrinsicWidth.coerceAtLeast(1)/longest).toInt()),p((42f*icon.intrinsicHeight.coerceAtLeast(1)/longest).toInt()))
   view.setPadding(p(18),p(8),p(12),p(8));view.compoundDrawablePadding=p(12);view.setCompoundDrawables(icon,null,null,null)
   view.setTextSize(TypedValue.COMPLEX_UNIT_PX,if(i==2)21*scale else 23*scale);view.maxLines=1;view.ellipsize=android.text.TextUtils.TruncateAt.END
  }
  wireFocus();loadWatchNext()
 }
 private fun dailyPhotos(files:List<File>,slot:String,count:Int):List<File> {
  val byId=files.associateBy{it.name.substringAfter('-')};val day=HomePhotoPolicy.localDay(System.currentTimeMillis())
  val prefs=getSharedPreferences("home-daily",0)
  fun saved(key:String)=prefs.getString(key,"").orEmpty().split(',').filter{it.isNotEmpty()}
  val same=prefs.getLong("$slot-day",Long.MIN_VALUE)==day
  val chosen=HomePhotoPolicy.choose(byId.keys.toList(),day,count,if(same)emptyList() else saved("$slot-ids"),if(same)saved("$slot-ids")else emptyList())
  if(previewFiles==null && (!same || chosen!=saved("$slot-ids")))prefs.edit().putLong("$slot-day",day).putString("$slot-ids",chosen.joinToString(",")).apply()
  return chosen.mapNotNull{byId[it]}
 }
 private fun updateHomePhotos() {
  if(page!="Home" || photoSlots.size!=4)return
  val files=photoFiles();val favourite=dailyPhotos(favourites(),"favourites",1).firstOrNull()
  val daily=dailyPhotos(files.filter{it!=favourite},"photos",3)
  val selected=listOf(daily.getOrNull(0),daily.getOrNull(1),favourite,daily.getOrNull(2))
  val descriptions=listOf(if(files.isEmpty())"No cached photos" else "Daily selection","Your collection",if(favourite==null)"No favourites yet" else "Saved by you","${files.size} cached photos")
  photoSlots.forEachIndexed{i,slot->
   slot.detail.text=descriptions[i];focusViews["photo:$i"]?.contentDescription=listOf("Recent","Browse photos","Favourites","All Photos")[i]+", "+descriptions[i]
   val file=selected[i];slot.placeholder.visibility=if(file==null)View.VISIBLE else View.GONE
   if(slot.image.request(file?.name)) {
    if(file==null){slot.image.show(null,null);return@forEachIndexed}
    val token=epoch;val request=slot.image.requestVersion
    worker.execute {
     if(token!=epoch || slot.image.requestVersion!=request)return@execute
     val bitmap=try{PhotoDecoding.load(file,768,900)}catch(_:Exception){null}
     if(token!=epoch || slot.image.requestVersion!=request){bitmap?.recycle();return@execute}
     val fill=bitmap?.let{OrientationPhotoView.softFill(it)}
     val face=bitmap?.let{HeroComposition.detect(it)}
     handler.post{if(token==epoch && slot.image.requestVersion==request && !isDestroyed){slot.image.show(bitmap,fill,face);slot.placeholder.visibility=if(bitmap==null)View.VISIBLE else View.GONE}else{bitmap?.recycle();fill?.recycle()}}
    }
   }
  }
 }
 private fun photoTile(file:File?,label:String,detail:String="",media:Boolean=false,action:()->Unit):FrameLayout {
  val card=ClippedPhotoCard(this,14*scale).apply { id=nextId++;isFocusable=true;isFocusableInTouchMode=true;isClickable=true;contentDescription=label;background=rounded(Color.rgb(16,35,48),false);foreground=rounded(Color.TRANSPARENT,false);clipToOutline=true }
  val image=ImageView(this).apply { scaleType=ImageView.ScaleType.CENTER_CROP }
  card.addView(image,FrameLayout.LayoutParams(-1,-1))
  card.addView(View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(Color.TRANSPARENT,Color.argb(235,1,8,14)))},FrameLayout.LayoutParams(-1,p(138),Gravity.BOTTOM))
  val captions=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(p(if(media)16 else 28),0,p(12),p(if(media)14 else 18));gravity=Gravity.BOTTOM}
  captions.addView(text(label,if(media)20 else 23).apply{typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);maxLines=if(media)1 else 2;ellipsize=android.text.TextUtils.TruncateAt.END})
  if(detail.isNotBlank())captions.addView(text(detail,if(media)18 else 22).apply{setTextColor(Color.LTGRAY);maxLines=1})
  card.addView(captions,FrameLayout.LayoutParams(-1,p(100),Gravity.BOTTOM))
  card.setOnFocusChangeListener { _,focus->card.foreground=rounded(Color.TRANSPARENT,focus) };card.setOnClickListener { action() }
  if(file!=null){image.setImageResource(android.R.drawable.ic_menu_gallery);loadPhoto(file,image)}
  return card
 }
 private fun loadPhoto(file:File,view:ImageView) {
  val token=epoch
  worker.execute {
   if(token!=epoch)return@execute
   val bitmap=try { PhotoDecoding.load(file,if(page=="Home")768 else 512,if(page=="Home")1080 else 400) } catch(_:Exception) {null}
   handler.post { if(token!=epoch || isFinishing)bitmap?.recycle() else if(bitmap!=null) {bitmaps.add(bitmap);view.setImageBitmap(bitmap)} }
  }
 }
 private fun catalog(category:String,index:Int){
  heading(category)
  val loading=text("Loading your Plex library…",28);place(loading,244,248,1500,68)
  val token=epoch
  worker.execute{
   try{
    val result=PlexFeed.catalog(this,category,index)
    handler.post{
     if(token!=epoch || isFinishing)return@post
     root.removeView(loading)
     place(text("Latest additions · Plex",25),244,236,1500,52)
     val gridControls=mutableListOf<View>()
     result.items.forEachIndexed{position,item->
      val card=photoTile(null,item.title){WatchItem(item.title,"com.plexapp.android",PlexProtocol.detailUri(item.server,item.key),"",-1,-1).open(this)}
      gridControls.add(card)
      place(card,244+(position%5)*333,306+(position/5)*294,312,272)
      val image=card.getChildAt(0) as ImageView
      try{image.setImageDrawable(packageManager.getApplicationIcon("com.plexapp.android"));image.scaleType=ImageView.ScaleType.FIT_CENTER;image.setPadding(p(70),p(30),p(70),p(90))}catch(_:Exception){}
      worker.execute imageJob@{if(token!=epoch)return@imageJob;val bitmap=if(item.thumb.isBlank())null else WatchNextReader.art(this,"plex-art://${item.server}/${item.key}");handler.post{if(token==epoch && bitmap!=null){bitmaps.add(bitmap);image.setPadding(0,0,0,0);image.scaleType=ImageView.ScaleType.FIT_CENTER;image.setImageBitmap(bitmap)}else bitmap?.recycle()}}
     }
     val navId=focusViews["nav:"+when(category){"TV Shows"->1;"Movies"->2;else->3}]?.id ?: View.NO_ID
     gridControls.forEachIndexed{i,v->v.nextFocusLeftId=if(i%5==0)navId else gridControls[i-1].id;v.nextFocusRightId=if(i%5==4 || i==gridControls.lastIndex)v.id else gridControls[i+1].id;v.nextFocusUpId=if(i<5)v.id else gridControls[i-5].id;v.nextFocusDownId=if(i+5<gridControls.size)gridControls[i+5].id else v.id}
     if(gridControls.isNotEmpty())focusViews.filterKeys{it.startsWith("nav:")}.values.forEach{it.nextFocusRightId=gridControls.first().id}
     if(result.items.isEmpty())place(text(if(category=="Anime")"No Anime library, Anime genre, or Japanese animation was found." else "No items found in this Plex category.",28),244,340,1500,110)
     val pager=mutableListOf<View>()
     fun pageButton(label:String,x:Int,action:()->Unit){val v=tile(label,action);pager.add(v);place(v,x,944,300,72)}
     if(index>0)pageButton("Previous page",244){showCatalogPage(category,index-1)}
     if(result.hasMore)pageButton("Next page",570){showCatalogPage(category,index+1)}
     pageButton("Refresh",896){showCatalogPage(category,index)}
     wireGrid(gridControls,5,pager)
     pager.forEachIndexed{i,v->v.nextFocusUpId=gridControls.lastOrNull()?.id ?: navId;v.nextFocusLeftId=if(i==0)navId else pager[i-1].id;v.nextFocusRightId=if(i==pager.lastIndex)v.id else pager[i+1].id;v.nextFocusDownId=v.id}
     val desired=catalogFocusAfterLoad;catalogFocusAfterLoad=null
     if(desired!=null && currentFocus?.id==navId && hasWindowFocus()){
      val target=if(desired=="@first")gridControls.firstOrNull() ?: pager.firstOrNull()else (gridControls+pager).firstOrNull{it.contentDescription?.toString()==desired}
      target?.requestFocus()
     }
    }
   }catch(_:Exception){handler.post{if(token==epoch){loading.text="Could not load this Plex library. Check your server connection.";val retry=tile("Retry"){showCatalogPage(category,index)};val connect=tile("Plex connection"){safeStart(Intent(this,PlexConnectActivity::class.java))};place(retry,244,360,300,80);place(connect,570,360,400,80);wireGrid(listOf(retry,connect),2)}}}
  }
 }
 private var catalogPageIndex=0
 private var catalogFocusAfterLoad:String?=null
 private fun showCatalogPage(category:String,index:Int){catalogFocusAfterLoad=if(index==catalogPageIndex)currentFocus?.contentDescription?.toString()else "@first";catalogPageIndex=index;showPage(category)}
 private fun galleryPage(favs:Boolean) { galleryFavourites=favs;galleryIndex=0;showPage("Photos") }
 private var galleryFavourites=false
 private var galleryIndex=0
 private fun gallery(favs:Boolean) {
  heading(if(favs)"Favourites" else "Your photos")
  val files=if(favs)favourites() else photoFiles()
  place(text("Family memories · ${files.size} cached photos",23),244,236,1050,48)
  val chips=listOf(tile("Favourites"){galleryPage(true)},tile("All"){galleryPage(false)})
  chips.forEachIndexed{i,v->place(v,1514+i*184,236,174,58)}
  galleryIndex=galleryIndex.coerceIn(0,maxOf(0,(files.size-1)/8))
  if(files.isEmpty()) place(text(if(favs)"Open a photo to add it to your favourites." else "No cached photos yet. Open connection settings.",28),244,340,1450,90)
  val cards=files.drop(galleryIndex*8).take(8).mapIndexed{i,file->
   val card=ClippedPhotoCard(this,14*scale).apply{id=nextId++;isFocusable=true;isFocusableInTouchMode=true;isClickable=true;contentDescription="View photo ${galleryIndex*8+i+1} of ${files.size}";foreground=rounded(Color.TRANSPARENT,false)}
   val image=OrientationPhotoView(this);card.addView(image,FrameLayout.LayoutParams(-1,-1))
   card.setOnClickListener{openPhoto(file)};card.setOnFocusChangeListener{_,focused->card.foreground=rounded(Color.TRANSPARENT,focused)}
   bindGalleryPhoto(image,file);place(card,244+(i%4)*416,316+(i/4)*290,396,270);card
  }
  val actions=mutableListOf<View>()
  actions.add(tile("Open Synology Photos"){launchPackage("com.synology.projectkailash")}.also{place(it,244,944,400,72)})
  actions.add(tile("Browse all cached photos"){galleryPage(false)}.also{place(it,664,944,430,72)})
  if(galleryIndex>0)actions.add(tile("Previous"){galleryIndex--;showPage("Photos")}.also{place(it,1114,944,220,72)})
  if((galleryIndex+1)*8<files.size)actions.add(tile("Next page"){galleryIndex++;showPage("Photos")}.also{place(it,1354,944,250,72)})
  wireGrid(cards,4,actions,chips)
 }
 private fun bindGalleryPhoto(view:OrientationPhotoView,file:File){
  val token=epoch;view.request(file.name);val version=view.requestVersion
  worker.execute{if(token!=epoch)return@execute;val b=try{PhotoDecoding.load(file,800,600)}catch(_:Exception){null};val fill=b?.let{OrientationPhotoView.softFill(it)}
   handler.post{if(token!=epoch || view.requestVersion!=version){b?.recycle();fill?.recycle()}else view.show(b,fill)}
  }
 }
 private fun favourites():List<File> { if(previewFiles!=null)return emptyList();val ids=getSharedPreferences("favourites",0).getStringSet("ids",emptySet()) ?: emptySet();return photoFiles().filter { it.name.substringAfter('-') in ids } }
 private fun openPhoto(file:File) {safeStart(Intent(this,PhotoViewerActivity::class.java).putExtra("photo",file.name))}
 private fun apps() {
  heading("Apps");place(text("Installed applications · select an app to open",23),244,236,1500,48)
  val intent=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
  val apps=packageManager.queryIntentActivities(intent,0).distinctBy {it.activityInfo.packageName}.sortedBy {it.loadLabel(packageManager).toString()}
  val scroll=ScrollView(this);val rows=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};scroll.addView(rows)
  val controls=mutableListOf<View>()
  apps.chunked(4).forEach{batch->val row=LinearLayout(this).apply{isBaselineAligned=false};batch.forEach{info->
   val label=info.loadLabel(packageManager).toString()
   val view=tile(label){launchPackage(info.activityInfo.packageName)}.apply{maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END;setTextSize(TypedValue.COMPLEX_UNIT_PX,22*scale)}
   try{val icon=info.loadIcon(packageManager);icon.setBounds(0,0,p(48),p(48));view.setCompoundDrawables(icon,null,null,null);view.compoundDrawablePadding=p(18)}catch(_:Exception){view.text=label.take(1)+"  "+label}
   controls.add(view);row.addView(view,LinearLayout.LayoutParams(contentPx(396),p(114)).apply{setMargins(0,0,contentPx(20),p(22))})
  };rows.addView(row)}
  place(scroll,244,306,1648,710);wireGrid(controls,4)
 }
 private fun wireGrid(cards:List<View>,columns:Int,actions:List<View> = emptyList(),above:List<View> = emptyList()){
  val nav=focusViews.filterKeys{it.startsWith("nav:")}.values.toList()
  val navId=focusViews["nav:"+when(page){"TV Shows"->1;"Movies"->2;"Anime"->3;"Apps"->4;"Photos"->5;"Search"->6;else->7}]?.id ?: View.NO_ID
  val first=above.firstOrNull() ?: cards.firstOrNull() ?: actions.firstOrNull()
  nav.forEach{it.nextFocusRightId=first?.id ?: it.id}
  cards.forEachIndexed{i,v->v.nextFocusLeftId=if(i%columns==0)navId else cards[i-1].id;v.nextFocusRightId=if(i%columns==columns-1 || i==cards.lastIndex)v.id else cards[i+1].id;v.nextFocusUpId=if(i<columns)above.getOrNull(i.coerceAtMost(above.lastIndex))?.id ?: v.id else cards[i-columns].id;v.nextFocusDownId=if(i+columns<cards.size)cards[i+columns].id else actions.getOrNull((i%columns).coerceAtMost(actions.lastIndex))?.id ?: v.id}
  for(list in listOf(above,actions))list.forEachIndexed{i,v->v.nextFocusLeftId=if(i==0)navId else list[i-1].id;v.nextFocusRightId=list.getOrNull(i+1)?.id ?: v.id;v.nextFocusUpId=if(list===above)v.id else cards.lastOrNull()?.id ?: above.firstOrNull()?.id ?: navId;v.nextFocusDownId=if(list===above)cards.getOrNull(i)?.id ?: actions.firstOrNull()?.id ?: v.id else v.id}
 }
 private fun settingsPage(){
  heading("Ambient TV settings");place(text("Manage devices, services and photo display",23),244,236,1500,48)
  val groups=listOf(
   "TV & devices" to listOf(Triple("TV & inputs","Configure connected TV",{showPage("TV")}),Triple("Android settings","System settings",{safeStart(Intent(Settings.ACTION_SETTINGS))}),Triple("Android screensaver settings","Display and timeout",{safeStart(Intent(Settings.ACTION_DREAM_SETTINGS))})),
   "Connected services" to listOf(Triple("Connect Plex","Manage your media server",{safeStart(Intent(this,PlexConnectActivity::class.java))}),Triple("Open Projectivy","Launch your TV interface",{launchPackage("com.spocky.projengmenu")}),Triple("Photo connection settings","Synology and album access",{safeStart(Intent(this,SettingsActivity::class.java))})).filter{it.first!="Open Projectivy" || packageManager.getLeanbackLaunchIntentForPackage("com.spocky.projengmenu")!=null},
   "Personalisation" to listOf(Triple("Photo screensaver","Family photos and timing",{safeStart(Intent(this,DreamSettingsActivity::class.java))}),Triple("Status & backup","Connections and local preferences",{safeStart(Intent(this,MaintenanceActivity::class.java))}),Triple("Restore hidden videos","${HomeDismissals.count(this)} hidden from Home",{restoreHiddenVideos()})))
  val controls=mutableMapOf<Pair<Int,Int>,View>()
  groups.forEachIndexed{col,(title,entries)->place(text(title,26),244+col*554,316,530,48);entries.forEachIndexed{row,(label,detail,action)->val v=tile(label+"\n"+detail,action).apply{setTextSize(TypedValue.COMPLEX_UNIT_PX,23*scale);maxLines=3;val copy=android.text.SpannableString(text);val start=label.length+1;copy.setSpan(android.text.style.AbsoluteSizeSpan(p(20)),start,copy.length,0);text=copy};controls[col to row]=v;place(v,244+col*554,382+row*148,530,126)}}
  val navId=focusViews["nav:7"]!!.id
  controls.forEach{(pos,v)->val(c,r)=pos;v.nextFocusLeftId=controls[(c-1) to r]?.id ?: if(c==0)navId else controls[(c-1) to 0]?.id ?: navId;v.nextFocusRightId=controls[(c+1) to r]?.id ?: controls[(c+1) to 0]?.id ?: v.id;v.nextFocusUpId=controls[c to (r-1)]?.id ?: v.id;v.nextFocusDownId=controls[c to (r+1)]?.id ?: v.id}
  focusViews.filterKeys{it.startsWith("nav:")}.values.forEach{it.nextFocusRightId=controls[0 to 0]!!.id}
  place(text("Settings open the existing Android TV, Plex and Synology integrations.",21),244,944,1648,72)
 }
 private fun searchPage(){
  heading("Search");place(text("Find videos on YouTube",23),244,236,1500,48)
  val panel=FrameLayout(this).apply{background=rounded(Color.rgb(11,30,47),false)};place(panel,244,316,1648,350)
  place(text("Find videos",30),280,348,1500,60)
  val input=EditText(this).apply{id=nextId++;hint="Search YouTube…";setTextColor(titleColor);setHintTextColor(Color.LTGRAY);setTextSize(TypedValue.COMPLEX_UNIT_PX,28*scale);setSingleLine(true);imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH;background=rounded(Color.rgb(24,52,77),false);setPadding(p(24),p(10),p(24),p(10))}
  input.setOnFocusChangeListener{_,focused->input.background=rounded(Color.rgb(24,52,77),focused)}
  place(input,280,440,1200,86)
  fun submit(){val query=input.text.toString().trim();if(query.isNotEmpty()){(getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(input.windowToken,0);val target=Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com/results?search_query="+Uri.encode(query))).setPackage("com.google.android.youtube.tv");if(target.resolveActivity(packageManager)!=null)safeStart(target)else launchPackage("com.google.android.youtube.tv")}}
  val submit=tile("Search YouTube"){submit()};place(submit,1500,440,350,86)
  input.setOnEditorActionListener{_,action,event->if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH || event?.keyCode==KeyEvent.KEYCODE_ENTER){if(event==null || event.action==KeyEvent.ACTION_UP)submit();true}else false}
  place(text("Use your remote to type. Results open in YouTube.",24),280,554,1500,58)
  wireGrid(listOf(input,submit),2)
 }
 private fun watchGrid(){
  val scroll=ScrollView(this);val rows=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};scroll.addView(rows)
  val controls=mutableListOf<View>()
  currentWatchItems.chunked(3).forEachIndexed{rowIndex,batch->
   val row=LinearLayout(this).apply{isBaselineAligned=false}
   batch.forEachIndexed{column,item->
    val index=rowIndex*3+column
    val card=tile(item.title+(if(item.nextEpisode)" · Next"else "")){item.open(this)}
    bindWatchOptions(card,item,index);focusViews["watch:$index"]=card;controls.add(card)
    row.addView(card,LinearLayout.LayoutParams(p(450),p(100)).apply{setMargins(0,0,p(20),p(20))})
   };rows.addView(row)
  }
  place(scroll,244,280,1520,700);wireGrid(controls,3)
 }
 private fun actionGrid(actions:List<Pair<String,()->Unit>>) {
  val scroll=ScrollView(this);val rows=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL};scroll.addView(rows)
  val controls=mutableListOf<View>()
  actions.chunked(3).forEach { batch -> val row=LinearLayout(this).apply{isBaselineAligned=false};batch.forEach { (label,action)->row.addView(tile(label,action).also{controls.add(it)},LinearLayout.LayoutParams(p(450),p(100)).apply {setMargins(0,0,p(20),p(20))}) };rows.addView(row) }
  place(scroll,244,280,1520,700);wireGrid(controls,3)
 }
 private fun startScreensaver() {
  val system=Intent().setClassName("com.android.systemui","com.android.systemui.Somnambulator")
  safeStart(if(system.resolveActivity(packageManager)!=null)system else Intent(Settings.ACTION_DREAM_SETTINGS))
 }
 private fun launchPackage(pkg:String) {
  val intent=packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg)
  if(intent!=null)safeStart(intent) else Toast.makeText(this,"This app is not available",Toast.LENGTH_SHORT).show()
 }
 private fun openTv() {
  val intent=Intent(Intent.ACTION_VIEW,android.media.tv.TvContract.Channels.CONTENT_URI)
  if(intent.resolveActivity(packageManager)!=null)safeStart(intent) else apps()
 }
 private fun safeStart(intent:Intent) {try {
  val target=if(intent.action==Settings.ACTION_DREAM_SETTINGS && intent.resolveActivity(packageManager)==null){Toast.makeText(this,"Screen saver controls are in Android settings on this TV",Toast.LENGTH_LONG).show();Intent(Settings.ACTION_SETTINGS)}else intent
  startActivity(target)}catch(_:Exception){Toast.makeText(this,"Unable to open this item",Toast.LENGTH_SHORT).show()} }
 private fun search() {
  val input=EditText(this).apply {hint="Search YouTube";setSingleLine(true)}
  AlertDialog.Builder(this).setTitle("Find videos").setView(input).setPositiveButton("Search YouTube") {_,_->
   val uri=Uri.parse("https://www.youtube.com/results?search_query="+Uri.encode(input.text.toString()))
   val target=Intent(Intent.ACTION_VIEW,uri).setPackage("com.google.android.youtube.tv")
   if(target.resolveActivity(packageManager)!=null)safeStart(target) else launchPackage("com.google.android.youtube.tv")
  }.setNegativeButton("Cancel",null).show()
 }
 private val feedObserver=object:android.database.ContentObserver(handler){override fun onChange(selfChange:Boolean){loadWatchNext()}}
 private val feedReturnFinalRetry=Runnable{if(feedActive && page=="Home")loadWatchNext(true)}
 private val feedReturnRetry=Runnable{if(feedActive && page=="Home")loadWatchNext(true)}
 private fun refreshFeedOnReturn(){
  loadWatchNext(true);handler.removeCallbacks(feedReturnRetry);handler.removeCallbacks(feedReturnFinalRetry)
  if(page=="Home"){handler.postDelayed(feedReturnRetry,5000);handler.postDelayed(feedReturnFinalRetry,15000)}
 }
 private val feedPoll=object:Runnable{override fun run(){if(feedActive){loadWatchNext(true);handler.postDelayed(this,60000)}}}
 private val feedRead:Runnable=Runnable{
  val force=feedState.begin(feedActive && page=="Home")
  if(force!=null){
   val token=epoch
   feedWorker.execute{
    val available=if(previewFiles!=null)emptyList()else WatchNextReader.read(this,force){native->handler.post{if(feedActive && token==epoch && page=="Home" && !feedState.hasPending)applyWatchItems(native)}}
    handler.post{
     val current=feedState.finish()
     if(current && feedActive && token==epoch && page=="Home"){
      applyWatchItems(available)
      if(previewFiles==null)getSharedPreferences("feed-audit",0).edit().putLong("homeReadAt",System.currentTimeMillis()).putBoolean("lastForced",force).putInt("commits",getSharedPreferences("feed-audit",0).getInt("commits",0)+1).apply()
     }
     if(feedActive && feedState.hasPending){handler.removeCallbacks(feedRead);handler.postDelayed(feedRead,750)}
    }
   }
  }
 }
 private fun loadWatchNext(forcePlex:Boolean=false){
  if(page!="Home" || !::mediaHost.isInitialized)return
  feedState.request(forcePlex);handler.removeCallbacks(feedRead);handler.postDelayed(feedRead,750)
 }
 private fun applyWatchItems(source:List<WatchItem>){
  availableWatchItems=source
  val available=source.filterNot{HomeDismissals.hidden(this,it) || (it.owner=="com.google.android.youtube.tv" && HomeDismissalPolicy.completed(it.position,it.duration))}
  // Give Plex three of five slots when its first three include a real next episode.
  val firstOwner=if(available.filter{it.owner=="com.plexapp.android"}.take(3).any{it.nextEpisode})"com.plexapp.android"else null
  val ordered=SourceMixPolicy.indices(available.map{it.owner},available.size,firstOwner).map{available[it]}
  currentWatchItems=ordered;val items=ordered.take(5)
  val signature=items.joinToString("|"){it.stableKey+it.title+it.position+it.duration+it.art+it.engagedAtMillis+it.nextEpisode}
  if(page=="Home")renderWatchItems(items,signature)
  if(previewFiles==null)getSharedPreferences("feed-audit",0).edit().putInt("visiblePlex",items.count{it.owner=="com.plexapp.android"}).putInt("visibleYouTube",items.count{it.owner=="com.google.android.youtube.tv"}).apply()
 }
 private fun renderWatchItems(items:List<WatchItem>,signature:String) {
  if(signature==mediaSignature)return
  val focused=focusViews.entries.firstOrNull{it.key.startsWith("media:") && it.value.hasFocus()}?.key
  val oldIndex=focused?.substringAfter(":")?.toIntOrNull() ?: -1
  val oldKey=displayedWatchItems.getOrNull(oldIndex)?.stableKey
  val newIndex=ResumeFeedPolicy.focusIndex(oldKey,oldIndex,items.map{it.stableKey});displayedWatchItems=items
  mediaSignature=signature;mediaEpoch++;val revision=mediaEpoch;val token=epoch
  fun detach(v:View){if(v is ImageView)v.setImageDrawable(null);if(v is ViewGroup)for(i in 0 until v.childCount)detach(v.getChildAt(i))}
  detach(mediaHost);mediaHost.removeAllViews();mediaBitmaps.forEach{if(!it.isRecycled)it.recycle()};mediaBitmaps.clear();focusViews.keys.filter{it.startsWith("media:")}.toList().forEach{focusViews.remove(it)}
  mediaHeading?.text="Continue Watching  ›"
  fun placeCard(card:View,i:Int){focusViews["media:$i"]=card;mediaHost.addView(card,FrameLayout.LayoutParams(contentPx(HomeLayout.MEDIA_W),p(HomeLayout.MEDIA_H)).apply{leftMargin=contentPx(i*333)})}
  if(items.isEmpty()) {
   val message=if(signature=="loading")"Loading Continue Watching…" else "No continue watching items available. Open Plex or YouTube below."
   mediaHost.addView(text(message,27).apply{setTextColor(Color.rgb(185,204,218));contentDescription=message},FrameLayout.LayoutParams(-1,p(90)).apply{topMargin=p(30)})
  } else items.forEachIndexed{i,item->
   val remaining=if(item.nextEpisode)"Next episode"else if(item.duration>0 && item.position>=0 && item.position<item.duration)"${(item.duration-item.position+59999)/60000} min left"else ""
   val card=photoTile(null,item.title,detail=remaining,media=true){item.open(this)};placeCard(card,i);bindWatchOptions(card,item,i)
   val source=if(item.owner=="com.plexapp.android")"Plex"else"YouTube"
   val watchedProgress=if(item.nextEpisode)null else WatchNextPolicy.progress(item.position,item.duration)
   card.contentDescription=item.title+", "+source+", hold OK for options"+(if(item.nextEpisode)", Next episode"else "")+(watchedProgress?.let{", ${it/10} percent watched"} ?: "")
   val badge=text(if(item.nextEpisode)"Plex · Next"else source,17).apply{gravity=Gravity.CENTER;setPadding(p(7),p(3),p(7),p(3));background=rounded(Color.argb(225,6,16,27),false);try{val icon=packageManager.getApplicationIcon(item.owner);icon.setBounds(0,0,p(18),p(18));compoundDrawablePadding=p(5);setCompoundDrawables(icon,null,null,null)}catch(_:Exception){}}
   card.addView(badge,FrameLayout.LayoutParams(p(if(item.nextEpisode)145 else if(source=="Plex")89 else 126),p(32),Gravity.TOP or Gravity.RIGHT).apply{topMargin=p(9);rightMargin=p(9)})
   val image=card.getChildAt(0) as ImageView
   try{image.setImageDrawable(packageManager.getApplicationIcon(item.owner))}catch(_:Exception){}
   image.scaleType=ImageView.ScaleType.FIT_CENTER;image.setPadding(p(90),p(14),p(90),p(60))
   worker.execute imageJob@{
    if(token!=epoch || revision!=mediaEpoch)return@imageJob
    val bitmap=WatchNextReader.art(this,item.art)
    handler.post{if(token==epoch && revision==mediaEpoch && bitmap!=null){mediaBitmaps.add(bitmap);image.setPadding(0,0,0,0);image.scaleType=ImageView.ScaleType.CENTER_CROP;image.setImageBitmap(bitmap)}else bitmap?.recycle()}
   }
   watchedProgress?.let{actual->card.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{max=1000;progress=actual;progressTintList=android.content.res.ColorStateList.valueOf(if(source=="YouTube")Color.rgb(255,36,62)else Color.rgb(66,205,235));progressBackgroundTintList=android.content.res.ColorStateList.valueOf(Color.rgb(70,78,92))},FrameLayout.LayoutParams(-1,p(4),Gravity.BOTTOM))}
  }
  wireFocus();if(focused!=null)(focusViews["media:$newIndex"] ?: focusViews["footer:0"])?.requestFocus()
 }
 private fun bindWatchOptions(card:View,item:WatchItem,index:Int){
  val press=RemoteCardPress()
  val revision=mediaEpoch
  val hold=Runnable{if(feedActive && revision==mediaEpoch && card.hasFocus() && press.hold())showWatchOptions(item,index)}
  val focus=card.onFocusChangeListener
  card.setOnFocusChangeListener{v,focused->focus?.onFocusChange(v,focused);if(!focused){handler.removeCallbacks(hold);press.cancel()}}
  card.setOnKeyListener{_,code,event->
   if(code !in listOf(KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_NUMPAD_ENTER))false else {
    when(event.action){
     KeyEvent.ACTION_DOWN->{if(press.down(event.repeatCount>0))handler.postDelayed(hold,android.view.ViewConfiguration.getLongPressTimeout().toLong());if(event.isLongPress && press.hold()){handler.removeCallbacks(hold);showWatchOptions(item,index)}}
     KeyEvent.ACTION_UP->{handler.removeCallbacks(hold);if(press.up(event.isCanceled) && feedActive && revision==mediaEpoch)card.performClick()}
    };true
   }
  }
  card.setOnLongClickListener{showWatchOptions(item,index);true}
 }
 private fun refreshAfterDismissal(){applyWatchItems(availableWatchItems);if(page=="Continue Watching")showPage("Continue Watching")}
 private fun focusAfterRemoval(index:Int){
  val key=if(page=="Continue Watching")"watch:${index.coerceAtMost(currentWatchItems.lastIndex)}"else "media:${index.coerceAtMost(displayedWatchItems.lastIndex)}"
  (focusViews[key] ?: focusViews["footer:0"] ?: focusViews["nav:0"])?.requestFocus()
 }
 private fun showWatchOptions(item:WatchItem,index:Int){
  fun hide(){
   val key=HomeDismissals.dismiss(this,item);refreshAfterDismissal()
   val undo=AlertDialog.Builder(this).setTitle("Hidden from Home")
    .setMessage("Your Plex and YouTube history is unchanged.")
    .setPositiveButton("Done",null).setNegativeButton("Undo"){_,_->HomeDismissals.undo(this,key);refreshAfterDismissal()}.create()
   undo.setOnDismissListener{focusAfterRemoval(index)};undo.show()
  }
  val menu=AlertDialog.Builder(this).setTitle(item.title)
   .setMessage("Hide this card from Ambient TV only. Your Plex and YouTube history will not change. You can restore hidden cards in Home settings.")
   .setPositiveButton("Remove"){_,_->hide()}.setNeutralButton("Not interested"){_,_->hide()}.setNegativeButton("Cancel",null).create()
  menu.setOnCancelListener{focusAfterRemoval(index)};menu.show()
 }
 private fun restoreHiddenVideos(){
  AlertDialog.Builder(this).setTitle("Restore hidden videos?")
   .setMessage("Show cards hidden from Ambient TV again. Plex and YouTube history will not change.")
   .setPositiveButton("Restore"){_,_->HomeDismissals.restore(this);showPage("Home")}.setNegativeButton("Cancel",null).show()
 }
 private fun wireFocus(){
  val media=focusViews.keys.count{it.startsWith("media:")}
  for(node in HomeFocusPolicy.nodes(media)){
   val view=focusViews[node.key] ?: continue
   fun id(direction:String)=focusViews[HomeFocusPolicy.target(node,direction,media).key]?.id ?: view.id
   view.nextFocusLeftId=id("left");view.nextFocusRightId=id("right");view.nextFocusUpId=id("up");view.nextFocusDownId=id("down")
  }
 }
 override fun onKeyUp(keyCode:Int,event:KeyEvent):Boolean {if(keyCode==KeyEvent.KEYCODE_ESCAPE){onBackPressed();return true};return super.onKeyUp(keyCode,event)}
 override fun onBackPressed() {if(page!="Home")showPage("Home") else focusViews["nav:0"]?.requestFocus()}
}
