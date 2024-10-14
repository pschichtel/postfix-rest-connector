package tel.schich.postfixrestconnector

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

actual fun setupHttpClient(config: Configuration): HttpClient {
    return HttpClient(CIO) {
        install(HttpTimeout)
        install(ContentNegotiation) {
            json()
        }
        install(UserAgent) {
            agent = config.userAgent
        }
    }
}