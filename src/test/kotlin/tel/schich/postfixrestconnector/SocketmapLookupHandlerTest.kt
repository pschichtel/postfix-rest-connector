package tel.schich.postfixrestconnector

import io.ktor.client.plugins.HttpTimeout.Plugin.INFINITE_TIMEOUT_MS
import io.ktor.http.Url
import io.ktor.utils.io.ByteWriteChannel
import kotlinx.coroutines.runBlocking
import tel.schich.postfixrestconnector.mocks.MockSocketChannel
import tel.schich.postfixrestconnector.mocks.MockSocketmapLookupHandler
import kotlin.test.Test
import kotlin.test.assertEquals

class SocketmapLookupHandlerTest {
    private val endpoint = Endpoint(
        "test-policy",
        Url("http://localhost"),
        "0.0.0.0",
        9000,
        "test123",
        INFINITE_TIMEOUT_MS,
        SocketmapLookupHandler.MODE_NAME,
        DEFAULT_RESPONSE_VALUE_SEPARATOR
    )
    private val handler = MockSocketmapLookupHandler(endpoint)

    @Test
    fun testRequest() = runBlocking {
        val d = "test 0123456789"
        val s = d.length.toString() + ":" + d + ","
        val b = stringBuffer(s)
        val sc: ByteWriteChannel = MockSocketChannel()
        val state = handler.createState()
        assertEquals(s.length.toLong(), state.read(sc, b))
        assertEquals(d, handler.data)
    }
}
