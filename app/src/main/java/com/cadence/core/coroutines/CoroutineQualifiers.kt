package com.cadence.core.coroutines

import javax.inject.Qualifier

/** `Dispatchers.IO`: blocking I/O such as files and system services. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** `Dispatchers.Default`: CPU work such as JSON mapping. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** Process-wide scope for work that must outlive a screen (e.g. the macro engine). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
