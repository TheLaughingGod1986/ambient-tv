package org.ambienttv.home
import org.junit.Assert.*
import org.junit.Test
class SynologyApiPolicyTest {
 @Test fun advertisedCgiPathsStayInsideWebapi(){assertEquals("Foto/entry.cgi",SynologyApiPolicy.path("Foto/entry.cgi"));for(p in listOf("../entry.cgi","https://example.com/entry.cgi","/entry.cgi","entry.cgi?token=x")){try{SynologyApiPolicy.path(p);fail(p)}catch(_:IllegalArgumentException){}}}
 @Test fun advertisedVersionBoundsAreRespected(){assertEquals(7,SynologyApiPolicy.version(7,1,9));assertEquals(6,SynologyApiPolicy.version(7,1,6))}
}
