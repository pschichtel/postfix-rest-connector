package tel.schich.postfixrestconnector

import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.utils.io.readUTF8Line
import kotlin.test.Test
import kotlin.test.assertEquals

class TcpLookupHandlerTest {
    private fun queryTest(key: String, results: List<String>, expected: String) = systemTest(TcpLookupHandler.MODE_NAME) { write, readChannel, requests ->
        write("get $key")

        val (call, _, response) = requests.receive()
        assertEquals(key, call.parameters["key"])

        response.complete(OK with results)

        assertEquals("200 $expected", readChannel.readUTF8Line())
    }

    private fun encodingTest(input: String, expected: String) =
        queryTest("k", listOf(input), expected)

    private fun decodeTest(input: String, expected: String) = systemTest(TcpLookupHandler.MODE_NAME) { write, readChannel, requests ->
        write("get $input")

        val (call) = requests.receive()
        assertEquals(expected, call.parameters["key"])
    }

    @Test
    fun testDecode() {
        assertEquals("+ %&§ß", TcpLookup.decodeRequest("+%20%25&%C2%A7%C3%9F"))
    }

    @Test
    fun testDecodeE2E() = systemTest(TcpLookupHandler.MODE_NAME) { write, readChannel, requests ->
        decodeTest("+%20%25&%C2%A7%C3%9F", "+ %&§ß")
    }

    @Test
    fun testEncode() {
        assertEquals("+%20%25&%C2%A7%C3%9F", TcpLookup.encodeResponse("+ %&§ß"))
    }

    @Test
    fun testEncodeE2E() {
        encodingTest("+ %&§ß", "+%20%25&%C2%A7%C3%9F")
    }

    @Test
    fun systemTest() = systemTest(TcpLookupHandler.MODE_NAME) { write, readChannel, requests ->
        val key = "k"
        val response1 = "r1"
        val response2 = "r2"
        write("get $key")

        val (call, _, response) = requests.receive()
        assertEquals(key, call.parameters["key"])

        response.complete(OK with listOf(response1, response2))

        assertEquals("200 $response1,$response2", readChannel.readUTF8Line())
    }

}
