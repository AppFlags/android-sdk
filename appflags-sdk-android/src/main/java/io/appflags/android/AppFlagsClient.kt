package io.appflags.android

import android.app.Application
import android.content.Context
import android.util.Log
import io.appflags.android.api.ConfigurationUpdateListener
import io.appflags.android.api.FlagsApi
import io.appflags.android.exception.AppFlagsException
import io.appflags.android.lifecycle.AppFlagsLifecycleCallbacks
import io.appflags.android.models.AppFlagsClientOptions
import io.appflags.android.models.AppFlagsFlag
import io.appflags.android.models.AppFlagsUser
import io.appflags.android.models.Configuration
import io.appflags.android.utils.ProtobufConverters
import io.appflags.protos.ComputedFlag
import io.appflags.protos.ConfigurationLoadType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import java.lang.ref.WeakReference

const val TAG = "AppFlagsClient"

class AppFlagsClient private constructor(
    private val sdkKey: String,
    user: AppFlagsUser,
    options: AppFlagsClientOptions? = null
) {

    companion object {
        private var INSTANCE: AppFlagsClient? = null

        fun getClient(): AppFlagsClient {
            return INSTANCE ?: throw AppFlagsException("AppFlagsClient has not been initialized.")
        }

        fun init(
            context: Context,
            sdkKey: String,
            user: AppFlagsUser,
            options: AppFlagsClientOptions? = null
        ): AppFlagsClient {
            val client = AppFlagsClient(sdkKey, user, options)
            client.registerApplicationContext(context)
            return client
        }
    }

    private val edgeUrl: String = options?.edgeUrlOverride ?: "https://edge.appflags.net"
    private val flagsApi: FlagsApi = FlagsApi(sdkKey, edgeUrl)

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private var user: AppFlagsUser = user
    private var configuration: Configuration? = null
    private var configurationUpdateListener: ConfigurationUpdateListener? = null

    private var cachedFlagsForUpdates: MutableMap<String, MutableList<WeakReference<AppFlagsFlag<*>>>> = mutableMapOf()

    init {
        if (INSTANCE != null) {
            throw AppFlagsException("An instance of AppFlagsClient already exists. You should only initialize AppFlagsClient once.")
        }
        INSTANCE = this

        val initializeJob = coroutineScope.async {
            initialize()
        }

        initializeJob.invokeOnCompletion {
            Log.d(TAG, "Initialized client")
        }
    }

    private fun initialize() {
        try {
            loadConfiguration(ConfigurationLoadType.INITIAL_LOAD)
            configurationUpdateListener = ConfigurationUpdateListener(edgeUrl, sdkKey) {published: Long ->
                loadConfiguration(ConfigurationLoadType.REALTIME_RELOAD, published)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize: " + t.stackTraceToString())
            throw t
        }
    }

    private fun registerApplicationContext(context: Context) {
        val application = context.applicationContext as Application
        application.registerActivityLifecycleCallbacks(
            AppFlagsLifecycleCallbacks(
                { onApplicationPaused() },
                { onApplicationResume() }
            )
        )
    }

    private fun onApplicationPaused() {
        Log.d(TAG, "Application paused, pausing realtime updates")
        configurationUpdateListener?.close()
    }

    private fun onApplicationResume() {
        if (configurationUpdateListener != null && configurationUpdateListener!!.isClosed()) {
            coroutineScope.async {
                Log.d(TAG, "Application resumed, reconnecting to realtime updates")
                configurationUpdateListener!!.reconnectIfNeeded()
                loadConfiguration(ConfigurationLoadType.PERIODIC_RELOAD)
            }
        }
    }

    private fun loadConfiguration(loadType: ConfigurationLoadType, published: Long? = null) {
        coroutineScope.async {
            try {
                val newConfiguration = flagsApi.getConfiguration(user, loadType, published)
                configuration = newConfiguration
                updateCachedFlags()
                Log.d(TAG, "Loaded configuration${if (published != null) " at $published" else ""}, there are ${newConfiguration.flags.count()} flags")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to load flags: " + t.stackTraceToString())
            }
        }
    }

    private fun updateCachedFlags() {
        for (flagKey in cachedFlagsForUpdates.keys) {

            // Get current flag from configuration
            val rawFlag = configuration!!.flags[flagKey]
            if (rawFlag == null) {
                Log.e(TAG, "Updated configuration does not include pre-existing flag [$flagKey]")
                continue
            }
            val updatedFlag = ProtobufConverters.toAppFlagsFlag(rawFlag)

            // Update cached flags
            val cachedFlags = cachedFlagsForUpdates[flagKey] ?: continue
            for (cachedFlagReference in cachedFlags) {
                val cachedFlag = cachedFlagReference.get()
                if (cachedFlag == null) {
                    cachedFlags.remove(cachedFlagReference)
                } else {
                    cachedFlag.updateFlag(updatedFlag)
                }
            }
        }
    }

    fun getBooleanFlag(flagKey: String, defaultValue: Boolean): AppFlagsFlag<Boolean> {
        return getFlagInternal(flagKey, AppFlagsFlag.FlagType.BOOLEAN, defaultValue)
    }

    fun getNumberFlag(flagKey: String, defaultValue: Double): AppFlagsFlag<Double> {
        return getFlagInternal(flagKey, AppFlagsFlag.FlagType.NUMBER, defaultValue)
    }

    fun getStringFlag(flagKey: String, defaultValue: String): AppFlagsFlag<String> {
        return getFlagInternal(flagKey, AppFlagsFlag.FlagType.STRING, defaultValue)
    }

    private fun <T> getFlagInternal(
        flagKey: String,
        flagType: AppFlagsFlag.FlagType,
        defaultValue: T
    ): AppFlagsFlag<T> {
        val flag: AppFlagsFlag<out Any?> = when(val rawFlag: ComputedFlag? = configuration?.flags?.get(flagKey)) {
            null -> AppFlagsFlag(flagKey, flagType, defaultValue, isDefaultValue = true)
            else -> ProtobufConverters.toAppFlagsFlag(rawFlag)
        }
        if (flag.flagType != flagType) {
            throw AppFlagsException("Flag $flagKey is not of type ${flagType.name}")
        }

        // cache flag so we can update it later
        if (!cachedFlagsForUpdates.containsKey(flag.key)) {
            cachedFlagsForUpdates[flag.key] = mutableListOf()
        }
        cachedFlagsForUpdates[flag.key]?.add(WeakReference(flag))

        @Suppress("UNCHECKED_CAST")
        return flag as AppFlagsFlag<T>
    }

    fun updateUser(user: AppFlagsUser) {
        this.user = user
        loadConfiguration(ConfigurationLoadType.PERIODIC_RELOAD)
    }

}