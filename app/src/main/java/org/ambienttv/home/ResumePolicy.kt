package org.ambienttv.home
object ResumePolicy {
 fun isUnfinished(position:Long,duration:Long)=position>0 && duration>position && duration-position>=30_000
}
