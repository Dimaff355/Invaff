package ru.invaff.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Хранилище токенов: AES-GCM ключом из Android Keystore (ключ не покидает устройство). */
class Vault(context: Context) {
    private val prefs = context.getSharedPreferences("vault", Context.MODE_PRIVATE)

    private val key: SecretKey
        get() = (KeyStore.getInstance(STORE).apply { load(null) }.getKey(ALIAS, null) as SecretKey?)
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE).apply {
                init(
                    KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
            }.generateKey()

    operator fun get(name: String): String? = prefs.getString(name, null)?.let {
        runCatching {
            val b = Base64.decode(it, Base64.NO_WRAP)
            val c = Cipher.getInstance(MODE).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, b, 0, 12)) }
            String(c.doFinal(b, 12, b.size - 12))
        }.getOrNull()
    }

    operator fun set(name: String, value: String?) {
        val e = prefs.edit()
        if (value.isNullOrBlank()) e.remove(name) else {
            val c = Cipher.getInstance(MODE).apply { init(Cipher.ENCRYPT_MODE, key) }
            e.putString(name, Base64.encodeToString(c.iv + c.doFinal(value.toByteArray()), Base64.NO_WRAP))
        }
        e.apply()
    }

    private companion object {
        const val STORE = "AndroidKeyStore"
        const val ALIAS = "tokens"
        const val MODE = "AES/GCM/NoPadding"
    }
}
