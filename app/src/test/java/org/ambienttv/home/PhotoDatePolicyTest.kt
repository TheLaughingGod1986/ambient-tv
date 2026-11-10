package org.ambienttv.home
import org.junit.Assert.*
import org.junit.Test
class PhotoDatePolicyTest {
 @Test fun blankMeansNoLimit(){assertNull(PhotoDatePolicy.parse("  "));assertTrue(PhotoDatePolicy.keep(0,null))}
 @Test fun parsesTheStartOfTheDay(){assertEquals(1704067200L,PhotoDatePolicy.parse(" 2024-01-01 "))}
 @Test fun keepsThatDayAndLaterOnly(){
  val earliest=PhotoDatePolicy.parse("2024-01-01")
  assertFalse(PhotoDatePolicy.keep(1704067199L,earliest))
  assertTrue(PhotoDatePolicy.keep(1704067200L,earliest))
  assertTrue(PhotoDatePolicy.keep(1800000000L,earliest))
 }
 @Test fun rejectsOtherShapesAndImpossibleDates(){
  for(bad in listOf("01/02/2024","2024-1-1","2024-02-30","2024-13-01","yesterday","1800-01-01"))
   try{PhotoDatePolicy.parse(bad);fail("accepted $bad")}catch(_:IllegalArgumentException){}
 }
 @Test fun acceptsLeapDay(){assertNotNull(PhotoDatePolicy.parse("2024-02-29"))}
}
