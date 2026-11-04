package com.example

import com.example.data.lan.LanServer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class LanServerAddressTest {

  private fun ip(text: String) = InetAddress.getByName(text)

  @Test
  fun `home network and vpn addresses are accepted`() {
    assertTrue(LanServer.isLocalClient(ip("192.168.1.20")))
    assertTrue(LanServer.isLocalClient(ip("10.0.0.5")))
    assertTrue(LanServer.isLocalClient(ip("172.20.1.9")))
    assertTrue(LanServer.isLocalClient(ip("127.0.0.1")))
    assertTrue(LanServer.isLocalClient(ip("100.101.102.103")))
  }

  @Test
  fun `public internet addresses are rejected`() {
    assertFalse(LanServer.isLocalClient(ip("8.8.8.8")))
    assertFalse(LanServer.isLocalClient(ip("172.32.0.1")))
    assertFalse(LanServer.isLocalClient(ip("100.128.0.1")))
    assertFalse(LanServer.isLocalClient(null))
  }
}
