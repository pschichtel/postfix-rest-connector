package tel.schich.postfixrestconnector

import io.ktor.http.decodeURLPart
import io.ktor.http.encodeURLPathPart

object TcpLookup {
    const val LOOKUP_PREFIX = "get "
    const val MAXIMUM_RESPONSE_LENGTH = 4096
    const val END_CHAR = '\n'
    const val END_CHAR_CODE = '\n'.code.toByte()


    fun decodeRequest(data: String) = data.decodeURLPart()

    fun encodeResponse(data: String) = data.encodeURLPathPart()
}