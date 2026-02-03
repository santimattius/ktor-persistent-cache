package io.github.santimattius.persistent.cache.startup

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.Service
import android.app.backup.BackupAgent
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.startup.Initializer

class ContextInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        injectContext(context = context)
        Log.d("AppContextInitializer", "AppContextInitializer initialized")
    }

    override fun dependencies(): List<Class<out Initializer<*>?>?> {
        return emptyList()
    }
}


/**
 * Checks if the current [Context] instance is prone to memory leaks if stored statically.
 *
 * This function evaluates the context type to determine if it's safe to hold a long-lived reference to it.
 * - [Application] contexts are safe.
 * - [Activity], [Service], and [BackupAgent] contexts are not safe as they are tied to their respective component lifecycles.
 * - [ContextWrapper] instances are checked based on their base context. If the base context is the instance itself (which can happen in some scenarios),
 *   it's considered unsafe. Otherwise, the check is delegated to the base context.
 * - Other context types are considered unsafe if their `applicationContext` is null, which might indicate a non-standard or problematic context.
 *
 * @return `true` if the context can leak memory, `false` otherwise.
 */
fun Context.canLeakMemory(): Boolean = when (this) {
    is Application -> false
    is Activity, is Service, is BackupAgent -> true
    is ContextWrapper -> if (baseContext === this) true else baseContext.canLeakMemory()
    else -> applicationContext === null
}

@SuppressLint("StaticFieldLeak")
private var applicationContext: Context? = null

/**
 * Injects a [Context] to be used as a global application context.
 *
 * This function should be called early in the application lifecycle, typically in `Application.onCreate()`,
 * with the application context itself.
 * It performs a check using [canLeakMemory] to ensure that the provided context is not one that
 * could cause memory leaks if stored statically (e.g., an Activity context).
 *
 * @param context The [Context] to be used as the application context.
 * @throws IllegalArgumentException if the provided context is deemed prone to memory leaks.
 */
internal fun injectContext(context: Context) {
    require(!context.canLeakMemory()) { "The passed $context would leak memory!" }
    applicationContext = context
}

/**
 * Retrieves the globally injected application [Context].
 *
 * This function provides access to the application context that was previously set via [injectContext].
 * It's a convenient way to get a context from anywhere in the application without needing to pass it around.
 *
 * @return The application [Context].
 * @throws IllegalStateException if [injectContext] has not been called before this function is invoked.
 */
fun getApplicationContext(): Context = applicationContext ?: error("Context not injected!")