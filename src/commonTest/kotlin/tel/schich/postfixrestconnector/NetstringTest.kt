package tel.schich.postfixrestconnector

import kotlinx.io.IOException
import tel.schich.postfixrestconnector.Netstring.compile
import tel.schich.postfixrestconnector.Netstring.compileOne
import tel.schich.postfixrestconnector.Netstring.parse
import tel.schich.postfixrestconnector.Netstring.parseOne
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
