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