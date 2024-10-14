package tel.schich.postfixrestconnector

import io.ktor.http.Url
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.core.remaining
import kotlinx.coroutines.runBlocking
import tel.schich.postfixrestconnector.mocks.MockPolicyRequestHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PolicyRequestHandlerTest {
    private val endpoint = Endpoint(
        "test-policy",
        Url("http://localhost"),
        "0.0.0.0",
        9000,
        "test123",
        1,
        "policy",
        DEFAULT_RESPONSE_VALUE_SEPARATOR
    )
    private val handler = MockPolicyRequestHandler(endpoint)
    
    @Test
    fun readCompleteRequestComplete() = runBlocking {
        val firstLine = "a=b\n\n"
        val buf = stringBuffer(firstLine)
        val state = handler.createState()
        state.read(ByteChannel(), buf)
        assertTrue(buf.exhausted())
        val data = handler.getDataAsPairs()
        assertNotNull(data)
        assertFalse(buf.exhausted())
        assertEquals(data[0], Pair("a", "b"))
    }

    @Test
    fun readCompleteRequestBroken() = runBlocking {
        val firstLine = "a=b\n\na"
        val buf = stringBuffer(firstLine)
        val state = handler.createState()
        state.read(ByteChannel(), buf)
        assertTrue(buf.exhausted())
        val data = handler.getDataAsPairs()
        assertNotNull(data)
        assertEquals(1, data.size)
        assertEquals(data[0], Pair("a", "b"))
    }

    @Test
    fun readFragmentedRequestComplete() = runBlocking {
        val firstLine = "a=b\n"
        val secondLine = "\n"
        val state = handler.createState()
        var buf = stringBuffer(firstLine)
        state.read(ByteChannel(), buf)
        assertTrue(buf.exhausted())
        var data = handler.getDataAsPairs()
        assertNull(data)
        buf = stringBuffer(secondLine)
        state.read(ByteChannel(), buf)
        assertTrue(buf.exhausted())
        data = handler.getDataAsPairs()
        assertEquals(listOf(Pair("a", "b")), data)
    }

    @Test
    fun readFragmentedRequestBroken() = runBlocking {
        val firstLine = "a=b\n"
        val secondLine = "\na"
        val rest = "=c\n\n"
        val state = handler.createState()
        var buf = stringBuffer(firstLine)
        state.read(ByteChannel(), buf)
        assertTrue(buf.exhausted())
        assertNull(handler.getData())
        buf = stringBuffer(secondLine)
        state.read(ByteChannel(), buf)
        assertTrue(buf.exhausted())
        assertEquals(listOf(Pair("a", "b")), handler.getDataAsPairs())
        buf = stringBuffer(rest)
        state.read(ByteChannel(), buf)
        assertTrue(buf.exhausted())
        assertEquals(listOf(Pair("a", "c")), handler.getDataAsPairs())
    }
}
