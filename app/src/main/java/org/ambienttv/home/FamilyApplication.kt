package org.ambienttv.home
import android.app.*
import android.os.Bundle
class FamilyApplication:Application(){
 companion object{@Volatile var ownUiVisible=false;private set}
 override fun onCreate(){super.onCreate();registerActivityLifecycleCallbacks(object:ActivityLifecycleCallbacks{
  private var resumed=0
  override fun onActivityResumed(a:Activity){resumed++;ownUiVisible=resumed>0}
  override fun onActivityPaused(a:Activity){resumed=(resumed-1).coerceAtLeast(0);ownUiVisible=resumed>0}
  override fun onActivityCreated(a:Activity,b:Bundle?){}
  override fun onActivityStarted(a:Activity){}
  override fun onActivityStopped(a:Activity){}
  override fun onActivitySaveInstanceState(a:Activity,b:Bundle){}
  override fun onActivityDestroyed(a:Activity){}
 })}
}
