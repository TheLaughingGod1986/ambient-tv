package org.ambienttv.home
import org.junit.Assert.*
import org.junit.Test
class ResumePolicyTest {
 @Test fun retainsRealUnfinishedProgress(){assertTrue(ResumePolicy.isUnfinished(2420375,6050111))}
 @Test fun excludesCompletedNearEndAndMissingProgress(){assertFalse(ResumePolicy.isUnfinished(87195,87285));assertFalse(ResumePolicy.isUnfinished(0,60000));assertFalse(ResumePolicy.isUnfinished(-1,-1));assertFalse(ResumePolicy.isUnfinished(60000,60000))}
}
