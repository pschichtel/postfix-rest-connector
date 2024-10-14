package tel.schich.postfixrestconnector.mocks

import io.ktor.utils.io.ByteWriteChannel
import tel.schich.postfixrestconnector.Endpoint
import tel.schich.postfixrestconnector.SocketmapLookupHandler
import tel.schich.postfixrestconnector.TestHttpClient
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class MockSocketmapLookupHandler(endpoint: Endpoint) : SocketmapLookupHandler(endpoint, TestHttpClient) {
    var data: String? = null
        private set

    override suspend fun handleRequest(ch: ByteWriteChannel, id: Uuid, requestData: String) {
        data = requestData
//        super.handleRequest(ch, id, requestData)
    }
}
