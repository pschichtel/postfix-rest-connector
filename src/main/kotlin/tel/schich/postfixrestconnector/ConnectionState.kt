package tel.schich.postfixrestconnector

import io.ktor.utils.io.ByteWriteChannel
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.bytestring.ByteString
import java.nio.ByteBuffer
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

abstract class ConnectionState {
    @OptIn(ExperimentalUuidApi::class)
    val id: Uuid = Uuid.random()

    abstract suspend fun read(ch: ByteWriteChannel, buffer: Source)
}
