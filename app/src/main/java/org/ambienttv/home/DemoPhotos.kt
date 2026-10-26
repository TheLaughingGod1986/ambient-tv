package org.ambienttv.home

import android.content.Context
import java.io.File

/** Only bundled, generated scenery. Never loads a gallery, account, or remote image. */
internal object DemoPhotos {
 @Synchronized fun files(c:Context):List<File> {
  val directory=File(c.filesDir,"demo").apply{mkdirs()}
  return listOf("lake.jpg","coast.jpg","forest.jpg").mapIndexed{index,asset->
   File(directory,"0-${index+1}.jpg").also{target->
    if(!target.isFile || target.length()==0L){
     val temporary=File(directory,target.name+".tmp")
     c.assets.open("demo/$asset").use{input->temporary.outputStream().use{input.copyTo(it)}}
     check(temporary.renameTo(target))
    }
   }
  }
 }
}
