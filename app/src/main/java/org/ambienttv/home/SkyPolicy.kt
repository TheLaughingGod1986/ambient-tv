package org.ambienttv.home

import java.util.TimeZone
import kotlin.math.*

/** What the sky is doing, reduced to the few kinds of scene Home draws. */
internal enum class SkyWeather { CLEAR, PARTLY_CLOUDY, OVERCAST, FOG, DRIZZLE, LIGHT_RAIN, RAIN, HEAVY_RAIN, SNOW, THUNDER;
 val rain get()=this==DRIZZLE || this==LIGHT_RAIN || this==RAIN || this==HEAVY_RAIN || this==THUNDER
 companion object {
  /** WMO weather codes as Open-Meteo reports them. Unknown codes are drawn as a fair, partly cloudy sky. */
  fun of(code:Int?):SkyWeather=when(code) {
   0,1->CLEAR; 2->PARTLY_CLOUDY; 3->OVERCAST; 45,48->FOG
   51,53,55,56,57->DRIZZLE; 61,66,80->LIGHT_RAIN; 63,81->RAIN; 65,67,82->HEAVY_RAIN
   71,73,75,77,85,86->SNOW; 95,96,97,99->THUNDER
   else->PARTLY_CLOUDY
  }
 }
}

internal enum class DayPart { NIGHT, DAWN, MORNING, AFTERNOON, SUNSET, DUSK }

/** Where the sun is for a place and moment. Accurate to well under a degree, which is plenty for scenery. */
internal object SunPosition {
 data class Sun(val elevation:Double,val morning:Boolean,val hourAngle:Double)
 fun at(millis:Long,latitude:Double,longitude:Double):Sun {
  val n=millis/86_400_000.0+2440587.5-2451545.0
  val meanLongitude=(280.460+0.9856474*n).mod(360.0)
  val anomaly=Math.toRadians((357.528+0.9856003*n).mod(360.0))
  val ecliptic=Math.toRadians(meanLongitude+1.915*sin(anomaly)+0.020*sin(2*anomaly))
  val obliquity=Math.toRadians(23.439-0.0000004*n)
  val ascension=Math.toDegrees(atan2(cos(obliquity)*sin(ecliptic),cos(ecliptic)))
  val declination=asin(sin(obliquity)*sin(ecliptic))
  val siderealHours=(18.697374558+24.06570982441908*n).mod(24.0)
  val hourAngle=((siderealHours*15+longitude-ascension)+540.0).mod(360.0)-180.0
  val lat=Math.toRadians(latitude)
  val elevation=Math.toDegrees(asin((sin(lat)*sin(declination)+cos(lat)*cos(declination)*cos(Math.toRadians(hourAngle))).coerceIn(-1.0,1.0)))
  return Sun(elevation,hourAngle<0,hourAngle)
 }
 /** 0 = new moon, 0.5 = full moon. */
 fun moonPhase(millis:Long):Double=((millis/86_400_000.0+2440587.5-2451550.1)/29.530588853).mod(1.0)
 /** A location only for the sun's height when weather is off: a mid latitude and the zone's longitude. */
 fun fallback(zone:TimeZone,millis:Long)=45.0 to (zone.getOffset(millis)/3_600_000.0*15).coerceIn(-180.0,180.0)
}

/** Colours are packed 0xAARRGGBB, mixed without android.graphics so the scene policy can be unit-tested. */
internal object SkyColor {
 fun rgb(r:Int,g:Int,b:Int)=(255 shl 24) or (r.coerceIn(0,255) shl 16) or (g.coerceIn(0,255) shl 8) or b.coerceIn(0,255)
 fun r(c:Int)=c ushr 16 and 255; fun g(c:Int)=c ushr 8 and 255; fun b(c:Int)=c and 255
 fun mix(a:Int,b:Int,t:Double):Int {
  val k=t.coerceIn(0.0,1.0)
  fun ch(x:Int,y:Int)=(x+(y-x)*k).roundToInt()
  return rgb(ch(r(a),r(b)),ch(g(a),g(b)),ch(this.b(a),this.b(b)))
 }
 fun luminance(c:Int)=(0.2126*r(c)+0.7152*g(c)+0.0722*b(c))/255.0
 /** A slightly cool grey of the same brightness, so overcast skies keep the hour's light level. */
 fun grey(c:Int):Int { val l=luminance(c)*255; return rgb((l*.94).roundToInt(),l.roundToInt(),(l*1.07).roundToInt()) }
 fun scale(c:Int,f:Double)=rgb((r(c)*f).roundToInt(),(g(c)*f).roundToInt(),(b(c)*f).roundToInt())
}

