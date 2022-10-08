package tel.schich.postfixrestconnector.mocks

import kotlinx.serialization.json.Json
import tel.schich.postfixrestconnector.Endpoint
import tel.schich.postfixrestconnector.SocketmapLookupHandler
import java.net.http.HttpClient.newHttpClient
import java.nio.channels.SocketChannel
import java.util.UUID

class MockSocketmapLookupHandler(endpoint: Endpoint) : SocketmapLookupHandler(endpoint, newHttpClient(), Json, "test") {
    var data: String? = null
        private set

    override fun handleRequest(ch: SocketChannel, id: UUID, requestData: String) {
        data = requestData
        super.handleRequest(ch, id, requestData)
    }
}
