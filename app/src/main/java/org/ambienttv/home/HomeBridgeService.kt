package org.ambienttv.home
import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.content.Intent

/** Only reads the active window's package name, never text or child nodes. */
class HomeBridgeService:AccessibilityService(){
 private val handler=android.os.Handler(android.os.Looper.getMainLooper())
 private var retries=0
 private val launch=object:Runnable{override fun run(){
  val root=rootInActiveWindow
  val owner=try{root?.packageName?.toString()}finally{root?.recycle()}
  if(owner==null && retries++<4){handler.postDelayed(this,50);return}
  if(owner!="com.google.android.tvlauncher")return
  getSharedPreferences("home-bridge-state",0).edit().putLong("lastRedirectAt",System.currentTimeMillis()).apply()
  val intent=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setClass(this@HomeBridgeService,HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
  startActivity(intent)
 }}
 override fun onAccessibilityEvent(event:AccessibilityEvent?){
  if(event?.eventType!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.packageName?.toString()!="com.google.android.tvlauncher")return
  // Sony opens this transparent helper while entering a dream. It is not Home;
  // redirecting it clears the previous task before the dream can reveal it.
  if(event.className?.toString()=="com.google.android.tvlauncher.BootHelperEmptyActivity") {
   handler.removeCallbacks(launch);return
  }
  handler.removeCallbacks(launch);retries=0;handler.post(launch)
 }
 override fun onInterrupt(){handler.removeCallbacks(launch)}
 override fun onDestroy(){handler.removeCallbacks(launch);super.onDestroy()}
}
