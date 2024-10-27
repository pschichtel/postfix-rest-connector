package tel.schich.postfixrestconnector

import io.ktor.client.plugins.HttpTimeoutConfig.Companion.INFINITE_TIMEOUT_MS
import io.ktor.http.Url
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.core.remaining
import kotlinx.coroutines.runBlocking
import tel.schich.postfixrestconnector.mocks.MockSocketmapLookupHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

//    @Test
//    fun testRequest() = runBlocking {
//        val data = "test 0123456789"
//        val encodedData = data.length.toString() + ":" + data + ","
//        val buf = stringBuffer(encodedData)
//        val state = handler.createState()
//        state.read(ByteChannel(), buf)
//        assertFalse(buf.hasNext())
//        assertEquals(data, handler.data)
//    }
}
