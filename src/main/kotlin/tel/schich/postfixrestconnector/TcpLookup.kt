package tel.schich.postfixrestconnector

import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.text.Charsets.UTF_8

object TcpLookup {
    const val LOOKUP_PREFIX = "get "
    const val MAXIMUM_RESPONSE_LENGTH = 4096
    const val END_CHAR = '\n'
    const val END_CHAR_CODE = '\n'.code

    private const val PLUS_CHAR = "+"
    private const val PLUS_URL_ENCODED = "%2B"
    private const val SPACE_URL_ENCODED = "%20"

    fun decodeRequest(data: String): String {
        return URLDecoder.decode(data.replace(PLUS_CHAR, PLUS_URL_ENCODED), UTF_8)
    }

    fun encodeResponse(data: String): String {
        return URLEncoder.encode(data, UTF_8)
            .replace(PLUS_CHAR, SPACE_URL_ENCODED)
            .replace(PLUS_URL_ENCODED, PLUS_CHAR)
    }
}