package tel.schich.postfixrestconnector

import io.ktor.http.HttpStatusCode.Companion.OK
import tel.schich.postfixrestconnector.PolicyRequestHandler.Companion.MODE_NAME
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PolicyRequestHandlerTest {
    @Test
    fun readCompleteRequestComplete() = systemTest(MODE_NAME) {
        write("a=b\n\n")

        val (_, body, response) = receiveReq()

        assertNotNull(body)
        val params = body.split("\n").map {
            val (name, value) = it.split("=", limit = 2)
            name to value
        }
        assertEquals(listOf("a" to "b"), params)

        response.complete(OK with "accept")

        assertEquals("action=accept", readln())
    }

    @Test
    fun readCompleteRequestBroken() = systemTest(MODE_NAME) {
        write("a=b\n\na")

        val (_, body, response) = receiveReq()

        assertNotNull(body)
        val params = body.split("\n").map {
            val (name, value) = it.split("=", limit = 2)
            name to value
        }
        assertEquals(listOf("a" to "b"), params)

        response.complete(OK with "accept")

        assertEquals("action=accept", readln())
    }

    @Test
    fun readFragmentedRequestComplete() = systemTest(MODE_NAME) {
        writeln("a=b")
        writeln()

        val (_, body, response) = receiveReq()

        assertNotNull(body)
        val params = body.split("\n").map {
            val (name, value) = it.split("=", limit = 2)
            name to value
        }
        assertEquals(listOf("a" to "b"), params)

        response.complete(OK with "accept")

        assertEquals("action=accept", readln())
    }

    @Test
    fun readFragmentedRequestBroken() = systemTest(MODE_NAME) {

        suspend fun handle(expectedParams: List<Pair<String, String>>) {
            val (_, body, response) = receiveReq()

            assertNotNull(body)
            val actualParams = body.split("\n").map {
                val (name, value) = it.split("=", limit = 2)
                name to value
            }
            assertEquals(expectedParams, actualParams)

            response.complete(OK with "accept")

            assertEquals("action=accept", readln())
            assertEquals("", readln())
        }

        write("a=b\n")
        write("\na")
        handle(listOf("a" to "b"))


        write("=c\n\n")
        handle(listOf("a" to "c"))
    }
}
