package com.example.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.SecureRandom
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class SecureCredentials(
    val libreEmail: String = "",
    val librePassword: String = "",
    val libreToken: String = "",
    val libreTokenExpiresMs: Long = 0L,
    val libreUserId: String = "",
    val libreRegion: String = "",
    val librePatientId: String = "",
    val librePatientName: String = "",
    val nightscoutUrl: String = "",
    val nightscoutToken: String = "",
    val nightscoutSecret: String = "",
    val jugglucoUrl: String = "",
    val jugglucoToken: String = "",
    val pinHash: String = ""
)

class KeystoreManager(private val context: Context) {

    companion object {
        private const val TAG = "KeystoreManager"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "GlucoseFrameMasterKey"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val CREDENTIALS_FILE = "security_credentials.dat"
        private const val LEGACY_KEY_FILE = "legacy_master.key"
        private val TEST_KEYSTORE_PASSWORD = "glucose_frame_test_keystore".toCharArray()
    }

    private val file = File(context.filesDir, CREDENTIALS_FILE)
    private val atomicFile = AtomicFile(file)

    private val _credentialsUnreadable = MutableStateFlow(false)
    val credentialsUnreadable: StateFlow<Boolean> = _credentialsUnreadable.asStateFlow()

    // AndroidKeyStore only supports AES keys from Android 6 (API 23). On Android 5 (API 21-22)
    // and in JVM tests we use a random AES key stored in a private app file instead.
    private val keyStore: KeyStore = try {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            throw IllegalStateException("AndroidKeyStore AES not supported below API 23")
        }
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    } catch (e: Exception) {
        KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, TEST_KEYSTORE_PASSWORD) }
    }

    // fallbackKey is ONLY used when keyStore.type != ANDROID_KEYSTORE (Android 5 / Robolectric / JVM tests).
    // It is persisted in a private file so credentials survive app restarts.
    private var fallbackKey: SecretKey? = null
    private val legacyKeyFile = File(context.filesDir, LEGACY_KEY_FILE)

    private fun loadOrCreateFileKey(): SecretKey {
        try {
            if (legacyKeyFile.exists()) {
                val bytes = legacyKeyFile.readBytes()
                if (bytes.size == 32) return SecretKeySpec(bytes, "AES")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al leer clave legacy: ${e.javaClass.simpleName}")
        }
        val keyBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        try {
            FileOutputStream(legacyKeyFile).use { it.write(keyBytes) }
            legacyKeyFile.setReadable(false, false)
            legacyKeyFile.setReadable(true, true)
            legacyKeyFile.setWritable(false, false)
            legacyKeyFile.setWritable(true, true)
        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar clave legacy: ${e.javaClass.simpleName}")
        }
        return SecretKeySpec(keyBytes, "AES")
    }

    init {
        ensureKeyExists()
    }

    private fun ensureKeyExists() {
        try {
            if (keyStore.type == ANDROID_KEYSTORE) {
                if (!keyStore.containsAlias(KEY_ALIAS) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    generateAndroidKeystoreKey()
                }
            } else if (fallbackKey == null) {
                fallbackKey = loadOrCreateFileKey()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error asegurando existencia de clave: ${e.javaClass.simpleName}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun generateAndroidKeystoreKey() {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val parameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(parameterSpec)
        keyGenerator.generateKey()
    }

    private fun getSecretKey(): SecretKey {
        return if (keyStore.type == ANDROID_KEYSTORE) {
            (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)
                ?: throw IllegalStateException("Clave maestra no encontrada en AndroidKeyStore")
        } else {
            // Environment fallback for tests only
            fallbackKey ?: loadOrCreateFileKey().also { fallbackKey = it }
        }
    }

    private fun regenerateKey() {
        try {
            if (keyStore.type == ANDROID_KEYSTORE) {
                if (keyStore.containsAlias(KEY_ALIAS)) {
                    keyStore.deleteEntry(KEY_ALIAS)
                }
                ensureKeyExists()
            } else {
                legacyKeyFile.delete()
                fallbackKey = loadOrCreateFileKey()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al regenerar clave maestra: ${e.javaClass.simpleName}")
        }
    }

    @Synchronized
    private fun loadCredentialsOrNull(): SecureCredentials? {
        if (!file.exists()) return SecureCredentials()
        val bytes = try {
            atomicFile.openRead().use { it.readBytes() }
        } catch (e: Exception) {
            Log.e(TAG, "Error al leer archivo de credenciales: ${e.javaClass.simpleName}")
            return null
        }

        if (bytes.size < GCM_IV_LENGTH) return null

        val iv = bytes.copyOfRange(0, GCM_IV_LENGTH)
        val cipherText = bytes.copyOfRange(GCM_IV_LENGTH, bytes.size)

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            val plainBytes = cipher.doFinal(cipherText)
            val jsonStr = String(plainBytes, Charsets.UTF_8)
            val json = JSONObject(jsonStr)

            _credentialsUnreadable.value = false
            SecureCredentials(
                libreEmail = json.optString("libreEmail", ""),
                librePassword = json.optString("librePassword", ""),
                libreToken = json.optString("libreToken", ""),
                libreTokenExpiresMs = json.optLong("libreTokenExpiresMs", 0L),
                libreUserId = json.optString("libreUserId", ""),
                libreRegion = json.optString("libreRegion", ""),
                librePatientId = json.optString("librePatientId", ""),
                librePatientName = json.optString("librePatientName", ""),
                nightscoutUrl = json.optString("nightscoutUrl", ""),
                nightscoutToken = json.optString("nightscoutToken", ""),
                nightscoutSecret = json.optString("nightscoutSecret", ""),
                jugglucoUrl = json.optString("jugglucoUrl", ""),
                jugglucoToken = json.optString("jugglucoToken", ""),
                pinHash = json.optString("pinHash", "")
            )
        } catch (e: Exception) {
            val isUnrecoverable = when (e) {
                is AEADBadTagException,
                is InvalidKeyException,
                is UnrecoverableKeyException,
                is JSONException -> true
                is IllegalStateException -> e.message?.contains("no encontrada", ignoreCase = true) == true
                else -> false
            }

            if (isUnrecoverable) {
                Log.e(TAG, "Fallo irrecuperable al descifrar credenciales: ${e.javaClass.simpleName}. Purgando archivo y regenerando clave.")
                atomicFile.delete()
                regenerateKey()
                _credentialsUnreadable.value = true
                SecureCredentials()
            } else {
                Log.e(TAG, "Error transitorio al cargar credenciales: ${e.javaClass.simpleName}")
                null
            }
        }
    }

    @Synchronized
    fun loadCredentials(): SecureCredentials {
        return loadCredentialsOrNull() ?: SecureCredentials()
    }

    @Synchronized
    fun saveCredentials(credentials: SecureCredentials): Boolean {
        return try {
            val json = JSONObject().apply {
                put("libreEmail", credentials.libreEmail)
                put("librePassword", credentials.librePassword)
                put("libreToken", credentials.libreToken)
                put("libreTokenExpiresMs", credentials.libreTokenExpiresMs)
                put("libreUserId", credentials.libreUserId)
                put("libreRegion", credentials.libreRegion)
                put("librePatientId", credentials.librePatientId)
                put("librePatientName", credentials.librePatientName)
                put("nightscoutUrl", credentials.nightscoutUrl)
                put("nightscoutToken", credentials.nightscoutToken)
                put("nightscoutSecret", credentials.nightscoutSecret)
                put("jugglucoUrl", credentials.jugglucoUrl)
                put("jugglucoToken", credentials.jugglucoToken)
                put("pinHash", credentials.pinHash)
            }
            val plainBytes = json.toString().toByteArray(Charsets.UTF_8)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            if (keyStore.type == ANDROID_KEYSTORE) {
                // AndroidKeyStore generates the IV itself (randomized encryption required)
                cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
            } else {
                val newIv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
                cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH, newIv))
            }
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainBytes)

            val output = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, output, 0, iv.size)
            System.arraycopy(cipherText, 0, output, iv.size, cipherText.size)

            val stream: FileOutputStream = atomicFile.startWrite()
            try {
                stream.write(output)
                atomicFile.finishWrite(stream)
                _credentialsUnreadable.value = false
                true
            } catch (e: Exception) {
                atomicFile.failWrite(stream)
                Log.e(TAG, "Error escribiendo con AtomicFile: ${e.javaClass.simpleName}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al cifrar credenciales: ${e.javaClass.simpleName}")
            false
        }
    }

    @Synchronized
    fun updateCredentials(transform: (SecureCredentials) -> SecureCredentials): SecureCredentials {
        val current = loadCredentialsOrNull() ?: return SecureCredentials()
        val updated = transform(current)
        saveCredentials(updated)
        return updated
    }

    @Synchronized
    fun clearCredentials() {
        if (file.exists()) {
            atomicFile.delete()
        }
        _credentialsUnreadable.value = false
    }
}
