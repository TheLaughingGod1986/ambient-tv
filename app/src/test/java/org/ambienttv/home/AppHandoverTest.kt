package org.ambienttv.home
import org.junit.Assert.*
import org.junit.Test
class AppHandoverTest {
 private val self="org.ambienttv.home"
 @Test fun openingADifferentAppClosesTheOneLeftBehind(){
  assertEquals("com.example.films",AppHandover.toClose("com.example.films","com.example.videos",self))
 }
 @Test fun goingBackToTheSameAppClosesNothing(){
  assertNull(AppHandover.toClose("com.example.films","com.example.films",self))
 }
 @Test fun theFirstAppOpenedClosesNothing(){
  assertNull(AppHandover.toClose(null,"com.example.videos",self));assertNull(AppHandover.toClose("","com.example.videos",self))
 }
 @Test fun homeIsNeverClosedAndNeverCountsAsAnApp(){
  assertNull(AppHandover.toClose(self,"com.example.videos",self));assertNull(AppHandover.toClose("com.example.films",self,self))
  assertNull(AppHandover.toClose("com.example.films","",self))
 }
}
