package tel.schich.postfixrestconnector

import kotlinx.io.IOException

object Netstring {
    private const val EMPTY = "0:,"

    fun parseOne(s: String): String {
        val strings = parse(s)
        if (strings.size > 1) {
            throw IOException("Multiple netstrings detected, but only one requested!")
        }
        return strings[0]
    }

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
                    throw IOException("Expected ',' at " + offset + ", but got '" + s[commaPos] + "' after data!")
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
