package org.ambienttv.home
import org.junit.Assert.*
import org.junit.Test
class BackupPolicyTest {
 private val value=PreferenceSnapshot("hero",0,"auto",mapOf("123.jpg" to "full"),setOf("123.jpg","456.jpg"))
 @Test fun roundTripPreservesOnlyUserPresentationChoices(){val encoded=BackupPolicy.encode(value);assertEquals(value,BackupPolicy.decode(encoded));assertFalse(encoded.contains("token"));assertFalse(encoded.contains("server"))}
 @Test fun rejectsUnexpectedCredentialFields(){val raw=org.json.JSONObject(BackupPolicy.encode(value)).put("token","not-a-secret");assertThrows(IllegalArgumentException::class.java){BackupPolicy.decode(raw.toString())}}
 @Test fun rejectsPathTraversal(){val raw=BackupPolicy.encode(value).replace("123.jpg","../secret");assertThrows(IllegalArgumentException::class.java){BackupPolicy.decode(raw)}}
 @Test fun rejectsNewerVersionsWithoutPartialImport(){val raw=BackupPolicy.encode(value).replace("\"version\": 1","\"version\": 999");assertThrows(IllegalArgumentException::class.java){BackupPolicy.decode(raw)}}
 @Test fun rejectsOversizedFiles(){assertThrows(IllegalArgumentException::class.java){BackupPolicy.decode(" ".repeat(BackupPolicy.MAX_BYTES+1))}}
 @Test fun rejectsFractionalColumnCounts(){val raw=org.json.JSONObject(BackupPolicy.encode(value)).put("columns",1.5);assertThrows(IllegalArgumentException::class.java){BackupPolicy.decode(raw.toString())}}
}
