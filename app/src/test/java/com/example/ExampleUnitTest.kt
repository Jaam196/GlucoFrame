package com.example

import com.example.core.common.CryptoUtils
import com.example.core.common.GlucoseUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testMmolToMgDlRounding() {
        // 10.0 mmol/L * 18.0182 = 180.182 -> 180
        assertEquals(180, GlucoseUtils.mmolToMgDl(10.0))

        // 5.55 mmol/L * 18.0182 = 100.001 -> 100
        assertEquals(100, GlucoseUtils.mmolToMgDl(5.55))

        // 3.9 mmol/L * 18.0182 = 70.27 -> 70
        assertEquals(70, GlucoseUtils.mmolToMgDl(3.9))

        // 13.9 mmol/L * 18.0182 = 250.45 -> 250
        assertEquals(250, GlucoseUtils.mmolToMgDl(13.9))
    }

    @Test
    fun testPbkdf2PinHashingAndVerification() {
        val pin = "1234"
        val hash = CryptoUtils.hashPinPbkdf2(pin)
        assertTrue("Hash must contain salt and hash separated by colon", hash.contains(":"))
        val parts = hash.split(":")
        assertEquals("Salt must be 16 bytes (32 hex characters)", 32, parts[0].length)
        assertEquals("PBKDF2 key must be 32 bytes (64 hex characters)", 64, parts[1].length)

        // Valid PIN verification
        val resultValid = CryptoUtils.verifyPin(pin, hash)
        assertTrue(resultValid is CryptoUtils.PinVerificationResult.Valid)
        assertEquals(null, (resultValid as CryptoUtils.PinVerificationResult.Valid).migratedHash)

        // Invalid PIN verification
        val resultInvalid = CryptoUtils.verifyPin("9999", hash)
        assertEquals(CryptoUtils.PinVerificationResult.INVALID, resultInvalid)
    }

    @Test
    fun testLegacySha256PinMigration() {
        val pin = "5678"
        val legacyHash = CryptoUtils.sha256(pin)
        // Does not contain ":"
        assertTrue(!legacyHash.contains(":"))

        // Correct PIN with legacy hash should return Valid with migratedHash
        val result = CryptoUtils.verifyPin(pin, legacyHash)
        assertTrue(result is CryptoUtils.PinVerificationResult.Valid)
        val migrated = (result as CryptoUtils.PinVerificationResult.Valid).migratedHash
        assertNotNull(migrated)
        assertTrue(migrated!!.contains(":"))

        // The migrated hash should now verify the PIN
        val migratedCheck = CryptoUtils.verifyPin(pin, migrated)
        assertTrue(migratedCheck is CryptoUtils.PinVerificationResult.Valid)

        // Wrong PIN with legacy hash should return INVALID
        val wrongResult = CryptoUtils.verifyPin("0000", legacyHash)
        assertEquals(CryptoUtils.PinVerificationResult.INVALID, wrongResult)
    }
}
