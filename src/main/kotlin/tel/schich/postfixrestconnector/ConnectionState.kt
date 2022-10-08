package tel.schich.postfixrestconnector

import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.util.UUID

interface ConnectionState : Closeable {
    val id: UUID?

    @Throws(IOException::class)
    fun read(ch: SocketChannel, buffer: ByteBuffer): Long
}
