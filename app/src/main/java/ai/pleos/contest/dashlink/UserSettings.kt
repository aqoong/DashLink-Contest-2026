package ai.pleos.contest.dashlink

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class EmergencyContact(val name: String, val number: String) {
    val masked: String get() = "${name.ifBlank { "긴급연락처" }} · •••• ${number.takeLast(4)}"
}

class UserSettings(context: Context) {
    private val prefs = context.getSharedPreferences("dashlink_user", Context.MODE_PRIVATE)
    var step: Int
        get() = prefs.getInt("step", 0).coerceIn(0, 3)
        set(value) { check(prefs.edit().putInt("step", value).commit()) { "설정을 저장하지 못했어요" } }
    var callAllowed: Boolean
        get() = prefs.getBoolean("call_allowed", false)
        set(value) { check(prefs.edit().putBoolean("call_allowed", value).commit()) { "설정을 저장하지 못했어요" } }

    fun contact(): EmergencyContact? {
        val stored = prefs.getString("contact", null) ?: return null
        val parts = stored.split(':')
        require(parts.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        val json = JSONObject(String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8))
        return EmergencyContact(json.getString("name"), json.getString("number"))
    }
    fun saveContact(name: String, number: String) {
        val normalized = requireNotNull(normalizedContact(number))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(true))
        val json = JSONObject().put("name", name.trim().take(30)).put("number", normalized).toString()
        val encoded = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(json.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        check(prefs.edit().putString("contact", encoded).commit()) { "연락처를 저장하지 못했어요" }
    }
    fun deleteContact() { check(prefs.edit().remove("contact").commit()) }
    fun clear() {
        check(prefs.edit().clear().commit())
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS)
    }
    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        check(create) { "저장한 연락처를 읽을 수 없어요" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    companion object { private const val KEY_ALIAS = "dashlink.contact.v1" }
}
