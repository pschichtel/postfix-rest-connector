package tel.schich.postfixrestconnector

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

fun stringBuffer(s: String): ByteBuffer {
    return ByteBuffer.wrap(s.toByteArray(StandardCharsets.US_ASCII))
}
