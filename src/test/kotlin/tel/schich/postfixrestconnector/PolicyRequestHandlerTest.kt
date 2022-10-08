package tel.schich.postfixrestconnector

import io.ktor.http.Url
import tel.schich.postfixrestconnector.mocks.MockPolicyRequestHandler
import tel.schich.postfixrestconnector.mocks.MockSocketChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
    fun readCompleteRequestComplete() {
        val firstLine = "a=b\n\n"
        val buf = stringBuffer(firstLine)
        val state = handler.createState()
        assertEquals(buf.remaining().toLong(), state.read(MockSocketChannel.DEFAULT, buf))
        val data = handler.getData()
        assertNotNull(data)
        assertEquals(1, data.size)
        assertEquals(data[0], Pair("a", "b"))
    }

    @Test
    fun readCompleteRequestBroken() {
        val firstLine = "a=b\n\na"
        val buf = stringBuffer(firstLine)
        val state = handler.createState()
        assertEquals(buf.remaining().toLong(), state.read(MockSocketChannel.DEFAULT, buf))
        val data = handler.getData()
        assertNotNull(data)
        assertEquals(1, data.size)
        assertEquals(data[0], Pair("a", "b"))
    }

    @Test
    fun readFragmentedRequestComplete() {
        val firstLine = "a=b\n"
        val secondLine = "\n"
        val state = handler.createState()
        var buf = stringBuffer(firstLine)
        assertEquals(buf.remaining().toLong(), state.read(MockSocketChannel.DEFAULT, buf))
        var data = handler.getData()
        assertNull(data)
        buf = stringBuffer(secondLine)
        assertEquals(buf.remaining().toLong(), state.read(MockSocketChannel.DEFAULT, buf))
        data = handler.getData()
        assertEquals(listOf(Pair("a", "b")), data)
    }

    @Test
    fun readFragmentedRequestBroken() {
        val firstLine = "a=b\n"
        val secondLine = "\na"
        val rest = "=c\n\n"
        val state = handler.createState()
        var buf = stringBuffer(firstLine)
        assertEquals(buf.remaining().toLong(), state.read(MockSocketChannel.DEFAULT, buf))
        assertNull(handler.getData())
        buf = stringBuffer(secondLine)
        assertEquals(buf.remaining().toLong(), state.read(MockSocketChannel.DEFAULT, buf))
        assertEquals(listOf(Pair("a", "b")), handler.getData())
        buf = stringBuffer(rest)
        assertEquals(buf.remaining().toLong(), state.read(MockSocketChannel.DEFAULT, buf))
        assertEquals(listOf(Pair("a", "c")), handler.getData())
    }
}
