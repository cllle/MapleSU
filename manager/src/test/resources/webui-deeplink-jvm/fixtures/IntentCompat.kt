package androidx.core.content

import android.content.Intent

object IntentCompat {
    fun <T> getParcelableExtra(intent: Intent, name: String, type: Class<T>): T? = null
    fun <T> getParcelableArrayListExtra(intent: Intent, name: String, type: Class<T>): ArrayList<T>? = null
}
