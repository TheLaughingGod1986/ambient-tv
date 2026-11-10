package org.ambienttv.home

/** Which in-progress items belong on a library page, and how each is described. Uses only what the server reports. */
object PlexResumePolicy {
 /**
  * Mirrors the library pages: Movies and TV Shows leave out libraries named for anime, and Anime
  * takes those libraries plus anything the server's own genre and country tags mark as anime.
  */
 fun inCategory(item:PlexResume,category:String,animeTagged:Boolean=false):Boolean {
  val animeLibrary=PlexCatalogPolicy.namedAnime(item.section)
  return when(category){
   "Movies"->item.type=="movie" && !animeLibrary
   "TV Shows"->item.type=="episode" && !animeLibrary
   "Anime"->item.type in listOf("movie","episode") && (animeLibrary || animeTagged)
   else->false
  }
 }
 /** The show for an episode, the film's own title otherwise. */
 fun name(item:PlexResume)=item.show.ifBlank{item.title}
 /** "S2 E5 · 20 min left", "S2 E6 · Next episode" or "54 min left". Blank parts are left out, never guessed. */
 fun detail(item:PlexResume):String {
  val place=if(item.type=="episode" && item.season>=0 && item.episode>=0)"S${item.season} E${item.episode}" else ""
  val state=if(item.nextEpisode)"Next episode" else if(item.duration>0 && item.position in 0 until item.duration)"${(item.duration-item.position+59999)/60000} min left" else ""
  return listOf(place,state).filter{it.isNotEmpty()}.joinToString(" · ")
 }
 /** Watched share of the bar, 0 to 1; a next episode has not been started. */
 fun progress(item:PlexResume)=if(item.nextEpisode || item.duration<=0)0f else (item.position.toFloat()/item.duration).coerceIn(0f,1f)
}
