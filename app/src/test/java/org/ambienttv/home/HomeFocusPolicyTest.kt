package org.ambienttv.home
import org.junit.Test
import org.junit.Assert.*
class HomeFocusPolicyTest {
 @Test fun everyVisibleHomeControlIsReachableForFallbackAndFullFeeds(){
  for(media in 0..5){
   val valid=HomeFocusPolicy.nodes(media).toSet();val seen=mutableSetOf<FocusNode>();val queue=java.util.ArrayDeque<FocusNode>();queue.add(FocusNode("nav",0))
   while(queue.isNotEmpty()){val node=queue.removeFirst();if(!seen.add(node))continue;for(direction in listOf("up","down","left","right")){val target=HomeFocusPolicy.target(node,direction,media);assertTrue(target in valid);queue.add(target)}}
   assertEquals(valid,seen)
  }
 }
 @Test fun remoteMovesAcrossPhotoFeedAndFooterAndBackToSidebar(){
  var n=FocusNode("nav",0)
  for(dir in listOf("right","right","down","down","left","left"))n=HomeFocusPolicy.target(n,dir,2)
  assertEquals(FocusNode("nav",0),n)
 }
 @Test fun topAndBottomDoNotWrapIntoUnexpectedApps(){assertEquals(FocusNode("nav",0),HomeFocusPolicy.target(FocusNode("nav",0),"up",2));assertEquals(FocusNode("footer",3),HomeFocusPolicy.target(FocusNode("footer",3),"down",2))}
}
