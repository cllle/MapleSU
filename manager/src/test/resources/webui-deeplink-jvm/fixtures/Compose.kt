package androidx.compose.runtime

import kotlin.reflect.KProperty
import kotlinx.coroutines.runBlocking

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)
annotation class Composable
class MutableState<T>(var value: T)
fun <T> mutableStateOf(value: T): MutableState<T> = MutableState(value)
operator fun <T> MutableState<T>.getValue(owner: Any?, property: KProperty<*>): T = value
operator fun <T> MutableState<T>.setValue(owner: Any?, property: KProperty<*>, value: T) { this.value = value }
fun <T> remember(block: () -> T): T = block()
fun <T> rememberUpdatedState(value: T): MutableState<T> = MutableState(value)
fun LaunchedEffect(key: Any?, block: suspend () -> Unit) = runBlocking { block() }
