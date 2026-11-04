package com.example

import com.example.core.datastore.DisplayStyleCodec
import com.example.core.model.ChartStyle
import com.example.core.model.ClockPosition
import com.example.core.model.DisplayStyle
import com.example.core.model.NightNumberMode
import com.example.core.model.NumberFont
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DisplayStyleCodecTest {

  @Test
  fun `default style survives a save and load`() {
    val style = DisplayStyle()
    assertEquals(style, DisplayStyleCodec.decode(DisplayStyleCodec.encode(style)))
  }

  @Test
  fun `customised style survives a save and load`() {
    val style = DisplayStyle(
      numberFont = NumberFont.CONDENSED,
      numberSizeScale = 1.3f,
      nightNumberMode = NightNumberMode.CUSTOM,
      nightCustomHex = "#112233",
      chartStyle = ChartStyle.AREA,
      clockPosition = ClockPosition.CENTER,
      clockSizeSp = 40f,
      widgetShowAge = false
    )
    assertEquals(style, DisplayStyleCodec.decode(DisplayStyleCodec.encode(style)))
  }

  @Test
  fun `missing or broken data falls back to defaults`() {
    assertEquals(DisplayStyle(), DisplayStyleCodec.decode(null))
    assertEquals(DisplayStyle(), DisplayStyleCodec.decode("not json"))
    assertEquals(NumberFont.BLACK, DisplayStyleCodec.decode("{\"numberFont\":\"DOES_NOT_EXIST\"}").numberFont)
  }
}
