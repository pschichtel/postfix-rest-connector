package tel.schich.postfixrestconnector

import io.ktor.http.HttpStatusCode.Companion.OK
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals

class TcpLookupHandlerTest {
    private fun queryTest(key: String, results: List<String>, expected: String) = systemTest(TcpLookupHandler.MODE_NAME) {
        writeln("get $key")

        val (call, _, response) = receiveReq()
        assertEquals(key, call.parameters["key"])

        response.complete(OK with results)

        assertEquals("200 $expected", readln())
    }

    private fun encodingTest(input: String, expected: String) =
        queryTest("k", listOf(input), expected)

    private fun decodeTest(input: String, expected: String) = systemTest(TcpLookupHandler.MODE_NAME) {
        writeln("get $input")

        val (call) = receiveReq()
        assertEquals(expected, call.parameters["key"])
    }

    @Test
    fun testDecode() {
        assertEquals("+ %&§ß", TcpLookup.decodeRequest("+%20%25&%C2%A7%C3%9F"))
    }

    @Test
    fun testDecodeE2E() {
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
    fun simpleQuery() = systemTest(TcpLookupHandler.MODE_NAME) {
        val key = "k"
        val response1 = "r1"
        val response2 = "r2"
        writeln("get $key")

        val (call, _, response) = receiveReq()
        assertEquals(key, call.parameters["key"])

        response.complete(OK with listOf(response1, response2))

        assertEquals("200 $response1,$response2", readln())
    }

    @Test
    fun simpleQueryWithPartialWrites() = systemTest(TcpLookupHandler.MODE_NAME) {
        val key = "k"
        val response1 = "r1"
        val response2 = "r2"
        write("g")
        delay(100)
        write("e")
        delay(100)
        write("t ")
        delay(100)
        writeln(key)

        val (call, _, response) = receiveReq()
        assertEquals(key, call.parameters["key"])

        response.complete(OK with listOf(response1, response2))

        assertEquals("200 $response1,$response2", readln())
    }

}
