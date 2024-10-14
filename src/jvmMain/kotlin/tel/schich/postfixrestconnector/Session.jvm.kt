package tel.schich.postfixrestconnector

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import java.net.http.HttpClient.Version.HTTP_2

actual fun setupHttpClient(config: Configuration): HttpClient {
    return HttpClient(Java) {
        engine {
            protocolVersion = HTTP_2
        }
        install(HttpTimeout)
        install(ContentNegotiation) {
            json()
        }
        install(UserAgent) {
            agent = config.userAgent
        }
    }
}