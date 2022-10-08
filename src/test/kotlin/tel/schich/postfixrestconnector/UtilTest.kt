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
