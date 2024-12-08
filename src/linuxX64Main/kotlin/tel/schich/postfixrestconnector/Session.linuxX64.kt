package tel.schich.postfixrestconnector

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.curl.Curl

actual fun setupHttpClient(customizer: HttpClientConfig<out HttpClientEngineConfig>.() -> Unit): HttpClient {
    return HttpClient(Curl) {
        customizer()
    }
}