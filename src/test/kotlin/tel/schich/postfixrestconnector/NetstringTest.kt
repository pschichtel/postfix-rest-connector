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

import tel.schich.postfixrestconnector.Netstring.compile
import tel.schich.postfixrestconnector.Netstring.compileOne
import tel.schich.postfixrestconnector.Netstring.parse
import tel.schich.postfixrestconnector.Netstring.parseOne
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NetstringTest {
    @Test
    fun parseOne() {
        val t = assertFailsWith<IOException> {
            assertEquals("a", parseOne("1:a,2:bc,"))
        }
        assertNull(t.cause)
        assertEquals("a", parseOne("1:a,"))
    }

    @Test
    fun parse() {
        assertEquals(listOf("a", "bc"), parse("1:a,2:bc,"))
    }

    @Test
    fun compile() {
        assertEquals("0:,", compile(listOf("")))
        assertEquals("", compile(emptyList()))
        assertEquals("1:a,2:bc,", compile(listOf("a", "bc")))
    }

    @Test
    fun compileOne() {
        assertEquals("0:,", compileOne(""))
    }
}