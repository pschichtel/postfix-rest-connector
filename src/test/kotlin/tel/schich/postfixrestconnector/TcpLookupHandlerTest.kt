package tel.schich.postfixrestconnector

import tel.schich.postfixrestconnector.TcpLookupHandler.Companion.decodeURLEncodedData
import tel.schich.postfixrestconnector.TcpLookupHandler.Companion.encodeResponseData
import kotlin.test.Test
import kotlin.test.assertEquals

class TcpLookupHandlerTest {
    @Test
    fun testDecode() {
        assertEquals("+ %&§ß", decodeURLEncodedData("+%20%25&%C2%A7%C3%9F"))
    }

    @Test
    fun testEncode() {
        assertEquals("+%20%25%26%C2%A7%C3%9F", encodeResponseData("+ %&§ß"))
    }
}
