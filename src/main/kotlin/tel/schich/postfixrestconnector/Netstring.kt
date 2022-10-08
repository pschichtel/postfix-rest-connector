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

import java.io.IOException

object Netstring {
    const val EMPTY = "0:,"
    
    @JvmStatic
    @Throws(IOException::class)
    fun parseOne(s: String): String {
        val strings = parse(s)
        if (strings.size > 1) {
            throw IOException("Multiple netstrings detected, but only one requested!")
        }
        return strings[0]
    }

    @JvmStatic
    @Throws(IOException::class)
    fun parse(s: String): List<String> {
        var colonPos: Int
        var offset = 0
        var runLength: Int
        var commaPos: Int
        val out: MutableList<String> = ArrayList()
        try {
            while (offset < s.length) {
                colonPos = s.indexOf(':', offset)
                if (colonPos == -1) {
                    throw IOException("Expected to find a ':' after $offset, but didn't!")
                }
                runLength = s.substring(offset, colonPos).toInt()
                commaPos = colonPos + 1 + runLength
                if (s[commaPos] != ',') {
                    throw IOException(
                        "Expected ',' at " + offset + ", but got '" + s[commaPos] + "' after data!"
                    )
                }
                out.add(s.substring(colonPos + 1, commaPos))
                offset = commaPos + 1
            }
        } catch (e: NumberFormatException) {
            throw IOException("Failed to parse netstring!", e)
        } catch (e: IndexOutOfBoundsException) {
            throw IOException("Failed to parse netstring!", e)
        }
        return out
    }

    @JvmStatic
    fun compile(strings: List<String>): String {
        if (strings.isEmpty()) {
            return ""
        }
        val out = StringBuilder()
        for (s in strings) {
            compile(s, out)
        }
        return out.toString()
    }

    @JvmStatic
    fun compileOne(s: String): String {
        if (s.isEmpty()) {
            return EMPTY
        }
        val out = StringBuilder()
        compile(s, out)
        return out.toString()
    }

    private fun compile(s: String, out: StringBuilder) {
        if (s.isEmpty()) {
            out.append(EMPTY)
        } else {
            out.append(s.length).append(':').append(s).append(',')
        }
    }
}