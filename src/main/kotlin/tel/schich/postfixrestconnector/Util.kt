package tel.schich.postfixrestconnector

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.utils.EmptyContent
import mu.KLogger
import java.util.UUID

fun HttpRequestBuilder.logRequest(id: UUID, logger: KLogger) {
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
    logger.info { "Request of $id:\n$sb" }
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