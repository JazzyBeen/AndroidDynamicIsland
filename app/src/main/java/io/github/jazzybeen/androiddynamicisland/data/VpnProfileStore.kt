package io.github.jazzybeen.androiddynamicisland.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Credentials and tunnel configuration never enter preferences, backups, or logs. */
class VpnProfileStore(context:Context){
    private val file=AtomicFile(File(context.noBackupFilesDir,"vpn-profile.enc"))
    fun exists()=file.baseFile.exists()
    @Synchronized fun read():JSONObject?{
        if(!exists())return null
        val bytes=file.openRead().use{it.readBytes()}
        require(bytes.size>29&&bytes[0]==1.toByte()){"Invalid encrypted profile"}
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(1,13)))
        return JSONObject(String(cipher.doFinal(bytes.copyOfRange(13,bytes.size)),Charsets.UTF_8))
    }
    @Synchronized fun write(profile:JSONObject){
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key())
        val data=byteArrayOf(1)+cipher.iv+cipher.doFinal(profile.toString().toByteArray(Charsets.UTF_8))
        val output=file.startWrite()
        try{output.write(data);file.finishWrite(output)}catch(error:Exception){file.failWrite(output);throw error}
    }
    @Synchronized fun clear(){file.delete()}
    private fun key():SecretKey{
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (store.getKey(ALIAS,null) as? SecretKey)?.let{return it}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{
            init(KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }
    companion object{private const val ALIAS="island-vpn-profile-v1"}
}
