package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.datastore.PreferencesRepository
import com.example.core.model.SyncResult
import com.example.core.security.KeystoreManager
import com.example.core.security.SecureCredentials
import com.example.data.librelinkup.LibreLinkUpSource
import com.example.data.nightscout.NightscoutSource
import com.example.data.source.SourceResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibreLinkUpMockTest {

    private lateinit var server: MockWebServer
    private lateinit var context: Context
    private lateinit var keystoreManager: KeystoreManager
    private lateinit var preferencesRepository: PreferencesRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        server = MockWebServer()
        server.start()

        keystoreManager = KeystoreManager(context)
        preferencesRepository = PreferencesRepository(context)

        // Seed test credentials pointing to mock server
        val mockBase = server.url("").toString().trimEnd('/')
        keystoreManager.saveCredentials(
            SecureCredentials(
                libreEmail = "follower@example.com",
                librePassword = "securePassword123",
                libreToken = "",
                librePatientId = "patient-123",
                nightscoutUrl = mockBase
            )
        )

        runBlocking {
            preferencesRepository.updateLluAdvanced(
                domain = "io",
                region = "",
                product = "llu.android",
                version = "4.17.0",
                autoAcceptTerms = true
            )
        }
    }

    private fun createMockClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val orig = chain.request()
                val newUrl = server.url(orig.url.encodedPath).newBuilder()
                    .encodedQuery(orig.url.encodedQuery)
                    .build()
                chain.proceed(orig.newBuilder().url(newUrl).build())
            }
            .build()
    }

    @After
    fun teardown() {
        server.shutdown()
        keystoreManager.clearCredentials()
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRejectCleartextHttpDomain() {
        val source = LibreLinkUpSource(keystoreManager, preferencesRepository)
        source.buildBaseUrl("http://insecure.libreview.io", "")
    }

    @Test
    fun testHandle429RateLimit() = runBlocking {
        // Enqueue 429 response
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setBody("Too Many Requests")
        )

        val client = createMockClient()
        val source = LibreLinkUpSource(keystoreManager, preferencesRepository, client)

        val result = source.fetchCurrentAndGraph(fetchGraph = false)
        assertTrue(result is SourceResult.Error)
        val err = (result as SourceResult.Error).error
        assertEquals(SyncResult.RATE_LIMIT, err.resultType)
        assertEquals(429, err.httpStatus)
        assertEquals(300, err.rateLimitRemainingSeconds)
    }

    @Test
    fun testHandle403MinimumVersion() = runBlocking {
        // First request: 403 with minimumVersion 4.18.0
        val body403 = """
            {
                "status": 920,
                "data": {
                    "minimumVersion": "4.18.0"
                }
            }
        """.trimIndent()

        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody(body403)
        )

        // Second request (retry with new version): 200 OK login
        val bodySuccess = """
            {
                "status": 0,
                "data": {
                    "user": { "id": "user-abc-123" },
                    "authTicket": {
                        "token": "valid-token-xyz",
                        "expires": 1790000000,
                        "duration": 3600000
                    }
                }
            }
        """.trimIndent()

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(bodySuccess)
        )

        // Third request: Graph endpoint
        val graphSuccess = """
            {
                "status": 0,
                "data": {
                    "connection": {
                        "glucoseMeasurement": {
                            "ValueInMgPerDl": 135,
                            "TrendArrow": 3,
                            "FactoryTimestamp": "11/10/2026 3:45:16 PM"
                        }
                    }
                }
            }
        """.trimIndent()

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(graphSuccess)
        )

        val client = createMockClient()
        val source = LibreLinkUpSource(keystoreManager, preferencesRepository, client)

        val result = source.fetchCurrentAndGraph(fetchGraph = false)
        assertTrue(result is SourceResult.Success)

        // Verify version updated in preferences
        val prefs = preferencesRepository.userPreferencesFlow.first()
        assertEquals("4.18.0", prefs.lluVersion)
    }

    @Test
    fun testNightscoutCurrentEndpoint() = runBlocking {
        val nsJson = """
            [
                {
                    "_id": "64fe0",
                    "sgv": 142,
                    "date": 1700000000000,
                    "dateString": "2023-11-14T20:53:20.000Z",
                    "trend": 4,
                    "direction": "Flat",
                    "device": "xDrip-DexcomG6",
                    "type": "sgv"
                }
            ]
        """.trimIndent()

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(nsJson)
        )

        val client = OkHttpClient.Builder().build()
        val source = NightscoutSource(keystoreManager, client)

        val result = source.fetchCurrentAndGraph(fetchGraph = false)
        assertTrue(result is SourceResult.Success)
        val readings = (result as SourceResult.Success).readings
        assertEquals(1, readings.size)
        assertEquals(142, readings[0].valueMgDl)
        assertEquals(com.example.core.model.TrendArrow.FLAT, readings[0].trend)
    }
}
