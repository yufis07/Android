package com.gamedeals.watchlist

import com.gamedeals.watchlist.web.AdBlocker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockerTest {
    @Test
    fun blocksGoogleAdHosts() {
        listOf(
            "pagead2.googlesyndication.com",
            "tpc.googlesyndication.com",
            "googleads.g.doubleclick.net",
            "securepubads.g.doubleclick.net",
            "www.googleadservices.com",
            "www.googletagservices.com",
            "adservice.google.com",
            "adservice.google.co.uk",
            "fundingchoicesmessages.google.com",
            "imasdk.googleapis.com",
            "admob.com",
            // other ad networks
            "c.amazon-adsystem.com",
            "ib.adnxs.com",
            "static.criteo.net",
            "cdn.taboola.com",
            "widgets.outbrain.com",
            "ads.pubmatic.com",
            "fastlane.rubiconproject.com",
            "match.adsrvr.org",
        ).forEach { assertTrue(it, AdBlocker.isAdHost(it)) }
    }

    @Test
    fun allowsDealSitesAndLogin() {
        listOf(
            "psdeals.net",
            "xbdeals.net",
            "ntdeals.net",
            "accounts.google.com",
            "www.google.com",
            "fonts.googleapis.com",
            "www.gstatic.com",
            "notdoubleclick.net",
            "store.playstation.com",
            "challenges.cloudflare.com",
            "connect.facebook.net",
            null,
        ).forEach { assertFalse(it.toString(), AdBlocker.isAdHost(it)) }
    }

    @Test
    fun parsesDomainAndHostsLists() {
        val list = AdBlocker.parseList(
            """
            # comment
            adserver.example
            0.0.0.0 tracker.example # trailing comment
            127.0.0.1 localhost
            psdeals.net
            """.trimIndent()
        )
        assertTrue("adserver.example" in list)
        assertTrue("tracker.example" in list)
        assertFalse("localhost" in list)
        assertFalse("psdeals.net" in list) // allow-listed
    }
}
