package net.ccbluex.liquidbounce.api

import net.ccbluex.liquidbounce.utils.io.applyBypassHttps
import net.ccbluex.liquidbounce.utils.io.decodeJson
import net.ccbluex.liquidbounce.utils.io.get
import net.ccbluex.liquidbounce.utils.io.post
import net.ccbluex.liquidbounce.utils.kotlin.RandomUtils
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.util.concurrent.TimeUnit

private const val HARD_CODED_BRANCH = "legacy"

private const val API_V1_ENDPOINT = "https://api.liquidbounce.net/api/v1"


/**
 * Session token
 *
 * This is used to identify the client in one session
 */
private val SESSION_TOKEN = RandomUtils.randomString(16)

private val client = OkHttpClient.Builder()
    .connectTimeout(3, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .applyBypassHttps()
    .addInterceptor { chain ->
        val original = chain.request()
        val request: Request = original.newBuilder()
            .header("X-Session-Token", SESSION_TOKEN)
            .build()

        chain.proceed(request)
    }.build()

/**
 * ClientApi
 */
object ClientApi {

    fun getNewestBuild(branch: String = HARD_CODED_BRANCH, release: Boolean = false): Build {
        val url = "$API_V1_ENDPOINT/version/newest/$branch${if (release) "/release" else "" }"
        client.get(url).use { response ->
            if (!response.isSuccessful) error("Request failed: ${response.code}")
            return response.body.charStream().decodeJson()
        }
    }

    fun getSettingsList(branch: String = HARD_CODED_BRANCH): List<AutoSettings> {
        val url = "$API_V1_ENDPOINT/client/$branch/settings"
        client.get(url).use { response ->
            if (!response.isSuccessful) error("Request failed: ${response.code}")
            return response.body.charStream().decodeJson()
        }
    }

    fun getSettingsScript(branch: String = HARD_CODED_BRANCH, settingId: String): String {
        val url = "$API_V1_ENDPOINT/client/$branch/settings/$settingId"
        client.get(url).use { response ->
            if (!response.isSuccessful) error("Request failed: ${response.code}")
            return response.body.string()
        }
    }

    @Deprecated("Removed API")
    fun reportSettings(branch: String = HARD_CODED_BRANCH, settingId: String): ReportResponse {
        val url = "$API_V1_ENDPOINT/client/$branch/settings/report/$settingId"
        client.get(url).use { response ->
            if (!response.isSuccessful) error("Request failed: ${response.code}")
            return response.body.charStream().decodeJson()
        }
    }

    @Deprecated("Removed API")
    fun uploadSettings(
        branch: String = HARD_CODED_BRANCH,
        name: RequestBody,
        contributors: RequestBody,
        settingsFile: MultipartBody.Part
    ): UploadResponse {
        val url = "$API_V1_ENDPOINT/client/$branch/settings/upload"
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("name", null, name)
            .addFormDataPart("contributors", null, contributors)
            .addPart(settingsFile)
            .build()

        client.post(url, requestBody).use { response ->
            if (!response.isSuccessful) error("Request failed: ${response.code}")
            return response.body.charStream().decodeJson()
        }
    }
}
