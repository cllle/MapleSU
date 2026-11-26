package androidx.compose.runtime.saveable

import androidx.compose.runtime.MutableState

class Saver<T, S>
fun <T, S> listSaver(save: (T) -> List<S>, restore: (List<S>) -> T): Saver<T, S> = Saver()
fun <T> rememberSaveable(stateSaver: Saver<T, *>, init: () -> MutableState<T>): MutableState<T> = init()
