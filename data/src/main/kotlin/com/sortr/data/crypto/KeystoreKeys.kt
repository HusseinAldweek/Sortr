package com.sortr.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Keys held by the Android Keystore. They can be used by this app on this phone but can't be
 * exported, so uninstalling the app (which deletes them) makes the vault unreadable.
 */
object KeystoreKeys {

    private const val PROVIDER = "AndroidKeyStore"
    private const val VAULT_MASTER = "sortr_vault_master_v1"
    private const val PIN_HMAC = "sortr_vault_pin_v1"

    private val lock = Any()

    fun vaultMasterKey(): SecretKey = getOrCreate(VAULT_MASTER) {
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply {
            init(
                KeyGenParameterSpec.Builder(VAULT_MASTER, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    fun pinHmacKey(): SecretKey = getOrCreate(PIN_HMAC) {
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, PROVIDER).apply {
            init(KeyGenParameterSpec.Builder(PIN_HMAC, KeyProperties.PURPOSE_SIGN).build())
        }.generateKey()
    }

    private fun getOrCreate(alias: String, create: () -> SecretKey): SecretKey = synchronized(lock) {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey) ?: create()
    }
}
