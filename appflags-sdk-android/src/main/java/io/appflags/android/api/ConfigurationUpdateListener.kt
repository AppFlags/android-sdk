package io.appflags.android.api

import android.util.Log
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.appflags.android.exception.AppFlagsException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit


class ConfigurationUpdateListener(
    val edgeUrl: String,
    val sdkKey: String,
    val updateEventHandler: (published: Long) -> Unit
) {

    private val listener: EventSourceListener = ConfigurationEventListener()

    private var eventSource: EventSource? = null
    private var lastEventId: String? = null

    private var isClosed = false

    init {
        createNewEventSource()
    }

    @OptIn(ExperimentalStdlibApi::class)
    companion object {
        private const val TAG = "ConfigUpdateListener"

        private val MOSHI: Moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        private val httpClient: OkHttpClient = OkHttpClient()
        private val sseClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(0, TimeUnit.MILLISECONDS) // no timeout
            .readTimeout(0, TimeUnit.MILLISECONDS) // no timeout
            .writeTimeout(0, TimeUnit.MILLISECONDS) // no timeout
            .build()

        private val getSseUrlResponseAdapter: JsonAdapter<GetSseUrlResponse> = MOSHI.adapter<GetSseUrlResponse>()
        private val eventSourceMessageJsonAdapter: JsonAdapter<EventSourceMessage> = MOSHI.adapter<EventSourceMessage>()
        private val configurationUpdateEventJsonAdapter: JsonAdapter<ConfigurationUpdateEvent> = MOSHI.adapter<ConfigurationUpdateEvent>()
    }

    private fun createNewEventSource() {
        if (eventSource != null) {
            eventSource!!.cancel()
            Log.d(TAG, "EventSource closed, creating new SSE EventSource")
        }
        var sseUrl: String = try {
            getSseUrl()
        } catch (e: IOException) {
            throw AppFlagsException("Unable to get SSE URL for new EventSource", e)
        }

        // Start with lastEvent if one is recorded (when restarting a connection after receiving a message)
        if (lastEventId != null) {
            sseUrl += "&lastEvent=$lastEventId"
        }
        val request: Request = Request.Builder()
            .url(sseUrl)
            .build()
        val factory: EventSource.Factory = EventSources.createFactory(sseClient)
        this.eventSource = factory.newEventSource(request, listener)
        isClosed = false
    }

    private fun getSseUrl(): String {
        val url = "$edgeUrl/realtimeToken/$sdkKey/eventSource"
        val request = Request.Builder()
            .url(url)
            .build()
        val response = httpClient.newCall(request).execute()
        val responseBodyJson = response.body!!.source()
        val getSseUrlResponse = getSseUrlResponseAdapter.fromJson(responseBodyJson)
        return getSseUrlResponse!!.url
    }

    fun close() {
        isClosed = true
        if (this.eventSource != null) {
            this.eventSource!!.cancel()
            this.eventSource = null
            Log.d(TAG, "Closed event source")
        }
    }

    fun isClosed(): Boolean {
        return isClosed
    }

    fun reconnectIfNeeded() {
        if (this.eventSource == null) {
            Log.d(TAG, "Event source is closed, reconnecting")
            createNewEventSource()
        } else {
            Log.d(TAG, "Event source is already connected")
        }
    }

    inner class ConfigurationEventListener : EventSourceListener() {
        override fun onClosed(eventSource: EventSource) {
            Log.d(TAG, "ConfigurationUpdaterListener EventSource closed, starting a new one")
            createNewEventSource()
        }

        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
            Log.d(TAG, "ConfigurationUpdaterListener EventSource, handling event of type: $type")
            lastEventId = id
            if ("message" == type) {
                try {
                    val eventSourceMessage: EventSourceMessage? =
                        eventSourceMessageJsonAdapter.fromJson(data)
                    val configurationUpdateEvent: ConfigurationUpdateEvent? =
                        configurationUpdateEventJsonAdapter.fromJson(eventSourceMessage!!.data!!)
                    updateEventHandler(configurationUpdateEvent!!.published)
                } catch (e: IOException) {
                    throw AppFlagsException("Error handling ConfigurationUpdaterListener EventSource message", e)
                }
            }
        }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            if (!isClosed) {
                Log.e(TAG, "ConfigurationUpdaterListener EventSource failure, starting a new one", t)
                createNewEventSource()
            }
        }

        override fun onOpen(eventSource: EventSource, response: Response) {
            Log.d(TAG, "ConfigurationUpdaterListener opened")
        }
    }

}


private data class GetSseUrlResponse (
    val url: String
)

private data class EventSourceMessage (
    val data: String?
)

private data class ConfigurationUpdateEvent (
    val published: Long
)
