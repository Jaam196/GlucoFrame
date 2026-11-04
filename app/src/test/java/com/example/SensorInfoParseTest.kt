package com.example

import com.example.data.librelinkup.SensorInfoStore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SensorInfoParseTest {

    private val graph = """
        {"connection":{"targetLow":70,"targetHigh":180,
          "sensor":{"deviceId":"d","sn":"SERIAL1","a":1700000000,"w":60,"pt":4},
          "patientDevice":{"v":"3.3.1","ll":65,"hl":250,"alarms":true}},
         "activeSensors":[{"sensor":{"deviceId":"d","sn":"SERIAL2","a":1700086400,"w":60,"pt":4},
                           "device":{"v":"3.4.0","ll":70,"hl":240,"alarms":false}}]}
    """.trimIndent()

    @Test
    fun `active sensor wins over connection sensor`() {
        val info = SensorInfoStore.parse(JSONObject(graph), 1L)
        assertNotNull(info)
        assertEquals("SERIAL2", info!!.serial)
        assertEquals(1700086400L, info.activatedAtSec)
        assertEquals(70, info.targetLowMgDl)
        assertEquals(240, info.highLimitMgDl)
        assertEquals("3.4.0", info.appVersion)
    }

    @Test
    fun `connection item without activeSensors is read`() {
        val conn = JSONObject(graph).getJSONObject("connection")
        val info = SensorInfoStore.parse(conn, 1L)
        assertEquals("SERIAL1", info!!.serial)
        assertEquals(true, info.alarmsEnabled)
    }

    @Test
    fun `missing sensor gives null and day count starts at 1`() {
        assertNull(SensorInfoStore.parse(JSONObject("{\"connection\":{}}"), 1L))
        val info = SensorInfoStore.parse(JSONObject(graph), 1L)!!
        val start = info.activatedAtSec * 1000L
        assertEquals(1, info.dayOfUse(start + 3_600_000L))
        assertEquals(6, info.dayOfUse(start + 5 * 86_400_000L + 1000L))
        assertEquals(0L, info.hoursLeft(start + 14 * 86_400_000L, 14))
    }
}
