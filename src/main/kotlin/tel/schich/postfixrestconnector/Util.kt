package tel.schich.postfixrestconnector

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel

@Throws(IOException::class)
fun writeAll(ch: WritableByteChannel, payload: ByteArray): Int {
    val buf = ByteBuffer.wrap(payload)
    var bytesWritten = 0
    while (buf.hasRemaining()) {
        bytesWritten += ch.write(buf)
    }
    return bytesWritten
}
