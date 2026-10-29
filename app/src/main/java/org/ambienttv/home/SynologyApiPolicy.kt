package org.ambienttv.home

/** Only accept NAS-advertised CGI paths within webapi, never another origin or traversal. */
object SynologyApiPolicy {
 fun path(value:String):String {
  require(value.matches(Regex("(?:[A-Za-z0-9_-]+/)*[A-Za-z0-9_-]+\\.cgi")))
  return value
 }
 fun version(requested:Int,min:Int,max:Int):Int {require(min>=1 && max>=min);return requested.coerceIn(min,max)}
}
