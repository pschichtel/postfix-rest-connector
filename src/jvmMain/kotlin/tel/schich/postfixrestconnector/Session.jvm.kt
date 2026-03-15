package tel.schich.postfixrestconnector

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.java.Java
import java.net.http.HttpClient.Version.HTTP_2

actual fun setupHttpClient(customizer: HttpClientConfig<out HttpClientEngineConfig>.() -> Unit): HttpClient {
    return HttpClient(Java) {
        engine {
            protocolVersion = HTTP_2
        }
        customizer()
    }
}
