package io.github.jazzybeen.androiddynamicisland.navigation

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class MapKeyStore(context:Context){
    private val file=AtomicFile(File(context.noBackupFilesDir,"mapkit-key.enc"))
    fun exists()=file.baseFile.exists()
    fun read():String=runCatching{val b=file.openRead().use{it.readBytes()};require(b.size in 30..2048&&b[0]==1.toByte());val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,b.copyOfRange(1,13)));String(c.doFinal(b.copyOfRange(13,b.size)),Charsets.UTF_8)}.getOrDefault("")
    fun write(value:String){require(value.trim().matches(Regex("[A-Za-z0-9_-]{16,256}"))){"Проверь API-ключ MapKit"};val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());val out=file.startWrite();try{out.write(byteArrayOf(1)+c.iv+c.doFinal(value.trim().toByteArray(Charsets.UTF_8)));file.finishWrite(out)}catch(e:Exception){file.failWrite(out);throw e}}
    fun clear(){file.delete()}
    private fun key():SecretKey{val alias="island-mapkit-key-v1";val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)};(store.getKey(alias,null) as? SecretKey)?.let{return it};return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())}.generateKey()}
}
