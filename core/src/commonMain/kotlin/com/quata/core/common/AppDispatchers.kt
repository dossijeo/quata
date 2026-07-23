package com.quata.core.common

/** Dispatchers injected into shared business logic. */

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

data class AppDispatchers(
    val io: CoroutineDispatcher = platformIoDispatcher,
    val default: CoroutineDispatcher = Dispatchers.Default,
    val main: CoroutineDispatcher = Dispatchers.Main
)

expect val platformIoDispatcher: CoroutineDispatcher
