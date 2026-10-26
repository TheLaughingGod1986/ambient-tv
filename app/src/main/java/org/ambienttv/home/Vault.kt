package org.ambienttv.home

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Only the revocable session is persisted; passwords and OTPs never are. */
class Vault(context: Context, private val namespace:String="session",private val alias:String="ambient-tv-session") {
 private val prefs = context.getSharedPreferences(namespace, Context.MODE_PRIVATE)
 private fun key(): SecretKey {
  val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  (store.getKey(alias, null) as? SecretKey)?.let { return it }
  return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
   init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
  }.generateKey()
 }
 fun save(base: String, sid: String) {
  val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
  val data = cipher.doFinal(sid.toByteArray(Charsets.UTF_8))
  prefs.edit().putString("base", base).putString("sid", Base64.encodeToString(data, Base64.NO_WRAP))
   .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).commit()
 }
 fun read(): Pair<String, String>? = try {
  val base = prefs.getString("base", null)
  val data = prefs.getString("sid", null)
  val iv = prefs.getString("iv", null)
  if (base == null || data == null || iv == null) null else {
   val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
    init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
   }
   base to String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
  }
 } catch (_: Exception) { null }
 fun clear() { prefs.edit().clear().commit() }
}
