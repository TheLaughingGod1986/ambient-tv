package org.ambienttv.home

data class FocusNode(val group:String,val index:Int) {val key get()="$group:$index"}
object HomeFocusPolicy {
 fun nodes(media:Int)=listOf("nav" to 8,"heading" to 2,"photo" to 4,"media" to media,"footer" to 5).flatMap{(g,n)->(0 until n).map{FocusNode(g,it)}}
 fun target(node:FocusNode,direction:String,media:Int):FocusNode {
  require(media>=0)
  val i=node.index
  fun mediaOrHeading(index:Int)=if(media>0)FocusNode("media",index.coerceAtMost(media-1))else FocusNode("heading",1)
  return when(node.group){
   "nav"->when(direction){"up"->FocusNode("nav",(i-1).coerceAtLeast(0));"down"->FocusNode("nav",(i+1).coerceAtMost(7));"right"->FocusNode("photo",0);else->node}
   "heading"->when(direction){"left"->FocusNode("nav",0);"down"->if(i==0)FocusNode("photo",0)else if(media>0)FocusNode("media",0)else FocusNode("footer",0);"up"->if(i==1)FocusNode("photo",0)else node;else->node}
   "photo"->when(direction){"left"->if(i==0)FocusNode("nav",0)else FocusNode("photo",i-1);"right"->FocusNode("photo",(i+1).coerceAtMost(3));"down"->mediaOrHeading(i);"up"->FocusNode("heading",0);else->node}
   "media"->when(direction){"left"->if(i==0)FocusNode("nav",0)else FocusNode("media",i-1);"right"->FocusNode("media",(i+1).coerceAtMost(media-1));"up"->FocusNode("heading",1);"down"->FocusNode("footer",i.coerceAtMost(4));else->node}
   "footer"->when(direction){"left"->if(i==0)FocusNode("nav",0)else FocusNode("footer",i-1);"right"->FocusNode("footer",(i+1).coerceAtMost(4));"up"->mediaOrHeading(i);else->node}
   else->node
  }
 }
}
