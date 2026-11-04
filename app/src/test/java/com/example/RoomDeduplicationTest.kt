package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.GlucoseDatabase
import com.example.core.database.GlucoseReadingEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomDeduplicationTest {

    private lateinit var db: GlucoseDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, GlucoseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testUniqueConstraintDeduplication() = runBlocking {
        val dao = db.glucoseReadingDao()
        val fixedTime = 1700000000000L

        val r1 = GlucoseReadingEntity(
            sourceId = "LIBRELINKUP",
            measuredAtUtcMs = fixedTime,
            valueMgDl = 120,
            trend = "FLAT",
            isHigh = false,
            isLow = false,
            receivedAtUtcMs = fixedTime
        )

        // First insert succeeds (returns generated rowId > 0)
        val id1 = dao.insertReading(r1)
        assertEquals(1L, id1)
        assertEquals(1, dao.getReadingsCount())

        // Duplicate insert with same (sourceId, measuredAtUtcMs) must be IGNORED (returns -1)
        val rDuplicate = r1.copy(valueMgDl = 145)
        val id2 = dao.insertReading(rDuplicate)
        assertEquals(-1L, id2)

        // Count must strictly remain 1
        assertEquals(1, dao.getReadingsCount())

        // Reading value must remain the original 120
        val latest = dao.getLatestReadingSync()
        assertEquals(120, latest?.valueMgDl)

        // Different timestamp succeeds
        val rDifferentTime = r1.copy(measuredAtUtcMs = fixedTime + 60000)
        val id3 = dao.insertReading(rDifferentTime)
        assertTrue("New record id should be positive", id3 > 0)
        assertEquals(2, dao.getReadingsCount())
    }

    @Test
    fun testSourceFiltering() = runBlocking {
        val dao = db.glucoseReadingDao()
        val t = 1700000000000L

        dao.insertReading(GlucoseReadingEntity(
            sourceId = "LIBRELINKUP",
            measuredAtUtcMs = t,
            valueMgDl = 110,
            trend = "FLAT",
            isHigh = false,
            isLow = false,
            receivedAtUtcMs = t
        ))

        dao.insertReading(GlucoseReadingEntity(
            sourceId = "NIGHTSCOUT",
            measuredAtUtcMs = t + 60000,
            valueMgDl = 135,
            trend = "FLAT",
            isHigh = false,
            isLow = false,
            receivedAtUtcMs = t + 60000
        ))

        val lluLatest = dao.getLatestReadingSync("LIBRELINKUP")
        assertEquals(110, lluLatest?.valueMgDl)

        val nsLatest = dao.getLatestReadingSync("NIGHTSCOUT")
        assertEquals(135, nsLatest?.valueMgDl)

        val jugglucoLatest = dao.getLatestReadingSync("JUGGLUCO")
        assertEquals(null, jugglucoLatest)
    }
}
