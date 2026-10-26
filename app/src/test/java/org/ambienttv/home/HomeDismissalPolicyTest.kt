package org.ambienttv.home
import org.junit.Assert.*
import org.junit.Test
class HomeDismissalPolicyTest {
 private val yt="com.google.android.youtube.tv"
 @Test fun sameVideoSurvivesRowRecreationAndPlaybackOffsets(){
  val a=HomeDismissalPolicy.identity(yt,"https://www.youtube.com/watch?v=abcdefghijk&t=120")
  assertEquals(a,HomeDismissalPolicy.identity(yt,"https://youtu.be/abcdefghijk?t=800"))
  assertEquals(a,HomeDismissalPolicy.identity(yt,"intent:#Intent;S.video_id=abcdefghijk;i.position=300;end"))
  assertNotEquals(a,HomeDismissalPolicy.identity(yt,"https://www.youtube.com/watch?v=lmnopqrstuv"))
 }
 @Test fun intentExtrasNeverCollapseDifferentContentIntoSameBase(){
  val a="intent://watch#Intent;S.content_id=episode-one;l.position=100;end"
  val b="intent://watch#Intent;S.content_id=episode-two;l.position=100;end"
  assertNotEquals(HomeDismissalPolicy.identity(yt,a),HomeDismissalPolicy.identity(yt,b))
  assertEquals(HomeDismissalPolicy.identity(yt,a),HomeDismissalPolicy.identity(yt,a.replace("position=100","position=800")))
 }
 @Test fun plexIdentityIncludesServerAndItemAndSeparatesSources(){
  val link=PlexProtocol.detailUri("server-1","123")
  assertNotEquals(HomeDismissalPolicy.identity("plex",link),HomeDismissalPolicy.identity("plex",PlexProtocol.detailUri("server-2","123")))
  assertNotEquals(HomeDismissalPolicy.identity("plex",link),HomeDismissalPolicy.identity("plex",PlexProtocol.detailUri("server-1","124")))
  assertNotEquals(HomeDismissalPolicy.identity(yt,link),HomeDismissalPolicy.identity("plex",link))
 }
 @Test fun onlyKnownCompletedProgressIsFiltered(){
  assertFalse(HomeDismissalPolicy.completed(-1,-1));assertFalse(HomeDismissalPolicy.completed(0,20000));assertFalse(HomeDismissalPolicy.completed(12000,-1))
  assertFalse(HomeDismissalPolicy.completed(1476000,1940000));assertTrue(HomeDismissalPolicy.completed(1930000,1940000))
  assertTrue(HomeDismissalPolicy.completed(20000,20000));assertTrue(HomeDismissalPolicy.completed(21000,20000))
  assertFalse(HomeDismissalPolicy.completed(5000,20000));assertTrue(HomeDismissalPolicy.completed(19500,20000))
 }
 @Test fun heldRemoteOkNeverAlsoClicksAndBackCancellationDoesNotLaunch(){
  val p=RemoteCardPress();assertTrue(p.down(false));assertTrue(p.hold());assertFalse(p.down(true));assertFalse(p.hold());assertFalse(p.up(false));assertFalse(p.up(false))
  p.down(false);p.cancel();assertFalse(p.up(false))
  p.down(false);assertFalse(p.up(true))
  p.down(false);assertTrue(p.up(false));assertFalse(p.up(false))
 }
}
