package tel.schich.postfixrestconnector

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeout.Plugin.INFINITE_TIMEOUT_MS
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpResponseData
import io.ktor.client.utils.EmptyContent
import io.ktor.http.Headers
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.date.GMTDate
import java.nio.ByteBuffer

val TestHttpClient = HttpClient(MockEngine) {
    engine {
        requestHandlers += { req ->
            HttpResponseData(
                HttpStatusCode.OK,
                GMTDate.START,
                Headers.Empty,
                HttpProtocolVersion.HTTP_1_0,
                EmptyContent,
                req.executionContext,
            )
        }
    }
    install(HttpTimeout) {
        requestTimeoutMillis = INFINITE_TIMEOUT_MS
        connectTimeoutMillis = INFINITE_TIMEOUT_MS
        socketTimeoutMillis = INFINITE_TIMEOUT_MS
    }
    install(ContentNegotiation) {
        json()
    }
    install(UserAgent) {
        agent = "test"
    }
}

fun stringBuffer(s: String): ByteBuffer {
    return Charsets.US_ASCII.encode(s)
}
