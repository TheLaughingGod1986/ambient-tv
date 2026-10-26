package org.ambienttv.home

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.widget.*

class DreamSettingsActivity:Activity() {
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(64,40,64,40);setBackgroundColor(Color.rgb(13,19,25)) }
  val title=TextView(this).apply { text="Ambient TV screensaver";textSize=28f;setTextColor(Color.WHITE) };root.addView(title)
  val help=TextView(this).apply { text="Single photo hero · change every minute\nHero uses a soft matching backdrop. Portrait edges may be cropped when a face is detected. Remote input exits.\nHome layout and sign-in remain unchanged.";textSize=20f;setTextColor(Color.LTGRAY) };root.addView(help)
  val group=RadioGroup(this)
  listOf("Hero photo with previews","Classic Auto","Classic 1 photo","Classic 2 columns","Classic 3 columns").forEachIndexed { mode,label ->
   group.addView(RadioButton(this).apply { id=mode+100;text=label;textSize=24f;minHeight=72;setTextColor(Color.WHITE) })
  }
  val prefs=getSharedPreferences("dream",0)
  group.check(if(prefs.getString("style","hero")=="classic")101+prefs.getInt("columns",0).coerceIn(0,3) else 100)
  group.setOnCheckedChangeListener { _,id -> prefs.edit().putString("style",if(id==100)"hero" else "classic").putInt("columns",if(id==100)0 else id-101).apply() }
  root.addView(group)
  root.addView(Button(this).apply{text="Default photo framing";setOnClickListener{PhotoFraming.choose(this@DreamSettingsActivity)}})
  root.addView(TextView(this).apply { text="Weather: open-meteo.com · CC BY 4.0\nhttps://open-meteo.com/ · https://creativecommons.org/licenses/by/4.0/\nTemperature rounded and conditions shortened. Cached photos continue when the NAS is unavailable.";textSize=16f;setTextColor(Color.LTGRAY);android.text.util.Linkify.addLinks(this,android.text.util.Linkify.WEB_URLS);movementMethod=android.text.method.LinkMovementMethod.getInstance() })
  setContentView(ScrollView(this).apply{addView(root)});group.findViewById<RadioButton>(group.checkedRadioButtonId).requestFocus()
 }
}
