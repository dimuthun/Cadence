package com.cadence.platform.http

import com.cadence.BuildConfig
import com.cadence.core.coroutines.IoDispatcher
import com.cadence.domain.action.ActionConfig
import com.cadence.domain.action.ActionResult
import com.cadence.domain.action.HttpMethod
import com.cadence.domain.engine.ExecutionContext
import com.cadence.domain.engine.render
import com.cadence.domain.plugin.ActionHandler
import com.cadence.domain.variable.GlobalVariableRepository
import com.cadence.domain.variable.JsonPath
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URISyntaxException
import java.net.UnknownHostException
import javax.inject.Inject
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runInterruptible

/**
 * [ActionConfig.HttpRequest] using `HttpURLConnection`, so no extra networking library is needed.
 *
 * Writes `http.status`, `http.body`, `http.content_type` and `http.duration_ms` as local
 * variables for later actions, and optionally saves the body (or a JSON value) to a global
 * variable.
 */
class HttpRequestActionHandler @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val globals: GlobalVariableRepository,
) : ActionHandler<ActionConfig.HttpRequest> {

    override val configType: KClass<ActionConfig.HttpRequest> = ActionConfig.HttpRequest::class

    /** The request's own timeout covers connecting and reading; allow a margin on top of it. */
    override fun timeout(config: ActionConfig.HttpRequest): Duration = (config.timeoutSeconds * 2 + 5).seconds

    override suspend fun execute(config: ActionConfig.HttpRequest, context: ExecutionContext): ActionResult {
        val parameters = config.queryParameters.map { context.render(it.name) to context.render(it.value) }
        val url = HttpUrls.withQueryParameters(context.render(config.url).trim(), parameters)
        val uri = try {
            URI(url)
        } catch (e: URISyntaxException) {
            return ActionResult.Failure("Not a valid URL: $url")
        }
        if (uri.scheme?.lowercase() !in ALLOWED_SCHEMES || uri.host.isNullOrBlank()) {
            return ActionResult.Failure("Only http:// and https:// URLs are supported")
        }
        val headers = config.headers
            .map { context.render(it.name).trim() to context.render(it.value) }
            .filter { (name, _) -> name.isNotEmpty() }
        val body = if (config.method.allowsBody) config.body?.let { context.render(it) } else null

        val mark = TimeSource.Monotonic.markNow()
        val response = try {
            runInterruptible(ioDispatcher) {
                send(uri, config.method, headers, body, config.contentType, config.timeoutSeconds * 1_000)
            }
        } catch (e: SocketTimeoutException) {
            return ActionResult.Failure("No response within ${config.timeoutSeconds} s", e)
        } catch (e: UnknownHostException) {
            return ActionResult.Failure("Couldn't find ${uri.host}. Check the address and the internet connection.", e)
        } catch (e: IOException) {
            return ActionResult.Failure("Network error: ${e.message ?: e::class.simpleName}", e)
        }
        val elapsed = mark.elapsedNow().inWholeMilliseconds

        context.locals["http.status"] = response.status.toString()
        context.locals["http.body"] = response.body.take(MAX_VARIABLE_CHARS)
        context.locals["http.content_type"] = response.contentType.orEmpty()
        context.locals["http.duration_ms"] = elapsed.toString()

        val path = config.jsonPath?.takeIf { it.isNotBlank() }
        val extracted = path?.let { JsonPath.extract(response.body, it) }
        config.saveResponseTo?.let { name ->
            globals.set(name, (if (path != null) extracted.orEmpty() else response.body).take(MAX_VARIABLE_CHARS))
        }

        val summary = "${config.method} ${uri.host} → HTTP ${response.status} in $elapsed ms"
        return when {
            config.failOnErrorStatus && response.status >= 400 -> ActionResult.Failure("HTTP ${response.status} from ${uri.host}")
            path != null && extracted == null -> ActionResult.Failure("$summary, but \"$path\" wasn't found in the response")
            else -> ActionResult.Success(summary)
        }
    }

    private data class Response(val status: Int, val body: String, val contentType: String?)

    private fun send(
        uri: URI,
        method: HttpMethod,
        headers: List<Pair<String, String>>,
        body: String?,
        contentType: String,
        timeoutMillis: Int,
    ): Response {
        val connection = uri.toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
            connection.instanceFollowRedirects = true
            connection.setRequestMethodCompat(method)
            connection.setRequestProperty("User-Agent", "Cadence/${BuildConfig.VERSION_NAME} (Android)")
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            if (body != null) {
                if (headers.none { it.first.equals("Content-Type", ignoreCase = true) }) {
                    connection.setRequestProperty("Content-Type", contentType)
                }
                val bytes = body.toByteArray(Charsets.UTF_8)
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val text = stream?.use { it.readLimited(MAX_RESPONSE_BYTES) }?.toString(Charsets.UTF_8).orEmpty()
            return Response(status, text, connection.contentType)
        } finally {
            connection.disconnect()
        }
    }

    /** `HttpURLConnection` may reject PATCH; fall back to the widely supported override header. */
    private fun HttpURLConnection.setRequestMethodCompat(method: HttpMethod) {
        try {
            requestMethod = method.name
        } catch (e: ProtocolException) {
            requestMethod = "POST"
            setRequestProperty("X-HTTP-Method-Override", method.name)
        }
    }

    private fun InputStream.readLimited(limit: Int): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8 * 1024)
        while (buffer.size() < limit) {
            val read = read(chunk, 0, minOf(chunk.size, limit - buffer.size()))
            if (read < 0) break
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    private val HttpMethod.allowsBody: Boolean
        get() = this == HttpMethod.POST || this == HttpMethod.PUT || this == HttpMethod.PATCH || this == HttpMethod.DELETE

    private companion object {
        val ALLOWED_SCHEMES = setOf("http", "https")
        const val MAX_RESPONSE_BYTES = 1024 * 1024
        const val MAX_VARIABLE_CHARS = 100_000
    }
}
