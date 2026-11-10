package org.ambienttv.home

/**
 * Up to two TV inputs (a games console, a set-top box) pinned beside the app shortcuts on Home.
 * Only the input the owner picks and a name from a short list are stored; nothing is read from the device.
 */
internal object HomeShortcuts {
 const val SLOTS=2
 val NAMES=listOf("PlayStation","Switch","Xbox","Console","Blu-ray","Set-top box")
 /** Cycles off -> first input -> ... -> last input -> off. An input that no longer exists counts as off. */
 fun nextInput(current:String,inputs:List<String>):String {
  val at=inputs.indexOf(current)
  return if(at<0)inputs.firstOrNull().orEmpty() else inputs.getOrNull(at+1).orEmpty()
 }
 fun nextName(current:String)=NAMES[(NAMES.indexOf(current)+1)%NAMES.size]
 fun name(saved:String)=if(saved in NAMES)saved else NAMES.first()
 /** Left edge and width of tile [index] of [count] across the 1644px content row with 20px gaps. */
 fun tileX(index:Int,count:Int)=HomeLayout.LEFT+index*(tileWidth(count)+if(count==3)21 else 20) // three keeps its original 555px step
 fun tileWidth(count:Int):Int {require(count in 1..3+SLOTS);return if(count==3)534 else (HomeLayout.RIGHT-HomeLayout.LEFT-(count-1)*20)/count}
}
