package tel.schich.postfixrestconnector

import io.ktor.http.HttpStatusCode.Companion.OK
import tel.schich.postfixrestconnector.SocketmapLookupHandler.Companion.MODE_NAME
import kotlin.test.Test
import kotlin.test.assertEquals

class SocketmapLookupHandlerTest {
    @Test
    fun testRequest() = systemTest(MODE_NAME) {
        val key = "test"
        val data = "0123456789"

        write("${key.length + 1 + data.length}:$key $data,")

        val (call, _, response) = receiveReq()

        assertEquals(key, call.parameters["name"])
        assertEquals(data, call.parameters["key"])

        response.complete(OK with listOf(data))

        assertEquals("${3 + data.length}:OK $data,", readAvailable())
    }
}
