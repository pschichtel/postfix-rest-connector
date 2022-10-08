package tel.schich.postfixrestconnector

import io.ktor.utils.io.ByteWriteChannel
import java.io.Closeable
import java.nio.ByteBuffer
import java.util.UUID

interface ConnectionState : Closeable {
    val id: UUID?

    suspend fun read(ch: ByteWriteChannel, buffer: ByteBuffer): Long
}
