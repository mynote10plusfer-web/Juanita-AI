package ai.juanita.app.avatar

import java.util.UUID

/**
 * User-owned avatar configuration.
 *
 * The profile describes the desired appearance and the generated asset. It does
 * not imply that image-to-3D generation is available on-device; generation
 * engines are deliberately kept separate from this portable data contract.
 */
enum class AvatarCreationSource {
    DESCRIPTION,
    PHOTO_REFERENCE,
    RANDOM
}

enum class AvatarRenderStyle {
    PHOTOREALISTIC,
    REALISTIC_3D,
    STYLIZED_3D,
    CARTOON
}

enum class AvatarMotionLevel {
    SUBTLE,
    NATURAL,
    EXPRESSIVE
}

data class AvatarProfile(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String = "Juanita",
    val creationSource: AvatarCreationSource = AvatarCreationSource.DESCRIPTION,
    val renderStyle: AvatarRenderStyle = AvatarRenderStyle.REALISTIC_3D,
    val motionLevel: AvatarMotionLevel = AvatarMotionLevel.NATURAL,
    val appearancePrompt: String = "",
    /** Persistable content URI selected by the user, if any. */
    val referencePhotoUri: String? = null,
    /** App-private relative path to a generated GLB/VRM or portrait asset. */
    val assetPath: String? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
) {
    fun validated(): AvatarProfile {
        require(id.isNotBlank()) { "Avatar id cannot be blank." }
        require(displayName.isNotBlank()) { "Avatar name cannot be blank." }
        require(appearancePrompt.length <= MAX_PROMPT_LENGTH) {
            "Avatar description is too long (max $MAX_PROMPT_LENGTH characters)."
        }
        require(referencePhotoUri.isNullOrBlank() || referencePhotoUri.startsWith("content://")) {
            "Photo reference must be an Android content URI."
        }
        require(assetPath.isNullOrBlank() || !assetPath.startsWith("/") && !assetPath.contains("..")) {
            "Avatar asset path must be app-relative."
        }
        return copy(
            displayName = displayName.trim().take(MAX_NAME_LENGTH),
            appearancePrompt = appearancePrompt.trim(),
            updatedAtEpochMillis = System.currentTimeMillis()
        )
    }

    companion object {
        const val MAX_PROMPT_LENGTH = 1200
        const val MAX_NAME_LENGTH = 48
    }
}
