package tel.schich.postfixrestconnector

import kotlin.test.Test
import kotlin.test.assertEquals

class TcpLookupHandlerTest {
    @Test
    fun testDecode() {
        assertEquals("+ %&§ß", TcpLookup.decodeRequest("+%20%25&%C2%A7%C3%9F"))
    }

    @Test
    fun testEncode() {
        assertEquals("+%20%25%26%C2%A7%C3%9F", TcpLookup.encodeResponse("+ %&§ß"))
    }
}
