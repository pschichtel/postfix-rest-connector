package tel.schich.postfixrestconnector

import io.ktor.utils.io.ByteWriteChannel
import kotlinx.io.Source
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

abstract class ConnectionState {
    @OptIn(ExperimentalUuidApi::class)
    val id: Uuid = Uuid.random()

    abstract suspend fun read(ch: ByteWriteChannel, buffer: Source)
}
