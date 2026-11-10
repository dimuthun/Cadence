package com.cadence.platform.http

import org.junit.Assert.assertEquals
import org.junit.Test

class HttpUrlsTest {

    @Test
    fun `parameters are appended with a question mark`() {
        assertEquals(
            "https://api.test/log?battery=42&lat=6.927079",
            HttpUrls.withQueryParameters("https://api.test/log", listOf("battery" to "42", "lat" to "6.927079")),
        )
    }

    @Test
    fun `existing query and fragment are kept`() {
        assertEquals(
            "https://api.test/log?v=1&battery=42#top",
            HttpUrls.withQueryParameters("https://api.test/log?v=1#top", listOf("battery" to "42")),
        )
        assertEquals(
            "https://api.test/?a=1",
            HttpUrls.withQueryParameters("https://api.test/?", listOf("a" to "1")),
        )
    }

    @Test
    fun `names and values are percent-encoded`() {
        assertEquals(
            "https://api.test/?msg=Hello%20world%20%26%20more%3D%E2%9C%93&my%20key=a%2Fb~c",
            HttpUrls.withQueryParameters(
                "https://api.test/",
                listOf("msg" to "Hello world & more=✓", "my key" to "a/b~c"),
            ),
        )
    }

    @Test
    fun `blank names are skipped and no parameters leaves the url unchanged`() {
        assertEquals("https://api.test/x", HttpUrls.withQueryParameters("https://api.test/x", listOf(" " to "v")))
        assertEquals("https://api.test/x", HttpUrls.withQueryParameters("https://api.test/x", emptyList()))
    }

    @Test
    fun `empty values are sent as empty`() {
        assertEquals("https://api.test/?q=", HttpUrls.withQueryParameters("https://api.test/", listOf("q" to "")))
    }
}
