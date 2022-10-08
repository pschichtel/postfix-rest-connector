package tel.schich.postfixrestconnector.mocks

import io.ktor.utils.io.ByteWriteChannel
import tel.schich.postfixrestconnector.Endpoint
import tel.schich.postfixrestconnector.SocketmapLookupHandler
import tel.schich.postfixrestconnector.TestHttpClient
import java.util.UUID

class MockSocketmapLookupHandler(endpoint: Endpoint) : SocketmapLookupHandler(endpoint, TestHttpClient, "test") {
    var data: String? = null
        private set

    override suspend fun handleRequest(ch: ByteWriteChannel, id: UUID, requestData: String) {
        data = requestData
//        super.handleRequest(ch, id, requestData)
    }
}