/** Everything the renderer needs. Ridges run from the farthest range to the nearest forest. */
internal data class SkyLook(
 val skyTop:Int,val skyMiddle:Int,val skyHorizon:Int,val glow:Int,val glowStrength:Double,
 val sun:Int,val sunVisible:Double,val ridges:List<Int>,val haze:Int,val mist:Double,val stars:Double,
 val cloud:Int,val cloudShade:Int,val cloudCover:Double,val snowCaps:Double,val brightness:Double
)

/** One scene: what to draw, plus the slowly changing inputs that decide when it must be redrawn. */
internal data class SkyScene(val weather:SkyWeather,val part:DayPart,val elevation:Double,val morning:Boolean,val sunX:Double,val sunY:Double,val moonPhase:Double,val night:Boolean) {
 /** Redraw only when something visible changes: weather, or the sun moving about one and a half degrees. */
 val key:String get()="$weather|${(elevation.coerceIn(-15.0,32.0)/1.5).roundToInt()}|$morning|${(moonPhase*8).roundToInt()%8}"
 val look:SkyLook get()=SkyPolicy.look(this)
 val description:String get()=when(part){DayPart.NIGHT->"Night";DayPart.DAWN->"Sunrise";DayPart.MORNING->"Morning";DayPart.AFTERNOON->"Afternoon";DayPart.SUNSET->"Sunset";DayPart.DUSK->"Dusk"}+", "+when(weather){
  SkyWeather.CLEAR->"clear";SkyWeather.PARTLY_CLOUDY->"partly cloudy";SkyWeather.OVERCAST->"overcast";SkyWeather.FOG->"fog";SkyWeather.DRIZZLE->"drizzle"
  SkyWeather.LIGHT_RAIN->"light rain";SkyWeather.RAIN->"rain";SkyWeather.HEAVY_RAIN->"heavy rain";SkyWeather.SNOW->"snow";SkyWeather.THUNDER->"thunderstorm"}
}

internal object SkyPolicy {
 fun part(elevation:Double,morning:Boolean)=when {
  elevation< -8->DayPart.NIGHT
  morning && elevation<6->DayPart.DAWN
  morning->DayPart.MORNING
  elevation>=6->DayPart.AFTERNOON
  elevation>=-1->DayPart.SUNSET
  else->DayPart.DUSK
 }
 /**
  * The scene for a moment. [code] is the latest weather reading, or null when weather is off, stale or unavailable;
  * then the sky is fair and partly cloudy rather than pretending to know it is sunny or raining.
  */
 fun scene(millis:Long,latitude:Double,longitude:Double,code:Int?):SkyScene {
  val sun=SunPosition.at(millis,latitude,longitude)
  val weather=SkyWeather.of(code)
  // The sun travels across the open sky between the header text and the clock, never behind either.
  val progress=(sun.hourAngle/110.0).coerceIn(-1.0,1.0)
  val sunX=.64+.17*progress
  val sunY=(1.0-(sun.elevation.coerceIn(-6.0,45.0)+6)/51.0)
  val phase=SunPosition.moonPhase(millis)
  return SkyScene(weather,part(sun.elevation,sun.morning),sun.elevation,sun.morning,sunX,sunY,phase,sun.elevation< -6)
 }

