package com.example

import android.os.Bundle
import com.example.core.model.TrendArrow
import com.example.data.broadcast.GlucoseBroadcastParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GlucoseBroadcastParserTest {

  private val now = 1_800_000_000_000L

  @Test
  fun `xdrip broadcast is parsed`() {
    val extras = Bundle().apply {
      putDouble("com.eveningoutpost.dexdrip.Extras.BgEstimate", 123.4)
      putLong("com.eveningoutpost.dexdrip.Extras.Time", now - 60_000L)
      putString("com.eveningoutpost.dexdrip.Extras.BgSlopeName", "FortyFiveUp")
    }
    val r = GlucoseBroadcastParser.parse(GlucoseBroadcastParser.ACTION_XDRIP, extras, now)!!
    assertEquals(123, r.valueMgDl)
    assertEquals(now - 60_000L, r.timeMs)
    assertEquals(TrendArrow.RISING, r.trend)
  }

  @Test
  fun `juggluco broadcast is parsed and rate gives the arrow`() {
    val extras = Bundle().apply {
      putInt("glucodata.Minute.mgdl", 95)
      putFloat("glucodata.Minute.Rate", -2.4f)
      putLong("glucodata.Minute.Time", now)
    }
    val r = GlucoseBroadcastParser.parse(GlucoseBroadcastParser.ACTION_JUGGLUCO, extras, now)!!
    assertEquals(95, r.valueMgDl)
    assertEquals(TrendArrow.FALLING_FAST, r.trend)
  }

  @Test
  fun `implausible values and unknown actions are rejected`() {
    val bad = Bundle().apply { putDouble("com.eveningoutpost.dexdrip.Extras.BgEstimate", 5.0) }
    assertNull(GlucoseBroadcastParser.parse(GlucoseBroadcastParser.ACTION_XDRIP, bad, now))
    assertNull(GlucoseBroadcastParser.parse("some.other.action", Bundle(), now))
  }

  @Test
  fun `future time is replaced by now`() {
    val extras = Bundle().apply {
      putDouble("com.eveningoutpost.dexdrip.Extras.BgEstimate", 110.0)
      putLong("com.eveningoutpost.dexdrip.Extras.Time", now + 3_600_000L)
    }
    val r = GlucoseBroadcastParser.parse(GlucoseBroadcastParser.ACTION_XDRIP, extras, now)!!
    assertEquals(now, r.timeMs)
  }
}
