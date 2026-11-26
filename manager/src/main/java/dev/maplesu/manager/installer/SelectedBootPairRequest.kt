package dev.maplesu.manager.installer

import java.io.Serializable

/** A pending document result stays bound to the saved source request until its header is known. */
internal data class SelectedBootPairRequest(
    val sourceKey: String,
    val generation: Int,
    val resultUri: String? = null,
) : Serializable {
    fun matches(currentSource: String, currentGeneration: Int): Boolean =
        sourceKey == currentSource && generation == currentGeneration

    fun receive(uri: String?, currentSource: String, currentGeneration: Int,
        allowed: Boolean): SelectedBootPairRequest? =
        if (uri != null && allowed && matches(currentSource, currentGeneration)) copy(resultUri = uri) else null

    fun selectedUri(currentSource: String, currentGeneration: Int,
        initBoot: Boolean, allowed: Boolean): String? =
        resultUri?.takeIf { initBoot && allowed && matches(currentSource, currentGeneration) }
}
