package org.ambienttv.home

data class FocusNode(val group:String,val index:Int) {val key get()="$group:$index"}
object HomeFocusPolicy {
 fun nodes(media:Int)=listOf("nav" to 8,"heading" to 2,"photo" to 4,"media" to media,"footer" to 3).flatMap{(g,n)->(0 until n).map{FocusNode(g,it)}}
 fun target(node:FocusNode,direction:String,media:Int):FocusNode {
  require(media>=0)
  val i=node.index
  fun mediaOrHeading(index:Int)=if(media>0)FocusNode("media",index.coerceAtMost(media-1))else FocusNode("heading",1)
  return when(node.group){
   "nav"->when(direction){"up"->FocusNode("nav",(i-1).coerceAtLeast(0));"down"->FocusNode("nav",(i+1).coerceAtMost(7));"right"->FocusNode("photo",0);else->node}
   "heading"->when(direction){"left"->FocusNode("nav",0);"down"->if(i==0)FocusNode("photo",0)else mediaOrHeading(0);"up"->if(i==1)FocusNode("footer",0)else node;else->node}
   "photo"->when(direction){
    "left"->if(i==0)FocusNode("nav",0)else FocusNode("photo",if(i>=2)1 else 0)
    "right"->FocusNode("photo",if(i==0)1 else if(i==1)2 else i)
    "down"->if(i==2)FocusNode("photo",3)else FocusNode("footer",i.coerceAtMost(2))
    "up"->if(i==3)FocusNode("photo",2)else FocusNode("heading",0)
    else->node}
   "media"->when(direction){"left"->if(i==0)FocusNode("nav",0)else FocusNode("media",i-1);"right"->FocusNode("media",(i+1).coerceAtMost(media-1));"up"->FocusNode("heading",1);else->node}
   "footer"->when(direction){"left"->if(i==0)FocusNode("nav",0)else FocusNode("footer",i-1);"right"->FocusNode("footer",(i+1).coerceAtMost(2));"up"->FocusNode("photo",if(i==2)3 else i);"down"->FocusNode("heading",1);else->node}
   else->node
  }
 }
}
