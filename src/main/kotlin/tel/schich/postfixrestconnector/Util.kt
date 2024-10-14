package tel.schich.postfixrestconnector

import io.github.oshai.kotlinlogging.KLogger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.utils.EmptyContent
import io.ktor.http.HttpMethod
import io.ktor.http.takeFrom
import kotlinx.io.IOException
import java.net.ConnectException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
suspend fun HttpClient.connectorEndpointRequest(
    endpoint: Endpoint,
    id: Uuid,
    logger: KLogger,
    builder: HttpRequestBuilder.() -> Unit,
): HttpResponse {
    return request {
        method = HttpMethod.Get
        url {
            takeFrom(endpoint.target)
        }
        headers.append("X-Auth-Token", endpoint.authToken)
        headers.append("X-Request-Id", id.toString())
        timeout {
            requestTimeoutMillis = endpoint.requestTimeout
        }
        builder()
        logRequest(id, logger)
    }
}

@OptIn(ExperimentalUuidApi::class)
fun HttpRequestBuilder.logRequest(id: Uuid, logger: KLogger) {
    val sb = StringBuilder()
    sb.append(method.value)
    sb.append(' ')
    sb.append(url.buildString())
    for ((name, values) in headers.entries()) {
        for (value in values) {
            sb.append('\n')
            sb.append(name)
            sb.append(": ")
            sb.append(value)
        }
    }
    sb.append("\n\n")
    if (body !is EmptyContent) {
        sb.append("<body>")
    }
    logger.info { "$id - connector endpoint request:\n$sb" }
}

fun encodeLookupResponse(values: Iterable<String>, separator: String): String {
    val it = values.iterator()
    if (!it.hasNext()) {
        return ""
    }
    val out = StringBuilder(it.next())
    while (it.hasNext()) {
        out.append(separator).append(it.next())
    }
    return out.toString()
}

fun ioExceptionMessage(e: IOException): String = e.message ?: when (e) {
    is ConnectException -> "Failed to connect"
    else -> "unknown IO error"
}