 private class Key(val elevation:Double,val top:Int,val middle:Int,val horizon:Int,val glow:Int,val glowStrength:Double,val sun:Int,val ridges:IntArray,val haze:Int,val mist:Double,val stars:Double,val cloud:Int)
 private fun c(r:Int,g:Int,b:Int)=SkyColor.rgb(r,g,b)
 private val night=Key(-14.0,c(5,11,28),c(11,21,46),c(22,36,66),c(40,56,96),.05,c(220,228,245),intArrayOf(c(30,42,70),c(22,33,58),c(15,24,44),c(9,16,30),c(5,10,20)),c(28,40,68),.10,1.0,c(34,44,66))
 /** Sunrise: lavender twilight, a peach and gold horizon, cool valley mist. */
 private val morningKeys=listOf(night,
  Key(-7.0,c(11,21,52),c(32,48,94),c(96,104,146),c(150,130,170),.30,c(255,210,170),intArrayOf(c(54,62,98),c(42,50,84),c(30,38,66),c(15,22,40),c(8,13,26)),c(84,90,130),.25,.35,c(70,74,110)),
  Key(-1.0,c(28,42,90),c(104,94,148),c(244,160,122),c(255,172,120),.60,c(255,214,160),intArrayOf(c(88,84,124),c(66,66,104),c(44,48,80),c(20,25,44),c(10,14,28)),c(170,140,160),.35,.0,c(214,150,150)),
  Key(3.0,c(42,68,128),c(148,138,168),c(255,196,132),c(255,206,140),.65,c(255,232,175),intArrayOf(c(112,112,150),c(86,92,128),c(58,68,100),c(26,36,54),c(13,20,32)),c(210,180,170),.45,.0,c(250,214,190)),
  Key(10.0,c(40,98,168),c(108,158,208),c(198,214,226),c(255,244,215),.35,c(255,250,230),intArrayOf(c(122,150,182),c(92,120,152),c(62,90,114),c(30,54,64),c(15,32,40)),c(200,214,226),.50,.0,c(244,247,250)),
  Key(32.0,c(28,90,168),c(88,148,210),c(172,204,230),c(255,250,235),.25,c(255,253,240),intArrayOf(c(118,146,178),c(88,116,148),c(58,86,108),c(28,52,60),c(14,30,38)),c(176,204,226),.22,.0,c(250,252,255)))
 /** Afternoon: deeper, warmer light that turns gold, then a sunset close to the original photograph. */
 private val afternoonKeys=listOf(night,
  Key(-7.0,c(13,19,50),c(54,46,94),c(148,88,108),c(170,96,110),.30,c(255,190,150),intArrayOf(c(52,50,82),c(40,40,70),c(28,30,56),c(13,15,32),c(7,9,22)),c(90,72,102),.20,.40,c(90,70,100)),
  Key(-1.0,c(34,38,94),c(146,78,110),c(255,120,60),c(255,130,64),.80,c(255,190,110),intArrayOf(c(96,70,104),c(74,56,92),c(48,40,72),c(22,20,40),c(12,12,26)),c(160,96,110),.30,.0,c(236,128,110)),
  Key(4.0,c(40,62,120),c(168,138,140),c(255,188,108),c(255,196,116),.70,c(255,214,140),intArrayOf(c(128,110,130),c(100,88,114),c(66,62,90),c(28,30,46),c(14,16,30)),c(200,160,140),.30,.0,c(255,210,170)),
  Key(12.0,c(34,80,150),c(108,148,198),c(214,204,184),c(255,236,196),.40,c(255,244,210),intArrayOf(c(136,140,162),c(104,112,138),c(68,80,104),c(30,44,56),c(15,26,36)),c(210,204,196),.25,.0,c(252,246,236)),
  Key(32.0,c(26,82,160),c(84,140,204),c(176,200,222),c(255,248,228),.25,c(255,252,236),intArrayOf(c(118,140,170),c(88,110,142),c(58,82,104),c(28,50,58),c(14,30,38)),c(178,200,222),.18,.0,c(250,251,255)))

 private class Weather(val cover:Double,val desaturate:Double,val darken:Double,val sun:Double,val fog:Double,val snow:Double)
 private fun weather(w:SkyWeather)=when(w) {
  SkyWeather.CLEAR->Weather(.08,0.0,0.0,1.0,0.0,0.0)
  SkyWeather.PARTLY_CLOUDY->Weather(.42,.10,.03,.9,0.0,0.0)
  SkyWeather.OVERCAST->Weather(.95,.70,.16,.12,.12,0.0)
  SkyWeather.FOG->Weather(.70,.78,0.0,.28,1.0,0.0)
  SkyWeather.DRIZZLE->Weather(.92,.70,.20,0.0,.25,0.0)
  SkyWeather.LIGHT_RAIN->Weather(.96,.72,.26,0.0,.22,0.0)
  SkyWeather.RAIN->Weather(1.0,.76,.32,0.0,.22,0.0)
  SkyWeather.HEAVY_RAIN->Weather(1.0,.80,.42,0.0,.30,0.0)
  SkyWeather.SNOW->Weather(1.0,.82,.06,0.0,.32,1.0)
  SkyWeather.THUNDER->Weather(1.0,.72,.50,0.0,.16,0.0)
 }

