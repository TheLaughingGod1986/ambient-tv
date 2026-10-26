package org.ambienttv.home
import org.junit.Test
import org.junit.Assert.*
class WatchNextPolicyTest {
 @Test fun missingProgressIsNotInventedAndLargeValuesStayBounded(){assertNull(WatchNextPolicy.progress(-1,100));assertNull(WatchNextPolicy.progress(20,0));assertEquals(250,WatchNextPolicy.progress(30,120));assertEquals(1000,WatchNextPolicy.progress(Long.MAX_VALUE,Long.MAX_VALUE))}
 @Test fun foreignDeepLinksFallBackToOwningApp(){assertTrue(WatchNextPolicy.sameOwner("com.plexapp.android","com.plexapp.android"));assertFalse(WatchNextPolicy.sameOwner("com.plexapp.android","unexpected.app"));assertFalse(WatchNextPolicy.sameOwner("",null))}
}
