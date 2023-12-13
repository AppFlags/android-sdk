package io.appflags.android.models

import io.appflags.android.exception.AppFlagsException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AppFlagsFlag<T>(
    val key: String,
    val flagType: FlagType,
    var value: T,
    var isDefaultValue: Boolean = false
) {

    private val callbacks: MutableList<(updatedFlag: AppFlagsFlag<T>) -> Unit> = mutableListOf()
    private val coroutineScope = CoroutineScope(Dispatchers.Main);

    fun onUpdate(callback: (updatedFlag: AppFlagsFlag<T>) -> Unit) {
        callbacks.add(callback)
    }

    internal fun updateFlag(updatedFlag: AppFlagsFlag<Any>) {
        if (flagType != updatedFlag.flagType) {
            throw AppFlagsException("Cannot update flag [$key], updated flag does not match type.")
        }
        @Suppress("UNCHECKED_CAST")
        value = updatedFlag.value as T
        isDefaultValue = false

        val self = this
        for (callback in callbacks) {
            coroutineScope.launch {
                callback(self)
            }
        }
    }

    enum class FlagType {
        BOOLEAN,
        NUMBER,
        STRING
    }

}
