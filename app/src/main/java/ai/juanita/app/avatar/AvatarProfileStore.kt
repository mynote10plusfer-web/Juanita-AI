package ai.juanita.app.avatar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Private, device-local avatar catalogue. No photos or generated models are
 * uploaded or copied by this class; it stores only metadata and app-relative
 * asset references. The photo picker flow must persist URI read permission.
 */
class AvatarProfileStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun listProfiles(): List<AvatarProfile> {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    decode(item)?.let(::add)
                }
            }.sortedByDescending { it.updatedAtEpochMillis }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun activeProfile(): AvatarProfile? {
        val id = prefs.getString(KEY_ACTIVE_ID, null) ?: return null
        return listProfiles().firstOrNull { it.id == id }
    }

    @Synchronized
    fun save(profile: AvatarProfile, makeActive: Boolean = true): AvatarProfile {
        val valid = profile.copy(updatedAtEpochMillis = System.currentTimeMillis()).validated()
        val profiles = listProfiles().filterNot { it.id == valid.id } + valid
        persist(profiles)
        if (makeActive) prefs.edit().putString(KEY_ACTIVE_ID, valid.id).apply()
        return valid
    }

    @Synchronized
    fun setActive(profileId: String): Boolean {
        val exists = listProfiles().any { it.id == profileId }
        if (!exists) return false
        prefs.edit().putString(KEY_ACTIVE_ID, profileId).apply()
        return true
    }

    @Synchronized
    fun delete(profileId: String) {
        persist(listProfiles().filterNot { it.id == profileId })
        if (prefs.getString(KEY_ACTIVE_ID, null) == profileId) {
            prefs.edit().remove(KEY_ACTIVE_ID).apply()
        }
    }

    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY_PROFILES).remove(KEY_ACTIVE_ID).apply()
    }

    private fun persist(profiles: List<AvatarProfile>) {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("displayName", profile.displayName)
                    .put("creationSource", profile.creationSource.name)
                    .put("renderStyle", profile.renderStyle.name)
                    .put("motionLevel", profile.motionLevel.name)
                    .put("appearancePrompt", profile.appearancePrompt)
                    .put("referencePhotoUri", profile.referencePhotoUri)
                    .put("assetPath", profile.assetPath)
                    .put("createdAtEpochMillis", profile.createdAtEpochMillis)
                    .put("updatedAtEpochMillis", profile.updatedAtEpochMillis)
            )
        }
        prefs.edit().putString(KEY_PROFILES, array.toString()).apply()
    }

    private fun decode(item: JSONObject): AvatarProfile? = runCatching {
        AvatarProfile(
            id = item.getString("id"),
            displayName = item.optString("displayName", "Avatar"),
            creationSource = enumValueOrDefault(
                item.optString("creationSource"),
                AvatarCreationSource.DESCRIPTION
            ),
            renderStyle = enumValueOrDefault(
                item.optString("renderStyle"),
                AvatarRenderStyle.REALISTIC_3D
            ),
            motionLevel = enumValueOrDefault(
                item.optString("motionLevel"),
                AvatarMotionLevel.NATURAL
            ),
            appearancePrompt = item.optString("appearancePrompt"),
            referencePhotoUri = item.optString("referencePhotoUri").takeIf { it.isNotBlank() && it != "null" },
            assetPath = item.optString("assetPath").takeIf { it.isNotBlank() && it != "null" },
            createdAtEpochMillis = item.optLong("createdAtEpochMillis", System.currentTimeMillis()),
            updatedAtEpochMillis = item.optLong("updatedAtEpochMillis", System.currentTimeMillis())
        ).validated()
    }.getOrNull()

    private inline fun <reified T : Enum<T>> enumValueOrDefault(
        value: String,
        default: T
    ): T = runCatching { enumValueOf<T>(value) }.getOrDefault(default)

    companion object {
        private const val PREFS_NAME = "juanita_avatar_profiles"
        private const val KEY_PROFILES = "profiles_json"
        private const val KEY_ACTIVE_ID = "active_profile_id"
    }
}
