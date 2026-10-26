package org.ambienttv.home
import org.junit.Assert.*
import org.junit.Test
class TriptychTest {
 @Test fun distinctAndWrap() { assertEquals(listOf(4,1,2),TriptychPolicy.group(listOf(1,2,3,4),4)) }
 @Test fun smallCaches() {
  assertEquals(emptyList<Int>(),TriptychPolicy.group(emptyList(),1))
  assertEquals(listOf(1),TriptychPolicy.group(listOf(1,1),1))
  assertEquals(listOf(2,1),TriptychPolicy.group(listOf(1,2),2))
 }
 @Test fun coherentSetsCoverAllTwelve() {
  val items=(1..12).toList()
  assertEquals(listOf(1,4,7,10),TriptychPolicy.anchors(items))
  assertEquals(items,TriptychPolicy.anchors(items).flatMap { TriptychPolicy.group(items,it) })
 }
 @Test fun staleAnchorUsesCurrentSet() { assertEquals(listOf(1,2,3),TriptychPolicy.group(listOf(1,2,3),99)) }
}
