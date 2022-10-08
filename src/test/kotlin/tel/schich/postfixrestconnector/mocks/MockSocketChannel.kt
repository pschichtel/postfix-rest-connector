@file:Suppress("DEPRECATION")

package tel.schich.postfixrestconnector.mocks

import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.WriterSuspendSession
import io.ktor.utils.io.bits.Memory
import io.ktor.utils.io.core.Buffer
import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.internal.ChunkBuffer
import java.nio.ByteBuffer

class MockSocketChannel : ByteWriteChannel {
    override val autoFlush: Boolean
        get() = TODO("Not yet implemented")
    override val availableForWrite: Int
        get() = TODO("Not yet implemented")
    override val closedCause: Throwable?
        get() = TODO("Not yet implemented")
    override val isClosedForWrite: Boolean
        get() = TODO("Not yet implemented")
    override val totalBytesWritten: Long
        get() = TODO("Not yet implemented")

    override suspend fun awaitFreeSpace() {
        TODO("Not yet implemented")
    }

    override fun close(cause: Throwable?): Boolean {
        TODO("Not yet implemented")
    }

    override fun flush() {
    }

    override suspend fun write(min: Int, block: (ByteBuffer) -> Unit) {
        TODO("Not yet implemented")
    }

    override suspend fun writeAvailable(src: ChunkBuffer): Int {
        TODO("Not yet implemented")
    }

    override suspend fun writeAvailable(src: ByteBuffer): Int {
        TODO("Not yet implemented")
    }

    override suspend fun writeAvailable(src: ByteArray, offset: Int, length: Int): Int {
        TODO("Not yet implemented")
    }

    override fun writeAvailable(min: Int, block: (ByteBuffer) -> Unit): Int {
        TODO("Not yet implemented")
    }

    override suspend fun writeByte(b: Byte) {
        TODO("Not yet implemented")
    }

    override suspend fun writeDouble(d: Double) {
        TODO("Not yet implemented")
    }

    override suspend fun writeFloat(f: Float) {
        TODO("Not yet implemented")
    }

    override suspend fun writeFully(memory: Memory, startIndex: Int, endIndex: Int) {
        TODO("Not yet implemented")
    }

    override suspend fun writeFully(src: Buffer) {
        TODO("Not yet implemented")
    }

    override suspend fun writeFully(src: ByteBuffer) {

    }

    override suspend fun writeFully(src: ByteArray, offset: Int, length: Int) {
        TODO("Not yet implemented")
    }

    override suspend fun writeInt(i: Int) {
        TODO("Not yet implemented")
    }

    override suspend fun writeLong(l: Long) {
        TODO("Not yet implemented")
    }

    override suspend fun writePacket(packet: ByteReadPacket) {
        TODO("Not yet implemented")
    }

    override suspend fun writeShort(s: Short) {
        TODO("Not yet implemented")
    }

    @Deprecated("Use write { } instead.")
    override suspend fun writeSuspendSession(visitor: suspend WriterSuspendSession.() -> Unit) {
        TODO("Not yet implemented")
    }

    override suspend fun writeWhile(block: (ByteBuffer) -> Boolean) {
        TODO("Not yet implemented")
    }

    companion object {
        val DEFAULT = MockSocketChannel()
    }
}
