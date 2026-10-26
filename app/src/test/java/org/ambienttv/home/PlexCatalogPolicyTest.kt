package org.ambienttv.home
import org.junit.Assert.*
import org.junit.Test
class PlexCatalogPolicyTest {
 @Test fun animeIsExplicitNotAllAnimation(){assertTrue(PlexCatalogPolicy.namedAnime("Anime TV"));assertFalse(PlexCatalogPolicy.namedAnime("Animation"));assertFalse(PlexCatalogPolicy.sectionMatches("Anime","show","Cartoons"))}
 @Test fun japaneseAnimationRequiresBothMetadataTags(){assertTrue(PlexCatalogPolicy.animeMetadata(listOf("Animation"),listOf("Japan")));assertFalse(PlexCatalogPolicy.animeMetadata(listOf("Animation"),listOf("United States")));assertFalse(PlexCatalogPolicy.animeMetadata(listOf("Drama"),listOf("Japan")))}
 @Test fun categoriesRespectTypeAndDedicatedAnime(){assertTrue(PlexCatalogPolicy.sectionMatches("TV Shows","show","Television"));assertTrue(PlexCatalogPolicy.sectionMatches("Movies","movie","Films"));assertFalse(PlexCatalogPolicy.sectionMatches("TV Shows","movie","Films"));assertFalse(PlexCatalogPolicy.sectionMatches("Movies","movie","Anime Movies"))}
}
