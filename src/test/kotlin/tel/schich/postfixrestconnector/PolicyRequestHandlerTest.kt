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
import tel.schich.postfixrestconnector.mocks.MockPolicyRequestHandler
import tel.schich.postfixrestconnector.mocks.MockSocketChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class PolicyRequestHandlerTest {
    private val ENDPOINT = Endpoint(
        "test-policy",
        Url("http://localhost"),
        "0.0.0.0",
        9000,
        "test123",
        1,
        "policy",
        DEFAULT_RESPONSE_VALUE_SEPARATOR
    )
    private val HANDLER = MockPolicyRequestHandler(ENDPOINT)
    
    @Test
    fun readCompleteRequestComplete() {
        val firstLine = "a=b\n\n"
        val buf = stringBuffer(firstLine)
        val state = HANDLER.createState()
        assertEquals(buf.remaining().toLong(), state.read(MockSocketChannel.DEFAULT, buf))
        val data = HANDLER.getData()
        assertNotNull(data)
        assertEquals(1, data.size)
        assertEquals(data[0], Pair("a", "b"))
    }

    @Test
    fun readCompleteRequestBroken() {
        val firstLine = "a=b\n\na"
        val buf = stringBuffer(firstLine)
        val state = HANDLER.createState()
        assertEquals(buf.remaining().toLong(), state.read(MockSocketChannel.DEFAULT, buf))
        val data = HANDLER.getData()
        assertNotNull(data)
        assertEquals(1, data.size)
        assertEquals(data[0], Pair("a", "b"))
    }

    @Test
    fun readFragmentedRequestComplete() {
        val firstLine = "a=b\n"
        val secondLine = "\n"
        val state = HANDLER.createState()
        var buf = stringBuffer(firstLine)
        assertEquals(buf.remaining().toLong(), state.read(MockSocketChannel.DEFAULT, buf))
        var data = HANDLER.getData()
        assertNull(data)
        buf = stringBuffer(secondLine)
        assertEquals(buf.remaining().toLong(), state.read(MockSocketChannel.DEFAULT, buf))
        data = HANDLER.getData()
        assertEquals(listOf(Pair("a", "b")), data)
    }

    @Test
    fun readFragmentedRequestBroken() {
        val firstLine = "a=b\n"
        val secondLine = "\na"
        val rest = "=c\n\n"
        val state = HANDLER.createState()
        var buf = stringBuffer(firstLine)
        assertEquals(buf.remaining().toLong(), state.read(MockSocketChannel.DEFAULT, buf))
        assertNull(HANDLER.getData())
        buf = stringBuffer(secondLine)
        assertEquals(buf.remaining().toLong(), state.read(MockSocketChannel.DEFAULT, buf))
        assertEquals(listOf(Pair("a", "b")), HANDLER.getData())
        buf = stringBuffer(rest)
        assertEquals(buf.remaining().toLong(), state.read(MockSocketChannel.DEFAULT, buf))
        assertEquals(listOf(Pair("a", "c")), HANDLER.getData())
    }
}