package org.ambienttv.home
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PlexMemoryTrimTest {
 @Test fun memoryTrimDoesNotWaitForPlexNetworkMonitor(){
  val held=CountDownLatch(1);val release=CountDownLatch(1)
  val executor=Executors.newFixedThreadPool(2)
  try {
   val network=executor.submit { synchronized(PlexFeed){held.countDown();release.await(5,TimeUnit.SECONDS)} }
   check(held.await(2,TimeUnit.SECONDS))
   executor.submit { PlexFeed.trimArtwork() }.get(1,TimeUnit.SECONDS)
   release.countDown();network.get(2,TimeUnit.SECONDS)
  } finally {release.countDown();executor.shutdownNow()}
 }
}
