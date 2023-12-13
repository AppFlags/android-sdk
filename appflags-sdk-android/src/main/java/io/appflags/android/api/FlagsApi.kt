package io.appflags.android.api

import android.util.Base64
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.appflags.android.exception.AppFlagsException
import io.appflags.android.models.AppFlagsUser
import io.appflags.android.models.Configuration
import io.appflags.android.utils.PlatformUtil
import io.appflags.android.utils.ProtobufConverters
import io.appflags.protos.ConfigurationLoadType
import io.appflags.protos.GetFlagRequest
import io.appflags.protos.GetFlagsResponse
import io.appflags.protos.PlatformData
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class FlagsApi(
    private val clientKey: String,
    private val edgeUrl: String
) {

    @OptIn(ExperimentalStdlibApi::class)
    companion object {
        private val httpClient: OkHttpClient = OkHttpClient()
        private val JSON: MediaType = "application/json; charset=utf-8".toMediaType()

        private val MOSHI: Moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        private val getFlagRequestBodyAdapter: JsonAdapter<GetFlagRequestBody> = MOSHI.adapter<GetFlagRequestBody>()
        private val getFlagResponseBodyAdapter: JsonAdapter<GetFlagResponseBody> = MOSHI.adapter<GetFlagResponseBody>()
    }

    private val platformData: PlatformData = PlatformUtil.getPlatformData()

    fun getConfiguration(user: AppFlagsUser, loadType: ConfigurationLoadType, getUpdatedAt: Long? = null): Configuration {
        val getFlagRequestBuilder = GetFlagRequest.newBuilder()
            .setConfigurationId(this.clientKey)
            .setLoadType(loadType)
            .setPlatformData(platformData)
            .setUser(ProtobufConverters.toUserProto(user))
        if (getUpdatedAt != null) {
            getFlagRequestBuilder.setGetUpdateAt(getUpdatedAt)
        }
        val getFlagRequest: GetFlagRequest = getFlagRequestBuilder.build()

        val encodedGetFlagRequest = Base64.encodeToString(getFlagRequest.toByteArray(), Base64.DEFAULT)
        val requestBody = GetFlagRequestBody(encodedGetFlagRequest)
        val requestBodyJson = getFlagRequestBodyAdapter.toJson(requestBody)

        val url = this.edgeUrl + "/configuration/v1/flags"
        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toRequestBody(JSON))
            .build()
        val response = httpClient.newCall(request).execute()

        if (response.code == 404) {
            throw AppFlagsException("Invalid clientKey");
        }
        if (response.code != 200) {
            throw AppFlagsException("Error retrieving flags from API, code [${response.code}], message [${response.message}]")
        }

        val responseBodyJson = response.body!!.source()
        val getFlagResponseBody: GetFlagResponseBody? = getFlagResponseBodyAdapter.fromJson(responseBodyJson)
        val responseArray: ByteArray = Base64.decode(getFlagResponseBody?.response, Base64.DEFAULT)
        val getFlagResponse: GetFlagsResponse = GetFlagsResponse.parseFrom(responseArray)
        return ProtobufConverters.toConfiguration(getFlagResponse)
    }

}

private data class GetFlagRequestBody (
    val request: String
)

private data class GetFlagResponseBody (
    val response: String
)