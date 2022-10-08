/*
 * Postfix REST Connector - A simple TCP server that can be used as tcp lookup, socketmap lookup or policy check server for the Postfix mail server.
 * Copyright © 2018 Phillip Schichtel (phillip@schich.tel)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package tel.schich.postfixrestconnector

import io.ktor.http.Url
import tel.schich.postfixrestconnector.TestHelper.stringBuffer
import tel.schich.postfixrestconnector.mocks.MockSocketChannel
import tel.schich.postfixrestconnector.mocks.MockSocketmapLookupHandler
import java.nio.channels.SocketChannel
import kotlin.test.Test
import kotlin.test.assertEquals

class SocketmapLookupHandlerTest {
    private val ENDPOINT = Endpoint(
        "test-policy",
        Url("http://localhost"),
        "0.0.0.0",
        9000,
        "test123",
        1,
        SocketmapLookupHandler.MODE_NAME,
        DEFAULT_RESPONSE_VALUE_SEPARATOR
    )
    private val HANDLER = MockSocketmapLookupHandler(ENDPOINT)

    @Test
    fun testRequest() {
        val d = "test 0123456789"
        val s = d.length.toString() + ":" + d + ","
        val b = stringBuffer(s)
        val sc: SocketChannel = MockSocketChannel()
        val state = HANDLER.createState()
        assertEquals(s.length.toLong(), state.read(sc, b))
        assertEquals(d, HANDLER.data)
    }
}