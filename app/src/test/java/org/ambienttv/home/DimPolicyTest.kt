package org.ambienttv.home
import org.junit.Assert.*
import org.junit.Test
class DimPolicyTest {
 private val utc=java.util.TimeZone.getTimeZone("UTC")
 private fun at(hour:Int,minute:Int=0)=1792540800000L+hour*3600000L+minute*60000L // a UTC midnight plus the time of day
 @Test fun offUnlessEnabled(){assertFalse(DimPolicy.active(at(23),false,utc))}
 @Test fun dimsFromNineInTheEveningUntilSevenInTheMorning(){
  assertFalse(DimPolicy.active(at(20,59),true,utc));assertTrue(DimPolicy.active(at(21),true,utc))
  assertTrue(DimPolicy.active(at(2),true,utc));assertTrue(DimPolicy.active(at(6,59),true,utc))
  assertFalse(DimPolicy.active(at(7),true,utc));assertFalse(DimPolicy.active(at(13),true,utc))
 }
 @Test fun followsTheGivenZone(){assertTrue(DimPolicy.active(at(13),true,java.util.TimeZone.getTimeZone("Asia/Tokyo")))}
}
