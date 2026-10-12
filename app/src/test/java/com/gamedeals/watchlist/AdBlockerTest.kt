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
            null,
        ).forEach { assertFalse(it.toString(), AdBlocker.isAdHost(it)) }
    }
}
