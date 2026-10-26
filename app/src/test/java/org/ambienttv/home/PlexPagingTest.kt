package org.ambienttv.home
import org.junit.Assert.*
import org.junit.Test
class PlexPagingTest {
 private fun items(n:Int)=(1..n).map{PlexPoster(if(it%2==0)"a" else "b",it.toString(),"Title $it","",(100-it/3).toLong())}
 @Test fun mergedServerPagesHaveNoRepeatsOrGapsEvenForTiedDates(){
  val pool=items(37);val pages=(0..3).flatMap{PlexCatalogPolicy.page(pool,it)}
  assertEquals(37,pages.size);assertEquals(37,pages.map{it.server+":"+it.key}.distinct().size)
  assertEquals(pool.map{it.key}.toSet(),pages.map{it.key}.toSet())
  assertEquals(pages,(0..3).flatMap{PlexCatalogPolicy.page(pool.reversed(),it)})
 }
 @Test fun partialAndEmptyLibrariesNeverInventExtraPosters(){
  for(n in 0..10){val pool=items(n);assertEquals(n,PlexCatalogPolicy.page(pool+pool,0).size);assertTrue(PlexCatalogPolicy.page(pool,1).isEmpty())}
 }
 @Test fun allBoundedPagesContainAtMostTwoCompleteFiveColumnRows(){
  val pool=items(100)
  for(i in 0..9)assertEquals(10,PlexCatalogPolicy.page(pool,i).size)
 }
}
