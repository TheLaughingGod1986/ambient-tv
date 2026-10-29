package org.ambienttv.home

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.widget.*
import android.graphics.Color
import android.graphics.Rect
import android.view.View
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

class SettingsActivity : Activity() {
 private val worker = Executors.newSingleThreadExecutor()
 private lateinit var status: TextView
 private lateinit var signIn: Button
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
  fun dp(n: Int) = (n * resources.displayMetrics.density + 0.5f).toInt()
  val root = LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL; setPadding(dp(48),dp(28),dp(48),dp(28)); setBackgroundColor(Color.rgb(12,22,32))
  }
  setContentView(root)
  val intro = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(0,0,dp(28),0) }
  root.addView(intro,LinearLayout.LayoutParams(0,-1,0.36f))
  val content = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(4),dp(4),dp(4),dp(24)) }
  val scroll = ScrollView(this).apply { isFillViewport=true; addView(content); isFocusable=false }
  root.addView(scroll,LinearLayout.LayoutParams(0,-1,0.64f))
  val layoutViews = linkedMapOf<String, View>()
  fun label(text: String, size: Float=22f, parent: LinearLayout=content) = TextView(this).apply {
   this.text=text; textSize=size; setTextColor(Color.WHITE); setPadding(0,dp(6),0,dp(8)); parent.addView(this)
  }
  label("Ambient TV Photos",30f,intro)
  label("Sign in to your Synology NAS",24f,intro)
  label("Use the same username and password you use for Synology Photos.\n\nIf your account uses a verification code, enter the current code too.",20f,intro)
  status = label(Photos.status(this),20f,intro)
  status.setTextColor(Color.rgb(141,228,202))
  label("Use ↑ and ↓ to move between fields. Press OK to type. Scroll down for the sign-in button.",18f,intro)
  fun field(name: String, value: String="", secret: Boolean=false): EditText {
   val title=label(name)
   layoutViews["label-$name"]=title
   return EditText(this).apply {
    contentDescription=name; setText(value); setSingleLine(true); isSaveEnabled=false
    textSize=24f; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
    setPadding(dp(14),dp(12),dp(14),dp(12)); minHeight=dp(60)
    background=getDrawable(R.drawable.input_background)
    inputType=if(secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    imeOptions=EditorInfo.IME_ACTION_NEXT or EditorInfo.IME_FLAG_NO_EXTRACT_UI
    content.addView(this,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
    layoutViews["field-$name"]=this
   }
  }
  val person = field("Person name in Synology Photos",AppPreferences.person(this))
  label("Copy the exact name from People in Synology Photos Personal Space. This is not an album or folder name.",18f)
  val user = field("1. Synology username")
  val password = field("2. Password",secret=true)
  val otp = field("3. Verification code — only if enabled",secret=true)
  label("Leave the code blank if your account does not use it.",18f)
  val localConsent = CheckBox(this).apply {
   text="Allow local HTTP connection to my NAS (unencrypted)"; textSize=20f; setTextColor(Color.WHITE)
   minHeight=dp(64); buttonTintList=android.content.res.ColorStateList.valueOf(Color.rgb(141,228,202)); content.addView(this)
  }
  val base = EditText(this).apply { setText(Vault(this@SettingsActivity).read()?.first ?: ""); isSaveEnabled=false }
  fun button(text: String, action: ()->Unit) = Button(this).apply {
   this.text=text; textSize=20f; isAllCaps=false; minHeight=dp(60)
   content.addView(this,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8); bottomMargin=dp(8) })
   setOnClickListener { action() }
  }
  signIn = button("Sign in and load photos") {
   val address = try { Synology.validateBase(base.text.toString()) } catch(_: Exception) { status.text="Enter your NAS HTTPS address, or a private IPv4 address with HTTP consent."; return@button }
   if(address.startsWith("http:") && !localConsent.isChecked) { status.text="Confirm local HTTP use, or enter a trusted HTTPS address."; return@button }
   val selectedPerson=person.text.toString().trim()
   if(selectedPerson.isBlank()){status.text="Enter the exact person name from Synology Photos.";return@button}
   val username = user.text.toString(); val pass = password.text.toString(); val code = otp.text.toString()
   password.text.clear(); otp.text.clear()
   if(username.isBlank() || pass.isEmpty()) { status.text="Enter your username and password."; return@button }
   signIn.isEnabled=false; status.text="Signing in…"
   worker.execute {
    try {
     val sid = Synology(address).login(username,pass,code)
     Photos.clear(applicationContext)
     AppPreferences.savePerson(applicationContext,selectedPerson)
     AppPreferences.setDemo(applicationContext,false)
     Vault(this).save(address,sid)
     Photos.refresh(applicationContext,true) { message -> runOnUiThread { status.text=message; signIn.isEnabled=true } }
    } catch(e: ApiFailure) { runOnUiThread {
     status.text=when(e.code) { 403 -> "MFA required. Re-enter password and current MFA code."
      404 -> "MFA code was rejected. Try the current code."
      400 -> "Username or password was rejected."
      406 -> "This account needs an additional Synology approval flow. Standard password and OTP sign-in is currently supported."
      else -> "Sign-in rejected (code ${e.code})." }; signIn.isEnabled=true
    } } catch(_: Exception) { runOnUiThread { status.text="Could not connect securely. Check the NAS address and certificate."; signIn.isEnabled=true } }
   }
  }
  label("Connection address",22f)
  base.apply {
   textSize=22f; setTextColor(Color.WHITE); setSingleLine(true); minHeight=dp(60)
   setPadding(dp(14),dp(12),dp(14),dp(12)); background=getDrawable(R.drawable.input_background)
   inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
   imeOptions=EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
   content.addView(this,LinearLayout.LayoutParams(-1,-2))
  }
  label("Enter your own NAS address. HTTPS with a trusted certificate is recommended.",18f)
  button("Use demo photos") { AppPreferences.setDemo(this,true); status.text="Demo photos enabled. Your accounts have not been changed." }
  button("Refresh photos") { getSharedPreferences("home-daily",0).edit().putLong("row-day",Long.MIN_VALUE).apply(); Photos.refresh(applicationContext,true) { message -> runOnUiThread { status.text=message } } }
  button("Disconnect and erase this app’s cached photos") {
   if(!signIn.isEnabled) { status.text="Wait for sign-in to finish."; return@button }
   try {
    val session=Vault(this).read(); Photos.clear(this); status.text="Disconnected. Cached photos erased."
    if(session!=null) worker.execute { try { Synology(session.first,session.second).call("SYNO.API.Auth","logout",mapOf("session" to "SynologyPhotos"),6) } catch(_: Exception) {} }
   } catch(_: Exception) { status.text="Wait for the current refresh to finish." }
  }
  if(Vault(this).read()!=null) Photos.refresh(applicationContext,intent.getBooleanExtra("refresh_photos",false)) { message -> runOnUiThread { status.text=message } }
  button("Photo screensaver layout — Hero / Classic") { startActivity(android.content.Intent(this,DreamSettingsActivity::class.java)) }
  button("Connect Plex for Continue Watching") { startActivity(android.content.Intent(this,PlexConnectActivity::class.java)) }
  button("Disconnect Plex from Ambient TV") { worker.execute { PlexFeed.clear(applicationContext); runOnUiThread {status.text="Plex disconnected from Ambient TV. Other apps are unchanged."} } }
  button("Preview Ambient TV") { startActivity(android.content.Intent(this,HomeActivity::class.java)) }
  label("Optional weather",22f)
  val weatherOn=CheckBox(this).apply{text="Show weather for a place I choose";textSize=20f;setTextColor(Color.WHITE);isChecked=AppPreferences.weatherEnabled(this@SettingsActivity);content.addView(this)}
  val place=field("Weather place label",AppPreferences.saved(this,"weatherLabel"))
  val latitude=field("Latitude",AppPreferences.saved(this,"latitude"))
  val longitude=field("Longitude",AppPreferences.saved(this,"longitude"))
  button("Save weather settings") {
   try { AppPreferences.saveWeather(this,weatherOn.isChecked,place.text.toString(),latitude.text.toString(),longitude.text.toString());WeatherStore.clear(this);WeatherStore.refresh(applicationContext);status.text="Weather settings saved." }
   catch(_:IllegalArgumentException){status.text="Enter a short place label, latitude from -90 to 90 and longitude from -180 to 180."}
  }
  label("Weather is off until you enable it. Your chosen coordinates are sent to Open-Meteo, which also sees your network address. Weather is refreshed at most every 30 minutes while the app is used. Readings older than 90 minutes are replaced with Weather unavailable. No device location or photo data is sent to the weather provider.",18f)
  label("Weather data: https://open-meteo.com/ · CC BY 4.0\nhttps://creativecommons.org/licenses/by/4.0/\nCondition names are shortened and temperatures rounded for display.",16f).apply {
   android.text.util.Linkify.addLinks(this,android.text.util.Linkify.WEB_URLS)
   setLinkTextColor(Color.rgb(141,228,202)); movementMethod=android.text.method.LinkMovementMethod.getInstance()
  }
  user.requestFocus()
  label("Then choose Ambient TV Photos in Projectivy’s wallpaper provider settings. Photos stay on your NAS and in this app’s private TV cache. Passwords and MFA codes are never saved. Offline: keep the last photo set.",16f)
 }
}
