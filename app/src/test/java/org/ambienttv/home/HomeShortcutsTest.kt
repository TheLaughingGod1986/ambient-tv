package org.ambienttv.home
import org.junit.Assert.*
import org.junit.Test
class HomeShortcutsTest {
 @Test fun inputCyclesThroughOffEveryInputAndBackToOff(){
  val inputs=listOf("a","b")
  assertEquals("a",HomeShortcuts.nextInput("",inputs));assertEquals("b",HomeShortcuts.nextInput("a",inputs));assertEquals("",HomeShortcuts.nextInput("b",inputs))
  assertEquals("a",HomeShortcuts.nextInput("gone",inputs));assertEquals("",HomeShortcuts.nextInput("",emptyList()))
 }
 @Test fun namesCycleAndUnknownFallsBackToTheFirst(){
  var name=HomeShortcuts.name("something else");assertEquals(HomeShortcuts.NAMES.first(),name)
  repeat(HomeShortcuts.NAMES.size){name=HomeShortcuts.nextName(name)};assertEquals(HomeShortcuts.NAMES.first(),name)
 }
 @Test fun tilesKeepTheOriginalThreeAndFitTheRowWhenMoreAreAdded(){
  assertEquals(534,HomeShortcuts.tileWidth(3));assertEquals(244+555,HomeShortcuts.tileX(1,3))
  for(count in 3..5){
   val right=HomeShortcuts.tileX(count-1,count)+HomeShortcuts.tileWidth(count)
   assertTrue(right<=HomeLayout.RIGHT && right>=HomeLayout.RIGHT-8)
  }
 }
 @Test fun focusStaysReachableAndInBoundsWithFiveQuickTiles(){
  for(footers in 3..5)for(media in 0..4){
   val valid=HomeFocusPolicy.nodes(media,footers).toSet();val seen=mutableSetOf<FocusNode>();val queue=java.util.ArrayDeque<FocusNode>();queue.add(FocusNode("nav",0))
   while(queue.isNotEmpty()){val node=queue.removeFirst();if(!seen.add(node))continue
    for(direction in listOf("up","down","left","right")){val target=HomeFocusPolicy.target(node,direction,media,footers);assertTrue("$node $direction -> $target",target in valid);queue.add(target)}}
   assertTrue((0 until footers).all{FocusNode("footer",it) in seen})
  }
  assertEquals(FocusNode("footer",4),HomeFocusPolicy.target(FocusNode("footer",3),"right",2,5))
  assertEquals(FocusNode("photo",3),HomeFocusPolicy.target(FocusNode("footer",4),"up",2,5))
  assertEquals(FocusNode("footer",2),HomeFocusPolicy.target(FocusNode("photo",1),"down",2,5))
 }
 @Test fun threeTilesKeepTheOriginalFocusMap(){
  for(i in 0..2)assertEquals(FocusNode("photo",if(i==2)3 else i),HomeFocusPolicy.target(FocusNode("footer",i),"up",2))
  assertEquals(FocusNode("footer",1),HomeFocusPolicy.target(FocusNode("photo",1),"down",2))
 }
}