 fun look(scene:SkyScene):SkyLook {
  val keys=if(scene.morning)morningKeys else afternoonKeys
  val e=scene.elevation.coerceIn(keys.first().elevation,keys.last().elevation)
  val upper=keys.indexOfFirst{it.elevation>=e}.coerceAtLeast(1)
  val a=keys[upper-1];val b=keys[upper];val t=if(b.elevation==a.elevation)0.0 else (e-a.elevation)/(b.elevation-a.elevation)
  fun m(x:Int,y:Int)=SkyColor.mix(x,y,t)
  fun d(x:Double,y:Double)=x+(y-x)*t
  val w=weather(scene.weather)
  /** Cloud cover greys and darkens the hour's colours; it never changes how bright the hour is by much. */
  fun weathered(color:Int,extraDark:Double=0.0)=SkyColor.scale(SkyColor.mix(color,SkyColor.grey(color),w.desaturate),1-w.darken-extraDark)
  val top=weathered(m(a.top,b.top));val middle=weathered(m(a.middle,b.middle));val horizon=weathered(m(a.horizon,b.horizon))
  val haze=weathered(m(a.haze,b.haze))
  // Fog lifts every range towards the haze; snow whitens the far ranges.
  val snowTone=SkyColor.mix(horizon,c(236,240,246),.55)
  val ridges=a.ridges.indices.map{i->
   val base=weathered(m(a.ridges[i],b.ridges[i]),.04*i*w.darken)
   val fogged=SkyColor.mix(base,haze,w.fog*(.75-.13*i).coerceAtLeast(.12))
   SkyColor.mix(fogged,SkyColor.scale(snowTone,1.0-.17*i),w.snow*(.55-.11*i).coerceAtLeast(0.0))
  }
  val clearCloud=m(a.cloud,b.cloud)
  // Fair-weather clouds are lit by the hour (white, gold or pink) with bluish bases. Under a full cover the
  // moving clouds are darker patches of the same grey sky, never bright puffs on a dark sky.
  val heavy=(w.cover-.6).coerceIn(0.0,.4)/.4
  val cloud=SkyColor.mix(clearCloud,SkyColor.scale(SkyColor.mix(middle,top,.35),.86-.25*w.darken),heavy)
  val cloudShade=SkyColor.mix(SkyColor.scale(SkyColor.mix(clearCloud,middle,.5),.84),SkyColor.scale(cloud,.74),heavy)
  val brightness=SkyColor.luminance(middle)
  return SkyLook(top,middle,horizon,weathered(m(a.glow,b.glow)),d(a.glowStrength,b.glowStrength)*(.25+.75*w.sun),
   m(a.sun,b.sun),w.sun,ridges,haze,(d(a.mist,b.mist)+w.fog*.55).coerceAtMost(1.0),d(a.stars,b.stars)*(1-w.cover).coerceAtLeast(0.0),
   cloud,cloudShade,w.cover,((1-w.cover*.45)*(if(scene.night).35 else 1.0)).coerceAtLeast(w.snow*.8),brightness)
 }

 /** Frames per second for the moving layer. Slow drift needs few frames; falling rain needs more. */
 fun frameRate(weather:SkyWeather,night:Boolean)=when(weather) {
  SkyWeather.LIGHT_RAIN,SkyWeather.RAIN,SkyWeather.HEAVY_RAIN,SkyWeather.THUNDER->30
  SkyWeather.DRIZZLE,SkyWeather.SNOW->24
  SkyWeather.FOG,SkyWeather.OVERCAST,SkyWeather.PARTLY_CLOUDY->15
  SkyWeather.CLEAR->if(night)10 else 15
 }
 /** Number of raindrops or snowflakes. */
 fun particles(weather:SkyWeather)=when(weather){SkyWeather.DRIZZLE->70;SkyWeather.LIGHT_RAIN->100;SkyWeather.RAIN->140;SkyWeather.HEAVY_RAIN,SkyWeather.THUNDER->210;SkyWeather.SNOW->120;else->0}
 /** Gentle, infrequent lightning: a soft brightening every 18-45 seconds, far below any flashing threshold. */
 fun nextLightningMs(random:java.util.Random)=18_000L+random.nextInt(27_000)
}
