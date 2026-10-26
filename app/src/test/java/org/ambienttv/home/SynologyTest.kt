package org.ambienttv.home

import java.net.ServerSocket
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test
import java.net.URLDecoder

class SynologyTest {
 @Test fun catalogSortsDatesAndRejectsOtherPersonsWhenNasIgnoresFilter() = withServer("""{"success":true,"data":{"list":[{"id":1,"type":"photo","time":100,"additional":{"person":[{"id":7}]}},{"id":2,"type":"live","time":300,"additional":{"person":[{"id":8}]}},{"id":3,"type":"photo","time":200,"additional":{"person":[{"id":7}]}}]}}""") { api,request ->
  assertEquals(listOf(3,1),api.photoCatalog(7).map{it.getInt("id")})
  assertEquals("7",request()["person_id"]);assertEquals("500",request()["limit"])
  assertEquals("7",request()["version"])
 }
 @Test fun missingPersonMembershipFailsClosed() = withServer("""{"success":true,"data":{"list":[{"id":1,"type":"photo","time":100}]}}""") {api,_->assertThrows(SafeFailure::class.java){api.photoCatalog(7)}}
 private fun withServer(response: String, test: (Synology, ()->Map<String,String>)->Unit) {
  var request = emptyMap<String,String>()
  val server=ServerSocket(0)
  val serving=thread(isDaemon=true) {
   server.accept().use { socket ->
    val reader=socket.getInputStream().bufferedReader()
    val startLine=reader.readLine()
    var hasCookie=false
    var length=0
    while(true) {
     val line=reader.readLine() ?: break
     if(line.isEmpty()) break
     if(line.equals("Cookie: id=test-session",true)) hasCookie=true
     if(line.startsWith("Content-Length:",true)) length=line.substringAfter(':').trim().toInt()
    }
    val body=CharArray(length); var n=0
    while(n<length) { val count=reader.read(body,n,length-n); if(count<0) break; n+=count }
    val encoded=if(startLine.startsWith("GET ")) startLine.substringAfter('?').substringBefore(' ') else String(body)
    request=encoded.split('&').associate {
     val p=it.split('=',limit=2); URLDecoder.decode(p[0],"UTF-8") to URLDecoder.decode(p[1],"UTF-8")
    } + mapOf("__method" to startLine.substringBefore(' '), "__cookie" to hasCookie.toString())
    val bytes=response.toByteArray()
    socket.getOutputStream().apply {
     write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
     write(bytes); flush()
    }
   }
  }
  try { test(Synology("http://127.0.0.1:${server.localPort}","test-session")) { request } } finally { server.close(); serving.join(1000) }
 }
 @Test fun personalPersonIsMatchedExactly() = withServer("""{"success":true,"data":{"list":[{"id":7,"name":"Demo Person"},{"id":8,"name":"Someone else"}]}}""") { api, request ->
  assertEquals(7,api.personId("Demo Person")); assertEquals("SYNO.Foto.Browse.Person",request()["api"])
 }
 @Test fun ambiguousPersonFailsClosed() = withServer("""{"success":true,"data":{"list":[{"id":7,"name":"Demo Person"},{"id":8,"name":"Demo Person"}]}}""") { api,_ ->
  assertThrows(IllegalArgumentException::class.java) { api.personId("Demo Person") }
 }
 @Test fun newestRequestIsBoundedAndPersonScoped() = withServer("""{"success":true,"data":{"list":[{"id":1,"type":"photo","time":200},{"id":2,"type":"photo","time":100}]}}""") { api,request ->
  assertEquals(2,api.newest(7).size)
  assertEquals("7",request()["person_id"]); assertEquals("\"desc\"",request()["sort_direction"])
  assertEquals("\"takentime\"",request()["sort_by"]); assertEquals("48",request()["limit"])
  assertEquals("SYNO.Foto.Browse.Item",request()["api"])
 }
 @Test fun livePhotosAreStillPhotos() = withServer("""{"success":true,"data":{"list":[{"id":1,"type":"live","time":200},{"id":2,"type":"photo","time":100}]}}""") { api,_ ->
  assertEquals(2,api.newest(7).size)
 }
 @Test fun archiveRequestIsPersonScopedAndOldestFirst() = withServer("""{"success":true,"data":{"list":[{"id":1,"type":"photo","time":100},{"id":2,"type":"live","time":200}]}}""") { api,request ->
  assertEquals(2,api.photoPage(7,32,48,true).size)
  assertEquals("7",request()["person_id"]);assertEquals("32",request()["offset"])
  assertEquals("\"asc\"",request()["sort_direction"]);assertEquals("48",request()["limit"])
 }
 @Test fun videosCannotBecomeWallpapers() = withServer("""{"success":true,"data":{"list":[{"id":1,"type":"video","time":200}]}}""") { api,_ ->
  assertThrows(SafeFailure::class.java) { api.newest(7) }
 }
 @Test fun incorrectOrderingFailsClosed() = withServer("""{"success":true,"data":{"list":[{"id":1,"type":"photo","time":100},{"id":2,"type":"photo","time":200}]}}""") { api,_ ->
  assertThrows(IllegalArgumentException::class.java) { api.newest(7) }
 }
 @Test fun expiredSessionIsDistinct() = withServer("""{"success":false,"error":{"code":119}}""") { api,_ ->
  assertEquals(119,assertThrows(ApiFailure::class.java) { api.personId("Demo Person") }.code)
 }
 @Test fun thumbnailUsesGetCookieAndUnitId() = withServer("JPEGTEST") { api,request ->
  val item=org.json.JSONObject("""{"id":1,"additional":{"thumbnail":{"unit_id":2,"cache_key":"test-cache","xl":"ready"}}}""")
  assertEquals("JPEGTEST",String(api.thumbnail(item)))
  assertEquals("GET",request()["__method"]); assertEquals("true",request()["__cookie"])
  assertEquals("2",request()["id"]); assertEquals("xl",request()["size"])
  assertEquals("1",request()["version"]); assertEquals("download",request()["mode"])
  assertFalse(request().containsKey("_sid"))
 }
 @Test fun thumbnailAuthFailureIsRecognized() = withServer("""{"success":false,"error":{"code":119}}""") { api,_ ->
  assertEquals(119,assertThrows(ApiFailure::class.java) { api.thumbnail(org.json.JSONObject("""{"id":1}""")) }.code)
 }
 @Test fun baseRejectsEmbeddedCredentialsAndUntrustedHttp() {
  assertThrows(IllegalArgumentException::class.java) { Synology.validateBase("https://user:pass@example.com") }
  assertThrows(IllegalArgumentException::class.java) { Synology.validateBase("http://example.com") }
  assertEquals("http://10.0.0.2:5000",Synology.validateBase("http://10.0.0.2:5000/"))
 }
}
