package tel.schich.postfixrestconnector

import io.ktor.utils.io.ByteWriteChannel
import java.nio.ByteBuffer
import java.util.UUID

abstract class ConnectionState {
    val id: UUID = UUID.randomUUID()

    abstract suspend fun read(ch: ByteWriteChannel, buffer: ByteBuffer)
}
