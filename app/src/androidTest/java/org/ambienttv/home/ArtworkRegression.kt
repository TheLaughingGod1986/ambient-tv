package org.ambienttv.home

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Loopback-only artwork checks: no media accounts or private images. */
internal object ArtworkRegression {
 fun run(c:Context){
  val image=Bitmap.createBitmap(32,18,Bitmap.Config.ARGB_8888)
  val data=ByteArrayOutputStream().also{image.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();image.recycle()
  // The literal loopback address: resolving the name "localhost" fails intermittently on a fresh emulator.
  val server=ServerSocket(0,8,InetAddress.getLoopbackAddress())
  val requests=AtomicInteger()
  val thread=Thread{
   try{while(!server.isClosed)server.accept().use{socket->
    socket.soTimeout=3000
    val reader=socket.getInputStream().bufferedReader();val request=reader.readLine().orEmpty()
    while(!reader.readLine().isNullOrEmpty())Unit
    requests.incrementAndGet()
    val response=when{
     request.contains("/redirect ")->"HTTP/1.1 302 Found\r\nLocation: /image\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
     request.contains("/bad ")->"HTTP/1.1 302 Found\r\nLocation: file:///unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
     else->"HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: ${data.size}\r\nConnection: close\r\n\r\n"
    }
    socket.getOutputStream().apply{write(response.toByteArray());if(response.startsWith("HTTP/1.1 200"))write(data);flush()}
   }}catch(_:java.io.IOException){ /* Closed by the test's finally. */ }
  }.apply{isDaemon=true;start()}
  try{
   WatchNextReader.trimArtwork()
   val url="http://127.0.0.1:${server.localPort}/redirect"
   val first=checkNotNull(WatchNextReader.art(c,url));check(first.width==32 && first.height==18);first.recycle()
   val before=requests.get();check(before==2)
   val second=checkNotNull(WatchNextReader.art(c,url));check(!second.isRecycled);second.recycle()
   check(requests.get()==before) // Cache owns bytes, never a recycled view bitmap.
   check(WatchNextReader.art(c,"http://127.0.0.1:${server.localPort}/bad")==null)
   WatchNextReader.trimArtwork()
   checkNotNull(WatchNextReader.art(c,url)).recycle();check(requests.get()==before+3)
  }finally{server.close();thread.join(3000);WatchNextReader.trimArtwork()}
  val held=CountDownLatch(1);val release=CountDownLatch(1);val pool=Executors.newFixedThreadPool(2)
  try{
   pool.submit{synchronized(PlexFeed){held.countDown();release.await(5,TimeUnit.SECONDS)}}
   check(held.await(2,TimeUnit.SECONDS))
   pool.submit{check(PlexFeed.art(c,Uri.parse("plex-art://missing/none"))==null)}.get(1,TimeUnit.SECONDS)
  }finally{release.countDown();pool.shutdownNow()}
 }
}
