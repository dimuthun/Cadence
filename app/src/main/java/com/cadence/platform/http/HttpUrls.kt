package com.cadence.platform.http

import java.net.URLEncoder

/** Pure URL helpers for HTTP request actions. */
object HttpUrls {

    /**
     * Appends [parameters] (already rendered, not yet encoded) to [url]'s query string,
     * percent-encoding every name and value. Existing query parameters and a `#fragment` are
     * kept. Parameters with blank names are skipped.
     */
    fun withQueryParameters(url: String, parameters: List<Pair<String, String>>): String {
        val encoded = parameters
            .filter { (name, _) -> name.isNotBlank() }
            .joinToString("&") { (name, value) -> "${encode(name.trim())}=${encode(value)}" }
        if (encoded.isEmpty()) return url
        val fragmentStart = url.indexOf('#').takeIf { it >= 0 } ?: url.length
        val base = url.substring(0, fragmentStart)
        val fragment = url.substring(fragmentStart)
        val separator = when {
            '?' !in base -> "?"
            base.endsWith("?") || base.endsWith("&") -> ""
            else -> "&"
        }
        return base + separator + encoded + fragment
    }

    /** RFC 3986 percent-encoding (spaces become `%20`, not `+`). */
    fun encode(value: String): String =
        // The Charset overload needs API 33; the charset-name overload works on every version.
        URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
            .replace("%7E", "~")
}
