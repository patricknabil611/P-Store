package com.example.installer

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface InstallEvent {
    data class Success(val packageName: String, val appName: String, val apkPath: String) : InstallEvent
    data class SignatureConflict(val packageName: String, val appName: String, val apkPath: String) : InstallEvent
    data class Failure(val packageName: String, val appName: String, val errorMessage: String, val apkPath: String) : InstallEvent
    data class UserActionRequired(val packageName: String, val appName: String) : InstallEvent
}

object InstallEvents {
    private val _events = MutableSharedFlow<InstallEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    fun emit(event: InstallEvent) {
        _events.tryEmit(event)
    }
}
