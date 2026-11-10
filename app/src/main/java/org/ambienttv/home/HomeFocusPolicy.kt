package org.ambienttv.home

data class FocusNode(val group:String,val index:Int) {val key get()="$group:$index"}
object HomeFocusPolicy {
 fun nodes(media:Int,footers:Int=3)=listOf("nav" to 8,"heading" to 2,"photo" to 4,"media" to media,"footer" to footers).flatMap{(g,n)->(0 until n).map{FocusNode(g,it)}}
 /** The photo tile above the middle of quick-access tile [i] of [footers], and the reverse. */
 private fun photoAbove(i:Int,footers:Int):Int {val middle=(i+.5f)/footers;return if(middle<.41f)0 else if(middle<.72f)1 else 3}
 private fun footerBelow(photo:Int,footers:Int)=when(photo){0->0;1->(.567f*footers).toInt().coerceAtMost(footers-1);else->footers-1}
 fun target(node:FocusNode,direction:String,media:Int,footers:Int=3):FocusNode {
  require(media>=0 && footers>=3)
  val i=node.index
  fun mediaOrHeading(index:Int)=if(media>0)FocusNode("media",index.coerceAtMost(media-1))else FocusNode("heading",1)
  return when(node.group){
   "nav"->when(direction){"up"->FocusNode("nav",(i-1).coerceAtLeast(0));"down"->FocusNode("nav",(i+1).coerceAtMost(7));"right"->FocusNode("photo",0);else->node}
   "heading"->when(direction){"left"->FocusNode("nav",0);"down"->if(i==0)FocusNode("photo",0)else mediaOrHeading(0);"up"->if(i==1)FocusNode("footer",0)else node;else->node}
   "photo"->when(direction){
    "left"->if(i==0)FocusNode("nav",0)else FocusNode("photo",if(i>=2)1 else 0)
    "right"->FocusNode("photo",if(i==0)1 else if(i==1)2 else i)
    "down"->if(i==2)FocusNode("photo",3)else FocusNode("footer",footerBelow(i,footers))
    "up"->if(i==3)FocusNode("photo",2)else FocusNode("heading",0)
    else->node}
   "media"->when(direction){"left"->if(i==0)FocusNode("nav",0)else FocusNode("media",i-1);"right"->FocusNode("media",(i+1).coerceAtMost(media-1));"up"->FocusNode("heading",1);else->node}
   "footer"->when(direction){"left"->if(i==0)FocusNode("nav",0)else FocusNode("footer",i-1);"right"->FocusNode("footer",(i+1).coerceAtMost(footers-1));"up"->FocusNode("photo",photoAbove(i,footers));"down"->FocusNode("heading",1);else->node}
   else->node
  }
 }
}
