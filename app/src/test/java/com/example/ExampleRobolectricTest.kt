package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("GlucoseFrame", appName)
  }

  @Test
  fun `test KeystoreManager updateCredentials`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val keystoreManager = com.example.core.security.KeystoreManager(context)
    keystoreManager.clearCredentials()

    val updated1 = keystoreManager.updateCredentials {
      it.copy(libreEmail = "user1@example.com", librePassword = "password1")
    }
    assertEquals("user1@example.com", updated1.libreEmail)

    val updated2 = keystoreManager.updateCredentials {
      it.copy(libreToken = "token_xyz", librePatientId = "patient_123")
    }
    assertEquals("user1@example.com", updated2.libreEmail)
    assertEquals("token_xyz", updated2.libreToken)
    assertEquals("patient_123", updated2.librePatientId)

    val reloaded = keystoreManager.loadCredentials()
    assertEquals("user1@example.com", reloaded.libreEmail)
    assertEquals("token_xyz", reloaded.libreToken)
    assertEquals("patient_123", reloaded.librePatientId)
  }
}
