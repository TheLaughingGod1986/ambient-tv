package org.ambienttv.home

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.Context
import android.graphics.*
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
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
 companion object {internal var previewFiles:List<File>?=null;internal var previewWeather:WeatherPanel?=null;internal var previewResolution:Pair<Int,Int>?=null;internal var previewWatch:List<WatchItem>?=null;internal var previewCatalog:((String,Int)->PlexCatalogPage)?=null}
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
  return !loading(root) && (page!="Apps" || appGrid!=null)
 }

 internal fun readyForTest()=photoSlots.size==4 && focusViews.containsKey("footer:2")
 internal fun photoReadyForTest()=photoSlots.count{it.image.hasPhoto}>=minOf(3,photoFiles().size)
 internal fun photoWorkIdleForTest()=worker.activeCount==0 && worker.queue.isEmpty()
 internal fun photoSelectionRunsForTest()=photoSelectionRuns
 @Volatile private var photoSelectionRuns=0
 internal fun refreshDecorForTest(){updateHeader();updateHomePhotos()}
 internal fun boundsForTest()=focusViews.mapValues{(_,view)->Rect().also{view.getDrawingRect(it);root.offsetDescendantRectToMyCoords(view,it)}}
 private val focusViews=linkedMapOf<String,View>()
 private val worker=java.util.concurrent.ThreadPoolExecutor(1,1,0L,java.util.concurrent.TimeUnit.MILLISECONDS,java.util.concurrent.LinkedBlockingQueue<Runnable>())
 private val catalogWorker=java.util.concurrent.ThreadPoolExecutor(1,1,0L,java.util.concurrent.TimeUnit.MILLISECONDS,java.util.concurrent.LinkedBlockingQueue<Runnable>())
 // Keep slow video artwork independent of family-photo decoding and Plex discovery.
 private val artworkWorker=java.util.concurrent.ThreadPoolExecutor(2,2,0L,java.util.concurrent.TimeUnit.MILLISECONDS,java.util.concurrent.LinkedBlockingQueue<Runnable>())
 private val nativeWorker=java.util.concurrent.ThreadPoolExecutor(1,1,0L,java.util.concurrent.TimeUnit.MILLISECONDS,java.util.concurrent.LinkedBlockingQueue<Runnable>())
 // The app listing has its own thread, so it never waits behind photo decoding or a slow Plex server.
 private val appsWorker=java.util.concurrent.ThreadPoolExecutor(1,1,0L,java.util.concurrent.TimeUnit.MILLISECONDS,java.util.concurrent.LinkedBlockingQueue<Runnable>())
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
 private var mediaArtShown=0
 private var mediaEpoch=0
 private val nothingDrawn="\u0000"
 private var mediaSignature=nothingDrawn
 private var currentWatchItems=emptyList<WatchItem>()
 private var availableWatchItems=emptyList<WatchItem>()
 private data class PhotoSlot(val image:OrientationPhotoView,val detail:TextView,val placeholder:ImageView,val title:TextView,val icon:ImageView,val partner:OrientationPhotoView?=null)
 @Volatile private var epoch=0
 private var scale=1f
 private var nextId=1000
 private val titleColor=Color.rgb(240,247,252)
 private val tick=object:Runnable { override fun run() { if(!isFinishing) { updateHeader();if(page=="Home")updateHomePhotos();refreshPhotos();refreshWeather();handler.postDelayed(this,60000-System.currentTimeMillis()%60000) } } }
 /** The store rate-limits itself; a new reading is drawn as soon as it arrives instead of at the next minute. */
 private fun refreshWeather() {
  if(previewFiles==null)WeatherStore.refresh(applicationContext){handler.post{if(!isFinishing && !isDestroyed)updateHeader()}}
 }
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
 private var sidebarScrim:View?=null
 private val navViews=mutableListOf<TextView>()
 private var compactLayout=false
 private var sidebarExpanded:Boolean?=null
 private val sidebarNames=listOf("Home","TV Shows","Movies","Anime","Apps","Photos","Search","Settings")
 private val sidebarUpdate=Runnable{updateSidebar()}
 /** Labels and widths change in one layout pass; after that only the scrim's alpha animates. */
 private fun updateSidebar(){
  if(!::sidebarPanel.isInitialized)return
  val expanded=!compactLayout || navViews.any{it.hasFocus()}
  if(sidebarExpanded==expanded)return
  val first=sidebarExpanded==null;sidebarExpanded=expanded
  val width=p(if(!expanded)64 else if(compactLayout)196 else 180)
  navViews.forEachIndexed{i,v->
   v.text=if(expanded)sidebarNames[i] else "";v.compoundDrawablePadding=if(expanded)p(14)else 0
   if(v.layoutParams.width!=width){v.layoutParams.width=width;v.requestLayout()}
  }
  sidebarScrim?.let{scrim->
   scrim.animate().cancel()
   // Labels appear at once, so the scrim starts mostly opaque and only finishes its fade.
   // ViewPropertyAnimator follows Android's animation scale, including "animations off".
   if(first)scrim.alpha=if(expanded)1f else 0f
   else{if(expanded)scrim.alpha=maxOf(scrim.alpha,.75f);scrim.animate().alpha(if(expanded)1f else 0f).setDuration(120L)}
  }
 }
 private fun contentPx(value:Int)=p(HomeLayout.contentWidth(value,compactLayout))
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
  // The backdrop paints every pixel, so a window or root background would only be drawn over.
  root=FrameLayout(this)
  setContentView(root);window.setBackgroundDrawable(null);root.viewTreeObserver.addOnGlobalFocusChangeListener{_,_->handler.removeCallbacks(sidebarUpdate);handler.post(sidebarUpdate)};showPage("Home")
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
 override fun onResume() { super.onResume();if(compactLayout!=getSharedPreferences("home-ui",0).getBoolean("compactSidebar",false))showPage(page);handler.post(sidebarUpdate);feedActive=true
  // The viewer can remove a favourite; show the Favourites grid as it is now.
  if(resumedOnce && page=="Photos" && galleryFavourites && favourites().map{it.name}!=galleryNames)showPage("Photos")
  if(resumedOnce && page=="Home"){if(mediaArtShown<displayedWatchItems.size)mediaSignature=nothingDrawn;updateHomePhotos()};resumedOnce=true
  if(previewFiles==null && !observingFeed && Build.VERSION.SDK_INT>=26)try{contentResolver.registerContentObserver(android.media.tv.TvContract.WatchNextPrograms.CONTENT_URI,true,feedObserver);observingFeed=true}catch(_:Exception){}
  refreshFeedOnReturn();handler.removeCallbacks(feedPoll);handler.postDelayed(feedPoll,60000)
  handler.removeCallbacks(tick);handler.post(tick)
 }
 override fun onPause() {rememberFocus();feedActive=false;feedSeen=false;feedState.pause();handler.removeCallbacks(feedRead);handler.removeCallbacks(nativeRead);nativeWorker.queue.clear();handler.removeCallbacks(feedPoll);handler.removeCallbacks(feedReturnRetry);handler.removeCallbacks(feedReturnFinalRetry)
  if(observingFeed){contentResolver.unregisterContentObserver(feedObserver);observingFeed=false};handler.removeCallbacks(tick);super.onPause() }
 override fun onTrimMemory(level:Int) {
  super.onTrimMemory(level)
  // Leaving Home drops what can be rebuilt from local data: decoded pictures, and app icons (so
  // an app updated meanwhile shows its new icon). Downloaded artwork is released only under real
  // memory pressure, so the row need not be fetched again on return.
  if(level==android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN){PhotoThumbs.clear();ArtThumbs.clear();AppIcons.clear()}
  else if(level>=android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)trimCaches()
 }
 override fun onLowMemory(){super.onLowMemory();trimCaches()}
 private fun trimCaches(){PlexFeed.trimArtwork();WatchNextReader.trimArtwork();PhotoThumbs.clear();ArtThumbs.clear();AppIcons.clear()}
 override fun onDestroy() { unregisterReceiver(homePress);epoch++;handler.removeCallbacksAndMessages(null);worker.shutdownNow();artworkWorker.shutdownNow();nativeWorker.shutdownNow();catalogWorker.shutdownNow();appsWorker.shutdownNow();feedWorker.shutdownNow();if(::root.isInitialized)clearPage();PhotoThumbs.clear();ArtThumbs.clear();super.onDestroy() }
 private fun clearPage() {
  sidebarScrim?.animate()?.cancel();sidebarScrim=null;navViews.clear();sidebarExpanded=null;epoch++;mediaEpoch++;worker.queue.clear();artworkWorker.queue.clear();nativeWorker.queue.clear();catalogWorker.queue.clear();appsWorker.queue.clear();focusViews.clear();photoSlots.clear();mediaSignature=nothingDrawn
  mediaArtShown=0
  fun detach(view:View){if(view is OrientationPhotoView)view.clear();if(view is ImageView)view.setImageDrawable(null);if(view is android.view.ViewGroup)for(i in 0 until view.childCount)detach(view.getChildAt(i))}
  detach(root)
  // The backdrop stays attached, so its rendered layer survives page changes.
  if(::backdrop.isInitialized && root.childCount>0 && root.getChildAt(0)===backdrop)root.removeViews(1,root.childCount-1)else root.removeAllViews()
 }
 private fun p(v:Int)=(v*scale).toInt()
 private fun place(view:View,x:Int,y:Int,w:Int,h:Int) { val content=x>=244;root.addView(view,FrameLayout.LayoutParams(if(content)contentPx(w)else p(w),p(h)).apply { leftMargin=p(if(content)HomeLayout.contentX(x,compactLayout)else x);topMargin=p(y) }) }
 private fun text(value:String,size:Int=30):TextView=TextView(this).apply { text=value;setTextSize(TypedValue.COMPLEX_UNIT_PX,size*scale);setTextColor(titleColor);gravity=Gravity.CENTER_VERTICAL }
 private val focusedState=intArrayOf(android.R.attr.state_focused)
 private val focusedText=Color.rgb(9,22,36)
 /** Built once per control. Android switches the state itself, so moving focus allocates nothing and needs no layout pass. */
 private fun states(focused:Drawable,resting:Drawable)=StateListDrawable().apply{addState(focusedState,focused);addState(intArrayOf(),resting)}
 private fun ring()=states(rounded(Color.TRANSPARENT,true),rounded(Color.TRANSPARENT,false))
 private fun focusText(resting:Int)=ColorStateList(arrayOf(focusedState,intArrayOf()),intArrayOf(focusedText,resting))
 /** [selected] marks the active choice in a group (for example a filter) while it is not focused. */
 private fun tile(label:String,selected:Boolean=false,action:()->Unit):TextView=text(label,26).apply {
  id=nextId++;isFocusable=true;isFocusableInTouchMode=true;isClickable=true;setPadding(p(20),p(10),p(16),p(10));contentDescription=if(selected)"$label, selected" else label
  background=states(rounded(Color.rgb(228,239,248),true),if(selected)rounded(Color.rgb(34,70,104),false,Color.rgb(150,190,226))else rounded(Color.rgb(17,36,51),false))
  setTextColor(focusText(titleColor))
  // Icons drawn by this app follow the text colour; installed apps keep their own icon colours.
  setOnFocusChangeListener { _,focused -> compoundDrawables.forEach{if(it is HomeNavIcon || it is ActionChevron)it.setTint(if(focused)focusedText else titleColor)} }
  setOnClickListener { action() }
 }
 // One focus language on every screen: a white ring, plus a pale fill on text tiles.
 private fun rounded(color:Int,focus:Boolean,rest:Int=Color.rgb(43,66,82))=GradientDrawable().apply { setColor(color);cornerRadius=14*scale;setStroke(maxOf(1,p(if(focus)4 else 1)),if(focus)Color.WHITE else rest) }
 private fun photoOutline(focused:Boolean)=GradientDrawable().apply{
  setColor(Color.TRANSPARENT);cornerRadius=14*scale
  setStroke(maxOf(1,p(if(focused)4 else 1)),if(focused)Color.WHITE else Color.argb(150,126,168,211))
 }
 private fun dockBackground(focused:Boolean)=GradientDrawable(GradientDrawable.Orientation.TL_BR,
  if(focused)intArrayOf(Color.rgb(235,244,253),Color.rgb(208,226,246))else intArrayOf(Color.rgb(25,61,105),Color.rgb(13,39,72))).apply{
  cornerRadius=14*scale;setStroke(maxOf(1,p(if(focused)4 else 1)),if(focused)Color.WHITE else Color.rgb(82,121,171))
 }
 private val pageFocus=mutableMapOf<String,String>()
 private fun rememberFocus(){focusViews.entries.firstOrNull{it.value.hasFocus() && !it.key.startsWith("nav:")}?.let{pageFocus[page]=it.key}}
 private fun restoreFocus(){val key=pageFocus[page] ?: return;focusViews[key]?.requestFocus()}
 private fun showPage(name:String) {
  rememberFocus()
  if(name!=page && name in listOf("Movies","TV Shows","Anime")){catalogPageIndex=0;catalogFocusAfterLoad=null}
  compactLayout=getSharedPreferences("home-ui",0).getBoolean("compactSidebar",false);page=name;clearPage()
  if(!::backdrop.isInitialized)backdrop=HomeBackdropView(this,true)
  if(backdrop.parent==null)root.addView(backdrop,0,FrameLayout.LayoutParams(-1,-1))
  header=HomeWeatherView(this);place(header,244,38,600,110)
  clock=text("",38).apply { gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL };place(clock,1458,40,430,118);updateHeader()
  val names=sidebarNames
  var first:View?=null
  names.forEachIndexed { index,label ->
   val nav=tile(label){if(label=="Photos")galleryPage(false)else showPage(label)}
   val icon=HomeNavIcon(if(label=="TV Shows")"TV" else label);icon.setBounds(0,0,p(30),p(30));icon.setTint(titleColor)
   nav.setTextSize(TypedValue.COMPLEX_UNIT_PX,22*scale);nav.setPadding(p(17),p(8),p(12),p(8));nav.compoundDrawablePadding=p(14);nav.setCompoundDrawables(icon,null,null,null)
   // The current page is marked quietly, so a pale fill always means "this has focus".
   nav.background=states(rounded(Color.rgb(228,239,248),true),if(label==name)rounded(Color.argb(52,228,239,248),false,Color.argb(84,228,239,248))else ColorDrawable(Color.TRANSPARENT))
   focusViews["nav:$index"]=nav;navViews.add(nav)
   place(nav,24,182+index*84,180,66);if(label==name)first=nav
  }
  navViews.forEachIndexed{i,v->v.nextFocusUpId=navViews[maxOf(0,i-1)].id;v.nextFocusDownId=navViews[minOf(navViews.lastIndex,i+1)].id;v.nextFocusLeftId=v.id}
  when(name) {
   "Home"->home()
   "Continue Watching"->{heading("Continue Watching");if(currentWatchItems.isEmpty())place(text("No continue watching items are available. Open Plex or YouTube from Home.",28),244,280,1500,100)else watchGrid()}
   "Apps"->apps()
   "Photos"->gallery(galleryFavourites)
   "Movies","TV Shows","Anime"->catalog(name,catalogPageIndex)
   "TV"->{
    heading("TV & inputs")
    val actions=mutableListOf<Pair<String,()->Unit>>("Open TV" to {openTv()})
    val inputs=passthroughInputs()
    inputs.forEach{(_,title,intent)->actions.add(title to {safeStart(intent)})}
    actions.add("Android input settings" to {safeStart(Intent("android.settings.TV_INPUT_SETTINGS"))})
    // Two optional Home shortcuts: each press steps to the next input or the next name, so nothing is typed.
    val pins=mutableListOf<Pair<String,()->Unit>>()
    if(inputs.isNotEmpty())for(slot in 0 until HomeShortcuts.SLOTS){
     fun inputLabel()="Home shortcut ${slot+1}: "+(inputs.firstOrNull{it.first==AppPreferences.shortcutInput(this,slot)}?.second ?: "off")
     fun nameLabel()="Shortcut ${slot+1} name: "+AppPreferences.shortcutName(this,slot)
     pins.add(inputLabel() to {AppPreferences.saveShortcut(this,slot,HomeShortcuts.nextInput(AppPreferences.shortcutInput(this,slot),inputs.map{it.first}),AppPreferences.shortcutName(this,slot));(currentFocus as? TextView)?.let{it.text=inputLabel();it.contentDescription=inputLabel()}})
     pins.add(nameLabel() to {AppPreferences.saveShortcut(this,slot,AppPreferences.shortcutInput(this,slot),HomeShortcuts.nextName(AppPreferences.shortcutName(this,slot)));(currentFocus as? TextView)?.let{it.text=nameLabel();it.contentDescription=nameLabel()}})
    }
    actionGrid(actions+pins)
   }
   "Search"->searchPage()
   "Settings"->settingsPage()
  }
  // Elevation keeps the rail above the raised cards; without an outline it casts no shadow.
  sidebarPanel=View(this).apply{elevation=p(8).toFloat();outlineProvider=null;isClickable=true;setBackgroundColor(if(compactLayout)Color.rgb(3,13,25)else Color.argb(220,3,13,25));importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO}
  place(sidebarPanel,0,0,if(compactLayout)96 else 220,1080)
  if(compactLayout)sidebarScrim=SidebarScrim(this).apply{elevation=p(8).toFloat();outlineProvider=null;alpha=0f;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO}.also{place(it,0,0,640,1080)}
  navViews.forEach{it.elevation=p(9).toFloat();it.outlineProvider=null;it.bringToFront()}
  (first ?: focusViews[if(name=="TV")"nav:7"else"nav:0"])?.requestFocus();restoreFocus();updateSidebar()
 }
 private fun heading(value:String) { place(text(value,46).apply{typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)},244,174,1500,65) }
 private val quietColor=Color.rgb(204,216,228)
 private fun subheading(value:String,width:Int=1500) { place(text(value,24).apply{setTextColor(quietColor)},244,236,width,48) }
 private fun updateHeader() {
  if(!::header.isInitialized) return
  // Evening dimming sits over the whole page, focus highlight included.
  val dim=previewFiles==null && DimPolicy.active(System.currentTimeMillis(),AppPreferences.dimEvening(this))
  if(dim!=(root.foreground!=null))root.foreground=if(dim)android.graphics.drawable.ColorDrawable(Color.argb(DimPolicy.ALPHA,0,0,0)) else null
  header.update(previewWeather ?: WeatherStore.panel(this))
  clock.text=run {
   val now=Date();val time=SimpleDateFormat("HH:mm",Locale.getDefault()).format(now);val date=SimpleDateFormat("EEE, d MMM",Locale.getDefault()).format(now)
   android.text.SpannableString("$time\n$date").apply {
    setSpan(android.text.style.RelativeSizeSpan(1.22f),0,time.length,0)
    setSpan(android.text.style.StyleSpan(Typeface.BOLD),0,time.length,0)
    setSpan(android.text.style.RelativeSizeSpan(.72f),time.length+1,length,0)
   }
  }
 }
 private fun headingAction(value:String,y:Int,key:String,action:()->Unit):TextView {
  val view=text(value,if(key=="heading:0")42 else 32).apply {
   typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);id=nextId++;isFocusable=true;isFocusableInTouchMode=true;isClickable=true
   contentDescription=if(key=="heading:0")"Browse your photos" else "Browse Continue Watching"
   setOnClickListener{action()};background=states(rounded(Color.TRANSPARENT,true),ColorDrawable(Color.TRANSPARENT))
  }
  focusViews[key]=view;place(view,244,y,if(key=="heading:0")550 else 650,52);return view
 }
 private fun home() {
  headingAction("Your Photos",148,"heading:0"){galleryPage(false)}
  place(text("Family memories, all in one place",22).apply{setTextColor(Color.rgb(218,228,239))},244,204,900,28)
  val titles=listOf("Featured memory","Browse photos","Favourites","All Photos")
  val icons=listOf("Recent","Photos","Favourites","All Photos")
  val actions=listOf<()->Unit>(
   {photoFiles().firstOrNull()?.let{openPhoto(it)} ?: safeStart(Intent(this,SettingsActivity::class.java))},
   {galleryPage(false)},{galleryPage(true)},{galleryPage(false)})
  for(i in titles.indices) {
   val card=ClippedPhotoCard(this,14*scale).apply{id=nextId++;isFocusable=true;isFocusableInTouchMode=true;isClickable=true;background=rounded(Color.rgb(17,35,49),false);foreground=states(photoOutline(true),photoOutline(false));contentDescription=titles[i];setOnClickListener{actions[i]()}}
   val image=OrientationPhotoView(this,true);card.addView(image,FrameLayout.LayoutParams(if(i<2)-1 else p(300),-1,Gravity.RIGHT))
   // The widest tile can hold a second upright photo beside the first; hidden until one is paired.
   val partner=if(i==0)OrientationPhotoView(this,true).apply{visibility=View.GONE}.also{card.addView(it,FrameLayout.LayoutParams(p(HomeLayout.PAIR_W),-1,Gravity.RIGHT))} else null
   val placeholder=ImageView(this).apply{setImageDrawable(HomeNavIcon(icons[i]).apply{setTint(Color.rgb(164,191,210))})};card.addView(placeholder,if(i<2)FrameLayout.LayoutParams(p(70),p(70),Gravity.CENTER).apply{bottomMargin=p(30)}else FrameLayout.LayoutParams(p(70),p(70),Gravity.RIGHT or Gravity.CENTER_VERTICAL).apply{rightMargin=p(115)})
   card.addView(View(this).apply{background=GradientDrawable(if(i<2)GradientDrawable.Orientation.TOP_BOTTOM else GradientDrawable.Orientation.RIGHT_LEFT,if(i<2)intArrayOf(Color.TRANSPARENT,Color.argb(242,4,10,18))else intArrayOf(Color.TRANSPARENT,Color.argb(180,15,32,54),Color.rgb(15,32,54)))},FrameLayout.LayoutParams(-1,if(i<2)p(170)else -1,Gravity.BOTTOM))
   val caption=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(p(20),0,p(16),p(16))}
   val icon=ImageView(this).apply{setImageDrawable(HomeNavIcon(icons[i]).apply{setTint(if(i==2)Color.rgb(255,210,70)else Color.WHITE)})};caption.addView(icon,LinearLayout.LayoutParams(p(30),p(30)).apply{rightMargin=p(12)})
   val words=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
   val title=text(titles[i],26).apply{typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END};words.addView(title)
   val detail=text("",18).apply{setTextColor(Color.rgb(204,216,228));maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END};words.addView(detail)
   caption.addView(words,LinearLayout.LayoutParams(0,-2,1f));caption.addView(text("›",44).apply{gravity=Gravity.CENTER;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO},LinearLayout.LayoutParams(p(34),p(56)));card.addView(caption,FrameLayout.LayoutParams(-1,p(94),if(i<2)Gravity.BOTTOM else Gravity.CENTER_VERTICAL))
   photoSlots.add(PhotoSlot(image,detail,placeholder,title,icon,partner));focusViews["photo:$i"]=card
   place(card,HomeLayout.photoX(i),HomeLayout.photoY(i),HomeLayout.photoWidth(i),HomeLayout.photoHeight(i))
  }
  updateHomePhotos()
  mediaHeading=headingAction("Continue Watching  ›",HomeLayout.MEDIA_HEADING_Y,"heading:1"){showPage("Continue Watching")}
  mediaHost=FrameLayout(this);place(mediaHost,HomeLayout.LEFT,HomeLayout.MEDIA_Y,HomeLayout.RIGHT-HomeLayout.LEFT,HomeLayout.MEDIA_H)
  // Coming back to Home shows the last feed on the next frame, so the page itself appears first;
  // the refresh started below replaces the row only if something changed.
  if(feedSeen){val token=epoch;handler.post{if(feedActive && token==epoch && page=="Home" && !isDestroyed)applyWatchItems(availableWatchItems)}}
  else renderWatchItems(emptyList(),"loading")
  place(text("Quick access",32).apply{typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)},244,600,800,48)
  val dock=mutableListOf<DockItem>()
  for((label,pkg,subtitle) in listOf(Triple("YouTube","com.google.android.youtube.tv","Watch videos together"),Triple("Plex","com.plexapp.android","Movies & TV shows"),Triple("Photos","com.synology.projectkailash","Open Synology Photos")))
   dock.add(DockItem(label,subtitle,AppIcons.load(this,pkg) ?: HomeNavIcon("Apps").apply{setTint(titleColor)}){launchPackage(pkg)})
  // Inputs the owner pinned in TV & inputs follow the apps; a missing input is simply left out.
  val inputs=passthroughInputs()
  for(slot in 0 until HomeShortcuts.SLOTS){
   val input=inputs.firstOrNull{it.first==AppPreferences.shortcutInput(this,slot)} ?: continue
   dock.add(DockItem(AppPreferences.shortcutName(this,slot),input.second,HomeNavIcon("Game").apply{setTint(titleColor)}){safeStart(input.third)})
  }
  dock.forEachIndexed{i,item->
   val view=tile(item.label){item.action()}
   view.background=states(dockBackground(true),dockBackground(false))
   focusViews["footer:$i"]=view;place(view,HomeShortcuts.tileX(i,dock.size),HomeLayout.DOCK_Y,HomeShortcuts.tileWidth(dock.size),HomeLayout.DOCK_H)
   val icon=item.icon;val edge=if(dock.size>4)52f else 64f
   val longest=maxOf(icon.intrinsicWidth,icon.intrinsicHeight,1);icon.setBounds(0,0,p((edge*icon.intrinsicWidth.coerceAtLeast(1)/longest).toInt()),p((edge*icon.intrinsicHeight.coerceAtLeast(1)/longest).toInt()))
   view.setPadding(p(18),p(8),p(12),p(8));view.compoundDrawablePadding=p(if(dock.size>4)14 else 20);val chevron=ActionChevron().apply{setTint(titleColor);setBounds(0,0,p(24),p(30))};view.setCompoundDrawables(icon,null,chevron,null)
   // Five tiles leave room for the name only.
   view.text=if(dock.size>4)android.text.SpannableString(item.label).apply{setSpan(android.text.style.StyleSpan(Typeface.BOLD),0,length,0)} else android.text.SpannableString("${item.label}\n${item.subtitle}").apply{
    setSpan(android.text.style.StyleSpan(Typeface.BOLD),0,item.label.length,0)
    setSpan(android.text.style.RelativeSizeSpan(.76f),item.label.length+1,length,0)
   }
   view.contentDescription=item.label+", "+item.subtitle
   view.setTextSize(TypedValue.COMPLEX_UNIT_PX,26*scale);view.maxLines=2;view.ellipsize=android.text.TextUtils.TruncateAt.END
  }
  wireFocus();loadWatchNext()
 }
 private class DockItem(val label:String,val subtitle:String,val icon:android.graphics.drawable.Drawable,val action:()->Unit)
 /** The TV's own external inputs as (id, label, intent that switches to it). Empty when the TV has none. */
 private fun passthroughInputs():List<Triple<String,String,Intent>> = if(Build.VERSION.SDK_INT<24)emptyList() else try {
  val manager=getSystemService(android.content.Context.TV_INPUT_SERVICE) as android.media.tv.TvInputManager
  manager.tvInputList.filter{it.isPassthroughInput}.mapNotNull{input->
   val intent=Intent(Intent.ACTION_VIEW,android.media.tv.TvContract.buildChannelUriForPassthroughInput(input.id))
   if(intent.resolveActivity(packageManager)==null)null else Triple(input.id,input.loadLabel(this)?.toString()?.takeIf{it.isNotBlank()} ?: "TV input",intent)
  }.sortedBy{it.second}
 }catch(_:Exception){emptyList()}
 private var photoSelectionKey=""
 private var selectedPhotoIds=emptyList<String?>()
 private var photoBoundEpoch=-1
 private val photoRatios=mutableMapOf<String,Float>()
 private val photoSignatures=mutableMapOf<String,IntArray>() // Only accessed by the image worker.
 private fun updateHomePhotos() {
  if(page!="Home" || photoSlots.size!=4)return
  val token=epoch
  worker.execute {
   if(token!=epoch)return@execute
   // Listing the photo cache and reading favourites touch storage, so they stay off the UI thread.
   val files=photoFiles();val favs=favourites(files)
   val dayKey=HomePhotoPolicy.localDay(System.currentTimeMillis(),TimeZone.getTimeZone("Europe/London"))
   val dailyPrefs=getSharedPreferences("home-daily",0)
   val selectionKey="$dayKey:${dailyPrefs.getLong("row-day",Long.MIN_VALUE)}:${dailyPrefs.getInt("row-version",0)}:"+files.joinToString{it.name+":"+it.length()+":"+it.lastModified()}+":"+favs.joinToString{it.name}
   if(selectionKey==photoSelectionKey){
    if(photoBoundEpoch!=token){
     val again=selectedPhotoIds.map{id->files.firstOrNull{it.name.substringAfter('-')==id}};val extras=homeExtras(again,files,dayKey)
     handler.post{if(token==epoch && page=="Home"){bindHomePhotos(again,files.size,extras);photoBoundEpoch=token}}
    }
    return@execute
   }
   photoSelectionRuns++
   // Header-only decoding is bounded by the cached pool and never blocks TV focus/clock updates.
   fun metadataKey(file:File)="${file.path}:${file.length()}:${file.lastModified()}"
   photoRatios.keys.retainAll(files.map(::metadataKey).toSet())
   val ratios=files.associateWith { file->photoRatios.getOrPut(metadataKey(file)){
    val bounds=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true}
    android.graphics.BitmapFactory.decodeFile(file.path,bounds)
    if(bounds.outWidth>0 && bounds.outHeight>0)bounds.outWidth.toFloat()/bounds.outHeight else 0f
   }}
   fun key(file:File)="${file.path}:${file.length()}:${file.lastModified()}"
   val activeKeys=files.map{key(it)}.toSet();photoSignatures.keys.retainAll(activeKeys)
   fun signature(file:File):IntArray? {
    if(token!=epoch)return null // the page changed: this selection's result is discarded, so stop sampling
    val cacheKey=key(file);photoSignatures[cacheKey]?.let{return it}
    val image=try{PhotoDecoding.load(file,64,64)}catch(_:Exception){null} ?: return null
    val tiny=Bitmap.createScaledBitmap(image,12,12,true)
    val pixels=IntArray(144);tiny.getPixels(pixels,0,12,0,0,12,12)
    if(tiny!==image)tiny.recycle();image.recycle();photoSignatures[cacheKey]=pixels;return pixels
   }
   val day=HomePhotoPolicy.localDay(System.currentTimeMillis(),TimeZone.getTimeZone("Europe/London"))
   val prefs=getSharedPreferences("home-daily",0)
   val same=prefs.getLong("row-day",Long.MIN_VALUE)==day && prefs.getInt("row-version",0)==7
   fun ids(key:String)=prefs.getString(key,"").orEmpty().split(',')
   fun id(file:File)=file.name.substringAfter('-')
   val saved=ids("row-ids")
   val yesterday=if(same)ids("previous-row")else saved
   var history=RecentPhotoHistory.read(prefs.getString("recent-rows","").orEmpty())
   val oldDay=prefs.getLong("row-day",Long.MIN_VALUE)
   if(oldDay in day-7..day)history=RecentPhotoHistory.remember(history,oldDay,saved)
   val recent=(history.toSortedMap(reverseOrder()).values.flatten()+yesterday).distinct()
   val times=try{org.json.JSONObject(getSharedPreferences("state",0).getString("photoTimes","{}"))}catch(_:Exception){org.json.JSONObject()}
   fun related(a:File,b:File):Boolean {
    if(id(a)==id(b))return true
    val ta=PhotoCaption.seconds(a.name,times);val tb=PhotoCaption.seconds(b.name,times)
    if(ta>0 && tb>0 && kotlin.math.abs(ta-tb)<1800)return true
    return signature(a)?.let{x->signature(b)?.let{y->HomePhotoSimilarity.similar(x,y)}}==true
   }
   val picked=DailyHomeSelection.select(files.map(::id),favs.map(::id).toSet(),day,
    if(same)saved else emptyList(),recent,
    {a,b->related(files.first{ id(it)==a },files.first{ id(it)==b })},
    {id,slot->val file=files.first{ id(it)==id };val target=if(slot>=2)245f/170f else HomeLayout.photoWidth(slot).toFloat()/HomeLayout.photoHeight(slot)
     val ratio=ratios[file] ?: 1f;kotlin.math.abs(kotlin.math.ln(ratio.coerceAtLeast(.01f)/target)).toDouble()},
    {a,b->val x=signature(files.first{id(it)==a});val y=signature(files.first{id(it)==b});if(x==null || y==null)1.0 else HomePhotoSimilarity.colourDistance(x,y)})
   if(token!=epoch)return@execute
   // A photo taken on this day in an earlier year leads the row, in place of whatever was chosen there.
   val nowMillis=System.currentTimeMillis()
   val anniversary=files.firstOrNull{PhotoCaption.yearsAgoToday(PhotoCaption.seconds(it.name,times),nowMillis)!=null}?.let(::id)
   val selected=if(anniversary==null || picked.firstOrNull()==anniversary)picked else picked.mapIndexed{slot,key->if(slot==0)anniversary else if(key==anniversary)picked[0] else key}
   photoSelectionKey=selectionKey;selectedPhotoIds=selected
   if(previewFiles==null)prefs.edit().putInt("row-version",7).putLong("row-day",day).putString("row-ids",selected.joinToString(","){it.orEmpty()}).putString("previous-row",yesterday.joinToString(",")).putString("recent-rows",RecentPhotoHistory.encode(RecentPhotoHistory.remember(history,day,selected))).apply()
   val selectedFiles=selected.map{key->files.firstOrNull{id(it)==key}}
   val extras=homeExtras(selectedFiles,files,day)
   handler.post {if(token==epoch && page=="Home" && !isDestroyed){bindHomePhotos(selectedFiles,files.size,extras);photoBoundEpoch=token}}
  }
 }
 /** Worker thread only: the featured photo's real month, and a spare photo for the third tile. */
 private class HomeExtras(val featuredDate:String?,val spare:File?,val spareDate:String?,val partner:File?)
 private fun homeExtras(selected:List<File?>,files:List<File>,day:Long):HomeExtras {
  val times=try{org.json.JSONObject(getSharedPreferences("state",0).getString("photoTimes","{}").orEmpty())}catch(_:Exception){org.json.JSONObject()}
  val now=System.currentTimeMillis();val birth=AppPreferences.birthdaySeconds(this)
  fun caption(file:File?)=file?.let{PhotoCaption.headline(PhotoCaption.seconds(it.name,times),now,birth)}
  val spare=if(selected.getOrNull(2)!=null)null else PhotoCaption.extra(files.map{it.name},selected.map{it?.name},day)?.let{name->files.firstOrNull{it.name==name}}
  // An upright featured photo is paired with another upright one so the wide tile is filled by both.
  fun upright(file:File):Boolean {val o=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true};android.graphics.BitmapFactory.decodeFile(file.path,o);return o.outWidth>0 && o.outHeight>0 && o.outWidth<o.outHeight*HomePhotoPolicy.UPRIGHT}
  val featured=selected.getOrNull(0)
  val partner=if(featured==null || !upright(featured))null else {
   val shown=selected.map{it?.name}+spare?.name
   var left=files.map{it.name}
   var found:File?=null
   // Step through the day's stable order until an upright photo turns up; a small pool may have none.
   repeat(minOf(files.size,12)){if(found==null){val name=PhotoCaption.extra(left,shown,day+1);val file=files.firstOrNull{it.name==name};if(file==null)return@repeat;if(upright(file))found=file else left=left-file.name}}
   found
  }
  return HomeExtras(caption(featured),spare,caption(spare),partner)
 }
 private fun bindHomePhotos(chosen:List<File?>,photoCount:Int,extras:HomeExtras) {
  val favourite=chosen[2]
  // With nothing favourited yet the third tile shows one more memory under its own name, never as a favourite.
  val spare=if(favourite==null)extras.spare else null
  val selected=chosen.toMutableList().also{if(spare!=null)it[2]=spare}
  val titles=listOf("Featured memory","Browse photos",if(spare!=null)"Another memory" else "Favourites","All Photos")
  val descriptions=listOf(if(photoCount==0)"No cached photos" else extras.featuredDate ?: "Our favourite moments","All your family memories",
   if(spare!=null)extras.spareDate ?: "Open to add it to favourites" else if(favourite==null)"No favourites yet" else "Saved by you","$photoCount photos")
  fun load(view:OrientationPhotoView,file:File?,wide:Boolean,placeholder:View?) {
   if(!view.request(file?.name))return
   if(file==null){view.show(null,null);return}
   // The wide cards need more pixels than the small stacked pair.
   val maxWidth=if(wide)1024 else 512;val maxHeight=if(wide)1080 else 512
   val key=PhotoThumbs.key(file,maxWidth,maxHeight)
   PhotoThumbs.get(key)?.let{saved->view.show(saved.photo,saved.fill,saved.face);return}
   val token=epoch;val request=view.requestVersion
   worker.execute {
    if(token!=epoch || view.requestVersion!=request)return@execute
    val entry=decodeThumb(file,maxWidth,maxHeight,faces=true)?.also{PhotoThumbs.put(key,it)}
    handler.post{if(token==epoch && view.requestVersion==request && !isDestroyed){view.show(entry?.photo,entry?.fill,entry?.face);placeholder?.visibility=if(entry==null)View.VISIBLE else View.GONE}}
   }
  }
  photoSlots.forEachIndexed{i,slot->
   slot.title.text=titles[i];slot.detail.text=descriptions[i];focusViews["photo:$i"]?.contentDescription=titles[i]+", "+descriptions[i]
   if(i==2){
    slot.icon.setImageDrawable(HomeNavIcon(if(spare!=null)"Recent" else "Favourites").apply{setTint(if(spare!=null)Color.WHITE else Color.rgb(255,210,70))})
    focusViews["photo:2"]?.setOnClickListener{if(spare!=null)openPhoto(spare) else galleryPage(true)}
   }
   val file=selected[i];if(i==0)focusViews["photo:0"]?.setOnClickListener{file?.let{openPhoto(it)} ?: galleryPage(false)};slot.placeholder.visibility=if(file==null)View.VISIBLE else View.GONE
   slot.partner?.let{partner->
    val pair=if(file!=null)extras.partner else null
    // Paired: two equal halves with a slim gap. Alone: the photo takes the whole tile as before.
    val width=if(pair!=null)p(HomeLayout.PAIR_W) else -1
    if(slot.image.layoutParams.width!=width)slot.image.layoutParams=FrameLayout.LayoutParams(width,-1,Gravity.LEFT)
    partner.visibility=if(pair!=null)View.VISIBLE else View.GONE
    load(partner,pair,true,null)
   }
   load(slot.image,file,i<2,slot.placeholder)
  }
 }
 /** Worker thread only. A detected face lets a Home tile fill its frame without cutting that face. */
 private fun decodeThumb(file:File,maxWidth:Int,maxHeight:Int,faces:Boolean):ThumbEntry? {
  val bitmap=try{PhotoDecoding.load(file,maxWidth,maxHeight)}catch(_:Exception){null} ?: return null
  val face=if(faces)HeroComposition.detect(bitmap)else null
  return ThumbEntry(bitmap,OrientationPhotoView.softFill(bitmap),face)
 }
 /** Continue Watching tile: artwork fills the card; title and remaining time sit on a bottom fade. */
 private fun mediaCard(label:String,detail:String,action:()->Unit):FrameLayout {
  val card=ClippedPhotoCard(this,14*scale).apply { id=nextId++;isFocusable=true;isFocusableInTouchMode=true;isClickable=true;contentDescription=label;background=rounded(Color.rgb(16,35,48),false);foreground=ring() }
  val image=ImageView(this).apply { scaleType=ImageView.ScaleType.CENTER_CROP }
  card.addView(image,FrameLayout.LayoutParams(-1,-1))
  card.addView(View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(Color.TRANSPARENT,Color.argb(235,1,8,14)))},FrameLayout.LayoutParams(-1,p(138),Gravity.BOTTOM))
  val captions=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(p(16),0,p(12),p(14));gravity=Gravity.BOTTOM}
  captions.addView(text(label,20).apply{typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END})
  if(detail.isNotBlank())captions.addView(text(detail,18).apply{setTextColor(Color.LTGRAY);maxLines=1})
  card.addView(captions,FrameLayout.LayoutParams(-1,p(100),Gravity.BOTTOM))
  card.setOnClickListener { action() }
  return card
 }
 /** Shared Plex tile: a poster-shaped card showing the whole poster above a separate two-line title. */
 private fun libraryCard(label:String,action:()->Unit):FrameLayout {
  val card=ClippedPhotoCard(this,14*scale).apply {
   id=nextId++;isFocusable=true;isFocusableInTouchMode=true;isClickable=true
   contentDescription=label;background=rounded(Color.rgb(12,29,47),false);foreground=ring()
   setOnClickListener{action()}
  }
  card.addView(OrientationPhotoView(this).apply{importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO},FrameLayout.LayoutParams(-1,p(HomeLayout.POSTER_H)))
  card.addView(ImageView(this).apply{setImageDrawable(HomeNavIcon("Movies").apply{setTint(Color.rgb(96,124,150))});importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO},FrameLayout.LayoutParams(p(56),p(56),Gravity.CENTER_HORIZONTAL).apply{topMargin=p((HomeLayout.POSTER_H-56)/2)})
  card.addView(text(label,19).apply {
   maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END
   gravity=Gravity.CENTER_VERTICAL;setPadding(p(10),0,p(10),0)
   typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
  },FrameLayout.LayoutParams(-1,p(HomeLayout.LIBRARY_CARD_H-HomeLayout.POSTER_H),Gravity.BOTTOM))
  return card
 }
 private fun catalog(category:String,index:Int){
  heading(category)
  val loading=text("Loading your Plex library…",28);place(loading,244,248,1500,68)
  val token=epoch;val refresh=catalogRefresh;catalogRefresh=false
  catalogWorker.execute{
   if(token!=epoch)return@execute
   try{
    val preview=previewCatalog
    val result=preview?.invoke(category,index) ?: PlexFeed.catalog(this,category,index,refresh)
    handler.post{
     if(token!=epoch || isFinishing)return@post
     root.removeView(loading)
     subheading("Latest additions · Plex"+if(index>0)" · Page ${index+1}" else "")
     val gridControls=mutableListOf<View>()
     result.items.forEachIndexed{position,item->
      val card=libraryCard(item.title){WatchItem(item.title,"com.plexapp.android",PlexProtocol.detailUri(item.server,item.key),"",-1,-1).open(this)}
      gridControls.add(card)
      place(card,HomeLayout.libraryX(position%HomeLayout.LIBRARY_COLUMNS),HomeLayout.LIBRARY_Y+(position/HomeLayout.LIBRARY_COLUMNS)*(HomeLayout.LIBRARY_CARD_H+HomeLayout.GAP),HomeLayout.LIBRARY_CARD_W,HomeLayout.LIBRARY_CARD_H)
      val poster=card.getChildAt(0) as OrientationPhotoView;val placeholder=card.getChildAt(1)
      poster.request(item.key);val version=poster.requestVersion
      val art=if(preview!=null)item.thumb else "plex-art://${item.server}/${item.key}"
      val saved=ArtThumbs.get("poster:$art")
      if(saved!=null){poster.show(saved.photo,saved.fill);placeholder.visibility=View.GONE}
      else if(item.thumb.isNotBlank())artworkWorker.execute imageJob@{
       if(token!=epoch)return@imageJob
       val entry=WatchNextReader.art(this,art)?.let{ThumbEntry(it,OrientationPhotoView.softFill(it))}?.also{ArtThumbs.put("poster:$art",it)}
       handler.post{if(token==epoch && poster.requestVersion==version && entry!=null){poster.show(entry.photo,entry.fill);placeholder.visibility=View.GONE}}
      }
     }
     val navId=focusViews["nav:"+when(category){"TV Shows"->1;"Movies"->2;else->3}]?.id ?: View.NO_ID
     if(result.items.isEmpty())place(text(if(category=="Anime")"No Anime library, Anime genre, or Japanese animation was found." else "No items found in this Plex category.",28),244,340,1500,110)
     // Buttons sit left to right from the content edge, whichever of them are present.
     val pager=mutableListOf<View>()
     fun pageButton(label:String,action:()->Unit){val v=tile(label,action=action);place(v,244+pager.size*300,HomeLayout.PAGER_Y,280,72);pager.add(v)}
     if(index>0)pageButton("Previous page"){showCatalogPage(category,index-1)}
     if(result.hasMore)pageButton("Next page"){showCatalogPage(category,index+1)}
     pageButton("Refresh"){showCatalogPage(category,index,refresh=true)}
     wireGrid(gridControls,HomeLayout.LIBRARY_COLUMNS,pager)
     // After paging or Refresh, focus returns to the same button when the new page still has it,
     // so Next can be pressed again; otherwise to the first poster. Focus that moved on is left alone.
     val desired=catalogFocusAfterLoad;catalogFocusAfterLoad=null
     if(desired==null)restoreFocus()
     else if(root.findFocus()?.id==navId)((gridControls+pager).firstOrNull{it.contentDescription?.toString()==desired} ?: gridControls.firstOrNull() ?: pager.firstOrNull())?.requestFocus()
    }
   }catch(_:Exception){handler.post{if(token==epoch){loading.text="Could not load this Plex library. Check your server connection.";val retry=tile("Retry"){showCatalogPage(category,index)};val connect=tile("Plex connection"){safeStart(Intent(this,PlexConnectActivity::class.java))};place(retry,244,360,300,80);place(connect,570,360,400,80);wireGrid(listOf(retry,connect),2)}}}
  }
 }
 private var catalogPageIndex=0
 private var catalogFocusAfterLoad:String?=null
 private var catalogRefresh=false
 private fun showCatalogPage(category:String,index:Int,refresh:Boolean=false){catalogRefresh=refresh;catalogFocusAfterLoad=root.findFocus()?.contentDescription?.toString() ?: "@first";catalogPageIndex=index;showPage(category)}
 private fun galleryPage(favs:Boolean) { galleryFavourites=favs;galleryIndex=0;showPage("Photos") }
 private var galleryFavourites=false
 private var galleryIndex=0
 private var galleryFocusLabel:String?=null
 private var galleryNames=emptyList<String>()
 private fun gallery(favs:Boolean) {
  heading(if(favs)"Favourites" else "Your photos")
  val files=if(favs)favourites() else photoFiles()
  galleryIndex=galleryIndex.coerceIn(0,maxOf(0,(files.size-1)/8));val pages=(files.size+7)/8
  subheading((if(favs)"Saved by you · ${files.size} "+(if(files.size==1)"photo" else "photos") else "Family memories · ${files.size} photos")+if(pages>1)" · Page ${galleryIndex+1} of $pages" else "",1050)
  val chips=listOf(tile("Favourites",selected=favs){galleryPage(true)},tile("All",selected=!favs){galleryPage(false)})
  chips.forEachIndexed{i,v->place(v,1530+i*184,236,174,58)}
  if(files.isEmpty()) place(text(if(favs)"Open a photo to add it to your favourites." else "No cached photos yet. Open connection settings.",28),244,340,1450,90)
  val cards=files.drop(galleryIndex*8).take(8).mapIndexed{i,file->
   val card=ClippedPhotoCard(this,14*scale).apply{id=nextId++;isFocusable=true;isFocusableInTouchMode=true;isClickable=true;contentDescription="View photo ${galleryIndex*8+i+1} of ${files.size}";foreground=ring()}
   val image=OrientationPhotoView(this,enlarge=true);card.addView(image,FrameLayout.LayoutParams(-1,-1))
   card.setOnClickListener{openPhoto(file)}
   bindGalleryPhoto(image,file);place(card,244+(i%4)*416,316+(i/4)*300,396,280);card
  }
  // Actions sit left to right from the content edge, whichever of them are present.
  val actions=mutableListOf<View>();var x=244
  fun action(label:String,width:Int,run:()->Unit){val v=tile(label,action=run);place(v,x,HomeLayout.PAGER_Y,width,72);x+=width+20;actions.add(v)}
  if(files.isEmpty() && !favs)action("Photo connection settings",440){safeStart(Intent(this,SettingsActivity::class.java))}
  action("Open Synology Photos",400){launchPackage("com.synology.projectkailash")}
  if(favs)action("Browse all photos",340){galleryPage(false)}
  if(galleryIndex>0)action("Previous page",280){galleryIndex--;galleryFocusLabel="Previous page";showPage("Photos")}
  if((galleryIndex+1)*8<files.size)action("Next page",280){galleryIndex++;galleryFocusLabel="Next page";showPage("Photos")}
  wireGrid(cards,4,actions,chips)
  galleryNames=files.map{it.name}
  // A remembered card that no longer exists (a favourite was removed) falls back to the last card.
  pageFocus["Photos"]?.let{key->if(key.startsWith("grid:") && (key.substringAfter(':').toIntOrNull() ?: 0)>=cards.size)pageFocus["Photos"]=if(cards.isEmpty())"filter:0" else "grid:${cards.lastIndex}"}
  // After paging, focus stays on the same button while the new page still has it (so it can be
  // pressed again), otherwise it moves to the first photo. showPage restores this key afterwards.
  galleryFocusLabel?.let{label->val same=actions.indexOfFirst{it.contentDescription?.toString()==label};pageFocus["Photos"]=if(same>=0)"action:$same" else "grid:0"}
  galleryFocusLabel=null
 }
 private fun bindGalleryPhoto(view:OrientationPhotoView,file:File){
  val token=epoch;view.request(file.name);val version=view.requestVersion
  val key=PhotoThumbs.key(file,800,600)
  PhotoThumbs.get(key)?.let{saved->view.show(saved.photo,saved.fill);return}
  worker.execute{
   if(token!=epoch)return@execute
   val entry=decodeThumb(file,800,600,faces=false)?.also{PhotoThumbs.put(key,it)}
   handler.post{if(token==epoch && view.requestVersion==version)view.show(entry?.photo,entry?.fill)}
  }
 }
 private fun favourites(files:List<File> = photoFiles()):List<File> { if(previewFiles!=null)return emptyList();val ids=getSharedPreferences("favourites",0).getStringSet("ids",emptySet()) ?: emptySet();return files.filter { it.name.substringAfter('-') in ids } }
 private fun openPhoto(file:File) {safeStart(Intent(this,PhotoViewerActivity::class.java).putExtra("photo",file.name))}
 private data class AppEntry(val pkg:String,val label:String,val iconKey:String)
 private var appEntries:List<AppEntry>?=null
 private var appGrid:View?=null
 private var appsLoading:View?=null
 private fun apps() {
  heading("Apps");subheading("Installed applications · select an app to open")
  appGrid=null;appsLoading=null
  // The last listing is shown at once, but only while every icon for it is still in memory.
  val known=appEntries
  if(known!=null && known.all{AppIcons.has(it.iconKey)})showApps(known)
  else appsLoading=text("Loading apps…",28).apply{setTextColor(quietColor)}.also{place(it,244,306,1500,68)}
  val token=epoch
  appsWorker.execute {
   if(token!=epoch)return@execute
   val pm=packageManager
   // This listing carries its own icons, so a cache trim while it loads cannot leave tiles without one.
   val icons=HashMap<String,Drawable.ConstantState>()
   val entries=try{pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),0)}catch(_:Exception){emptyList()}
    .distinctBy{it.activityInfo.packageName}.map{info->
     val key="app:"+info.activityInfo.packageName
     (AppIcons.state(key) ?: try{val icon:Drawable?=info.loadIcon(pm);icon?.let{AppIcons.remember(key,it)}}catch(_:Exception){null})?.let{icons[key]=it}
     AppEntry(info.activityInfo.packageName,info.loadLabel(pm).toString(),key)
    }.sortedBy{it.label}
   handler.post{
    if(token==epoch && !isDestroyed && page=="Apps" && (entries!=appEntries || appGrid==null)){
     appEntries=entries;showApps(entries,icons)
     // A late listing still returns focus to the remembered app, unless the user has moved on.
     if(root.findFocus()?.id==navViews.getOrNull(4)?.id)restoreFocus()
    }
   }
  }
 }
 private fun showApps(entries:List<AppEntry>,icons:Map<String,Drawable.ConstantState>?=null) {
  val focused=focusViews.entries.firstOrNull{it.key.startsWith("grid:") && it.value.hasFocus()}?.value?.tag
  appsLoading?.let{root.removeView(it)};appsLoading=null
  appGrid?.let{root.removeView(it)};focusViews.keys.filter{it.startsWith("grid:")}.forEach{focusViews.remove(it)}
  val scroll=ScrollView(this);val rows=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};scroll.addView(rows)
  val controls=mutableListOf<View>()
  entries.chunked(4).forEach{batch->val row=LinearLayout(this).apply{isBaselineAligned=false};batch.forEach{app->
   val view=tile(app.label){launchPackage(app.pkg)}.apply{tag=app.pkg;maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END;setTextSize(TypedValue.COMPLEX_UNIT_PX,22*scale)}
   val icon=icons?.get(app.iconKey)?.newDrawable(resources)?.mutate() ?: AppIcons.cached(this,app.iconKey)
   if(icon!=null){icon.setBounds(0,0,p(64),p(64));view.setCompoundDrawables(icon,null,null,null);view.compoundDrawablePadding=p(18)}else view.text=app.label.take(1)+"  "+app.label
   controls.add(view);row.addView(view,LinearLayout.LayoutParams(contentPx(396),p(132)).apply{setMargins(0,0,contentPx(20),p(22))})
  };rows.addView(row)}
  // A late listing is inserted beneath this page's sidebar, so the expanded menu still covers it.
  val params=FrameLayout.LayoutParams(contentPx(1648),p(710)).apply{leftMargin=p(HomeLayout.contentX(244,compactLayout));topMargin=p(306)}
  val below=if(::sidebarPanel.isInitialized)root.indexOfChild(sidebarPanel)else -1
  if(below>=0)root.addView(scroll,below,params)else root.addView(scroll,params)
  appGrid=scroll;wireGrid(controls,4)
  if(focused!=null)controls.firstOrNull{it.tag==focused}?.requestFocus()
 }
 private fun wireGrid(cards:List<View>,columns:Int,actions:List<View> = emptyList(),above:List<View> = emptyList()){
  cards.forEachIndexed{i,v->focusViews["grid:$i"]=v};actions.forEachIndexed{i,v->focusViews["action:$i"]=v};above.forEachIndexed{i,v->focusViews["filter:$i"]=v}
  val nav=navViews.toList()
  val navId=focusViews["nav:"+when(page){"Continue Watching"->0;"TV Shows"->1;"Movies"->2;"Anime"->3;"Apps"->4;"Photos"->5;"Search"->6;else->7}]?.id ?: View.NO_ID
  // Right from the sidebar lands on the first card; filters above the grid are one Up away.
  val first=cards.firstOrNull() ?: above.firstOrNull() ?: actions.firstOrNull()
  nav.forEach{it.nextFocusRightId=first?.id ?: it.id}
  val lastRow=if(cards.isEmpty())0 else (cards.size-1)/columns*columns
  cards.forEachIndexed{i,v->
   v.nextFocusLeftId=if(i%columns==0)navId else cards[i-1].id
   v.nextFocusRightId=if(i%columns==columns-1 || i==cards.lastIndex)v.id else cards[i+1].id
   v.nextFocusUpId=if(i<columns)above.getOrNull(i.coerceAtMost(above.lastIndex))?.id ?: v.id else cards[i-columns].id
   // Below a shorter last row, Down still reaches that row; from the last row it reaches the buttons.
   v.nextFocusDownId=when{i+columns<cards.size->cards[i+columns].id;i<lastRow->cards.last().id;else->actions.getOrNull((i%columns).coerceAtMost(actions.lastIndex))?.id ?: v.id}
  }
  above.forEachIndexed{i,v->
   v.nextFocusLeftId=if(i==0)navId else above[i-1].id;v.nextFocusRightId=above.getOrNull(i+1)?.id ?: v.id;v.nextFocusUpId=v.id
   v.nextFocusDownId=cards.getOrNull(i)?.id ?: actions.firstOrNull()?.id ?: v.id
  }
  actions.forEachIndexed{i,v->
   v.nextFocusLeftId=if(i==0)navId else actions[i-1].id;v.nextFocusRightId=actions.getOrNull(i+1)?.id ?: v.id;v.nextFocusDownId=v.id
   // Up goes to the card above this button in the last row, not always to the last card.
   v.nextFocusUpId=cards.getOrNull(lastRow+i.coerceAtMost(cards.lastIndex-lastRow))?.id ?: above.firstOrNull()?.id ?: navId
  }
 }
 private fun settingsPage(){
  heading("Ambient TV settings");subheading("Manage devices, services and photo display")
  val groups=listOf(
   "TV & devices" to listOf(Triple("TV & inputs","Configure connected TV",{showPage("TV")}),Triple("Android settings","System settings",{safeStart(Intent(Settings.ACTION_SETTINGS))}),Triple("Android screensaver settings","Display and timeout",{safeStart(Intent(Settings.ACTION_DREAM_SETTINGS))})),
   "Connected services" to listOf(Triple("Connect Plex","Manage your media server",{safeStart(Intent(this,PlexConnectActivity::class.java))}),Triple("Open Projectivy","Launch your TV interface",{launchPackage("com.spocky.projengmenu")}),Triple("Photo connection settings",PhotoSync.summary(this).substringBefore("\n"),{safeStart(Intent(this,SettingsActivity::class.java))})).filter{it.first!="Open Projectivy" || packageManager.getLeanbackLaunchIntentForPackage("com.spocky.projengmenu")!=null},
   "Personalisation" to listOf(Triple("Photo screensaver","Family photos and timing",{safeStart(Intent(this,DreamSettingsActivity::class.java))}),Triple("Status & backup","Connections and local preferences",{safeStart(Intent(this,MaintenanceActivity::class.java))}),Triple("Restore hidden videos","${HomeDismissals.count(this)} hidden from Home",{restoreHiddenVideos()}),Triple("Start screensaver","Show family memories now",{startScreensaver()})))
  val controls=mutableMapOf<Pair<Int,Int>,View>()
  groups.forEachIndexed{col,(title,entries)->place(text(title,26),244+col*554,316,530,48);entries.forEachIndexed{row,(label,detail,action)->val v=tile(label+"\n"+detail,action=action).apply{setTextSize(TypedValue.COMPLEX_UNIT_PX,23*scale);maxLines=3;val copy=android.text.SpannableString(text);val start=label.length+1;copy.setSpan(android.text.style.AbsoluteSizeSpan(p(20)),start,copy.length,0);text=copy};controls[col to row]=v;place(v,244+col*554,382+row*148,530,126)}}
  val navId=focusViews["nav:7"]!!.id
  controls.forEach{(pos,v)->val(c,r)=pos;v.nextFocusLeftId=controls[(c-1) to r]?.id ?: if(c==0)navId else controls[(c-1) to 0]?.id ?: navId;v.nextFocusRightId=controls[(c+1) to r]?.id ?: controls[(c+1) to 0]?.id ?: v.id;v.nextFocusUpId=controls[c to (r-1)]?.id ?: v.id;v.nextFocusDownId=controls[c to (r+1)]?.id ?: v.id}
  navViews.forEach{it.nextFocusRightId=controls[0 to 0]!!.id}
  place(text("Settings open the existing Android TV, Plex and Synology integrations.",21),244,1000,1648,48)
 }
 private fun searchPage(){
  heading("Search");subheading("Find videos on YouTube")
  val input=EditText(this).apply{id=nextId++;hint="Search YouTube…";setTextColor(titleColor);setHintTextColor(Color.LTGRAY);setTextSize(TypedValue.COMPLEX_UNIT_PX,28*scale);setSingleLine(true);imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH;background=states(rounded(Color.rgb(24,52,77),true),rounded(Color.rgb(24,52,77),false));setPadding(p(24),p(10),p(24),p(10))}
  place(input,244,316,1200,96)
  fun submit(){val query=input.text.toString().trim();if(query.isNotEmpty()){(getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(input.windowToken,0);val target=Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com/results?search_query="+Uri.encode(query))).setPackage("com.google.android.youtube.tv");if(target.resolveActivity(packageManager)!=null)safeStart(target)else launchPackage("com.google.android.youtube.tv")}}
  val submit=tile("Search YouTube"){submit()};place(submit,1464,316,424,96)
  input.setOnEditorActionListener{_,action,event->if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH || event?.keyCode==KeyEvent.KEYCODE_ENTER){if(event==null || event.action==KeyEvent.ACTION_UP)submit();true}else false}
  place(text("Use your remote to type. Results open in YouTube.",24).apply{setTextColor(quietColor)},244,432,1500,58)
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
  actions.chunked(3).forEach { batch -> val row=LinearLayout(this).apply{isBaselineAligned=false};batch.forEach { (label,action)->row.addView(tile(label,action=action).also{controls.add(it)},LinearLayout.LayoutParams(p(450),p(100)).apply {setMargins(0,0,p(20),p(20))}) };rows.addView(row) }
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
  if(intent.resolveActivity(packageManager)!=null)safeStart(intent) else showPage("Apps")
 }
 private fun safeStart(intent:Intent) {try {
  val target=if(intent.action==Settings.ACTION_DREAM_SETTINGS && intent.resolveActivity(packageManager)==null){Toast.makeText(this,"Screen saver controls are in Android settings on this TV",Toast.LENGTH_LONG).show();Intent(Settings.ACTION_SETTINGS)}else intent
  startActivity(target)}catch(_:Exception){Toast.makeText(this,"Unable to open this item",Toast.LENGTH_SHORT).show()} }
 private val feedObserver=object:android.database.ContentObserver(handler){override fun onChange(selfChange:Boolean){loadWatchNext()}}
 private val feedReturnFinalRetry=Runnable{if(feedActive && page=="Home")loadWatchNext(true)}
 private val feedReturnRetry=Runnable{if(feedActive && page=="Home")loadWatchNext(true)}
 private fun refreshFeedOnReturn(){
  loadWatchNext(true);handler.removeCallbacks(feedReturnRetry);handler.removeCallbacks(feedReturnFinalRetry)
  if(page=="Home"){handler.postDelayed(feedReturnRetry,5000);handler.postDelayed(feedReturnFinalRetry,15000)}
 }
 private val feedPoll=object:Runnable{override fun run(){if(feedActive){loadWatchNext(true);handler.postDelayed(this,60000)}}}
 private val nativeRead=Runnable{
  if(feedActive && page=="Home"){
   val token=epoch
   nativeWorker.queue.clear()
   nativeWorker.execute{
    val items=if(previewFiles!=null)previewWatch.orEmpty()else WatchNextReader.readNative(this)
    handler.post{if(feedActive && token==epoch && page=="Home"){feedSeen=true;applyWatchItems(items)}}
   }
  }
 }
 private val feedRead:Runnable=Runnable{
  val force=feedState.begin(feedActive && page=="Home")
  if(force!=null){
   val token=epoch
   feedWorker.execute{
    val available=if(previewFiles!=null)previewWatch.orEmpty()else WatchNextReader.read(this,force){native->handler.post{if(feedActive && token==epoch && page=="Home"){feedSeen=true;applyWatchItems(native)}}}
    handler.post{
     val current=feedState.finish()
     if(current && feedActive && token==epoch && page=="Home"){
      feedSeen=true;applyWatchItems(available)
      if(previewFiles==null)getSharedPreferences("feed-audit",0).edit().putLong("homeReadAt",System.currentTimeMillis()).putBoolean("lastForced",force).putInt("commits",getSharedPreferences("feed-audit",0).getInt("commits",0)+1).apply()
     }
     if(feedActive && feedState.hasPending){handler.removeCallbacks(feedRead);handler.postDelayed(feedRead,750)}
    }
   }
  }
 }
 private fun loadWatchNext(forcePlex:Boolean=false){
  if(page!="Home" || !::mediaHost.isInitialized)return
  feedState.request(forcePlex);handler.removeCallbacks(nativeRead);handler.postDelayed(nativeRead,250);handler.removeCallbacks(feedRead);handler.postDelayed(feedRead,750)
 }
 // True once a real read has been applied since Home last became active; a hide/undo or the replay never sets it.
 private var feedSeen=false
 private fun applyWatchItems(source:List<WatchItem>){
  availableWatchItems=source
  val available=source.filterNot{HomeDismissals.hidden(this,it) || (it.owner=="com.google.android.youtube.tv" && HomeDismissalPolicy.completed(it.position,it.duration))}
  // Preserve provider order and next-episode priority within the mixed feed.
  val firstOwner=if(available.filter{it.owner=="com.plexapp.android"}.take(3).any{it.nextEpisode})"com.plexapp.android"else null
  val ordered=SourceMixPolicy.indices(available.map{it.owner},available.size,firstOwner).map{available[it]}
  currentWatchItems=ordered;val items=ordered.take(4)
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
  detach(mediaHost);mediaHost.removeAllViews();mediaArtShown=0;focusViews.keys.filter{it.startsWith("media:")}.toList().forEach{focusViews.remove(it)}
  mediaHeading?.text="Continue Watching  ›"
  fun placeCard(card:View,i:Int){focusViews["media:$i"]=card;mediaHost.addView(card,FrameLayout.LayoutParams(contentPx(HomeLayout.MEDIA_W),p(HomeLayout.MEDIA_H)).apply{leftMargin=contentPx(i*416)})}
  if(items.isEmpty()) {
   val message=if(signature=="loading")"Loading Continue Watching…" else "No continue watching items available. Open Plex or YouTube above."
   mediaHost.addView(text(message,27).apply{setTextColor(Color.rgb(185,204,218));contentDescription=message},FrameLayout.LayoutParams(-1,p(90)).apply{topMargin=p(30)})
  } else items.forEachIndexed{i,item->
   val remaining=if(item.nextEpisode)"Next episode"else if(item.duration>0 && item.position>=0 && item.position<item.duration)"${(item.duration-item.position+59999)/60000} min left"else ""
   val card=mediaCard(item.title,remaining){item.open(this)};placeCard(card,i);bindWatchOptions(card,item,i)
   val source=if(item.owner=="com.plexapp.android")"Plex"else"YouTube"
   val watchedProgress=if(item.nextEpisode)null else WatchNextPolicy.progress(item.position,item.duration)
   card.contentDescription=item.title+", "+source+", hold OK for options"+(if(item.nextEpisode)", Next episode"else "")+(watchedProgress?.let{", ${it/10} percent watched"} ?: "")
   val badge=text(if(item.nextEpisode)"Plex · Next"else source,17).apply{gravity=Gravity.CENTER;setPadding(p(7),p(3),p(7),p(3));background=rounded(Color.argb(225,6,16,27),false);AppIcons.load(this@HomeActivity,item.owner)?.let{icon->icon.setBounds(0,0,p(18),p(18));compoundDrawablePadding=p(5);setCompoundDrawables(icon,null,null,null)}}
   card.addView(badge,FrameLayout.LayoutParams(p(if(item.nextEpisode)145 else if(source=="Plex")89 else 126),p(32),Gravity.TOP or Gravity.RIGHT).apply{topMargin=p(9);rightMargin=p(9)})
   val image=card.getChildAt(0) as ImageView
   AppIcons.load(this,item.owner)?.let{image.setImageDrawable(it)}
   image.scaleType=ImageView.ScaleType.FIT_CENTER;image.setPadding(p(90),p(14),p(90),p(60))
   // Decoded stills are shared with later rebuilds of this row, so an unchanged card never flickers.
   val artKey="row:"+item.art
   fun showArt(bitmap:Bitmap){image.setPadding(0,0,0,0);image.scaleType=ImageView.ScaleType.CENTER_CROP;image.setImageBitmap(bitmap);mediaArtShown++}
   fun loadArt(retry:Boolean){artworkWorker.execute imageJob@{
    if(token!=epoch || revision!=mediaEpoch || !feedActive)return@imageJob
    val bitmap=WatchNextReader.art(this,item.art)?.also{ArtThumbs.put(artKey,ThumbEntry(it,null))}
    handler.post{
     if(token!=epoch || revision!=mediaEpoch || isDestroyed)return@post
     if(bitmap!=null)showArt(bitmap)
     else if(retry && feedActive)handler.postDelayed({if(feedActive && token==epoch && revision==mediaEpoch)loadArt(false)},3000)
    }
   }}
   val saved=ArtThumbs.get(artKey);if(saved!=null)showArt(saved.photo)else loadArt(true)
   watchedProgress?.let{actual->
    // Two plain views cost far less to create than a themed ProgressBar.
    card.addView(View(this).apply{setBackgroundColor(Color.rgb(70,78,92))},FrameLayout.LayoutParams(-1,maxOf(1,p(4)),Gravity.BOTTOM))
    card.addView(View(this).apply{setBackgroundColor(if(source=="YouTube")Color.rgb(255,36,62)else Color.rgb(66,205,235))},FrameLayout.LayoutParams(contentPx(HomeLayout.MEDIA_W)*actual/1000,maxOf(1,p(4)),Gravity.BOTTOM or Gravity.LEFT))
   }
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
  val media=focusViews.keys.count{it.startsWith("media:")};val footers=focusViews.keys.count{it.startsWith("footer:")}.coerceAtLeast(3)
  for(node in HomeFocusPolicy.nodes(media,footers)){
   val view=focusViews[node.key] ?: continue
   fun id(direction:String)=focusViews[HomeFocusPolicy.target(node,direction,media,footers).key]?.id ?: view.id
   view.nextFocusLeftId=id("left");view.nextFocusRightId=id("right");view.nextFocusUpId=id("up");view.nextFocusDownId=id("down")
  }
 }
 override fun onKeyUp(keyCode:Int,event:KeyEvent):Boolean {if(keyCode==KeyEvent.KEYCODE_ESCAPE){onBackPressed();return true};return super.onKeyUp(keyCode,event)}
 override fun onBackPressed() {if(page!="Home")showPage("Home") else focusViews["nav:0"]?.requestFocus()}
}
