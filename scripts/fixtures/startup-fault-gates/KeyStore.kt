package dev.maplesu.manager.modules
import android.content.Context
import dev.maplesu.manager.runtime.ultra.Fixture
object UltraRootKeyStore {
    fun read(context: Context) = Fixture.key
    fun isSuspended(context: Context) = Fixture.suspended
    fun captureOwnerMarker(context: Context): () -> Boolean = { Fixture.rootMarkerCurrent }
}
