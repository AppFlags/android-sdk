package io.appflags.android.lifecycle

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log

const val TAG = "AFLifecycleCallbacks"
const val WAIT_AFTER_PAUSE_MS: Long = 1000

class AppFlagsLifecycleCallbacks(
    private val onPaused: () -> Any,
    private val onResumed: () -> Any,
): Application.ActivityLifecycleCallbacks {

    private val listenerThread: HandlerThread = HandlerThread("AppFlagsLifecycleListener")
    private val delayHandler: Handler

    init {
        listenerThread.start()
        delayHandler = Handler(listenerThread.looper)
    }

    private val onPauseRunnable = Runnable {
        onPaused()
    }

    override fun onActivityPaused(activity: Activity) {
        Log.d(TAG, "onActivityPaused called")
        delayHandler.removeCallbacks(onPauseRunnable) // remove any previous callbacks
        delayHandler.postDelayed(onPauseRunnable, WAIT_AFTER_PAUSE_MS) // setup callback for near future
    }

    override fun onActivityResumed(activity: Activity) {
        Log.d(TAG, "onActivityResumed called")
        delayHandler.removeCallbacks(onPauseRunnable) // remove any previous pause callbacks
        onResumed()
    }

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {}
}