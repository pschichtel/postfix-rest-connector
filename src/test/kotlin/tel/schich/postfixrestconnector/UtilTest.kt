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

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder
import kotlin.text.Charsets.UTF_8

class UtilTest {
    @Test
    fun appendQueryString() {
        val params = listOf(
            Pair("simply", "param"),
            Pair("c+m plicæted!", " param "),
        )
        val a = URI.create("https://localhost")
        Assertions.assertEquals(
            "https://localhost?simply=param&c%252Bm+plic%25C3%25A6ted%2521=+param+",
            appendQueryParams(a, params).toString()
        )
        val b = URI.create("https://localhost?existing=arg")
        Assertions.assertEquals(
            "https://localhost?existing=arg&simply=param&c%252Bm+plic%25C3%25A6ted%2521=+param+",
            appendQueryParams(b, params).toString()
        )
        val c = URI.create("https://localhost?simply=param")
        Assertions.assertEquals(
            "https://localhost?simply=param&simply=param&c%252Bm+plic%25C3%25A6ted%2521=+param+",
            appendQueryParams(c, params).toString()
        )
    }

    companion object {
        fun appendQueryParams(source: URI, params: Collection<Pair<String, String>>): URI {
            val existingQuery = source.query
            val extraQuery = params.joinToString("&") { (key, value) ->
                URLEncoder.encode(key, UTF_8) + "=" + URLEncoder.encode(value, UTF_8)
            }
            val query =
                if (existingQuery == null || existingQuery.isEmpty()) extraQuery else "$existingQuery&$extraQuery"
            return try {
                URI(source.scheme, source.userInfo, source.host, source.port, source.path, query, source.fragment)
            } catch (e: URISyntaxException) {
                throw IllegalArgumentException("URI syntax got invalid during query appending!", e)
            }
        }
    }
}