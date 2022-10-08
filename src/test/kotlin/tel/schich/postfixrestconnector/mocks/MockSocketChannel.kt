package tel.schich.postfixrestconnector.mocks

import java.net.Socket
import java.net.SocketAddress
import java.net.SocketOption
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.channels.spi.SelectorProvider

class MockSocketChannel : SocketChannel(SelectorProvider.provider()) {
    override fun bind(local: SocketAddress): SocketChannel? {
        return null
    }

    override fun <T> setOption(name: SocketOption<T>, value: T): SocketChannel? {
        return null
    }

    override fun shutdownInput(): SocketChannel? {
        return null
    }

    override fun shutdownOutput(): SocketChannel? {
        return null
    }

    override fun socket(): Socket? {
        return null
    }

    override fun isConnected(): Boolean {
        return false
    }

    override fun isConnectionPending(): Boolean {
        return false
    }

    override fun connect(remote: SocketAddress): Boolean {
        return false
    }

    override fun finishConnect(): Boolean {
        return false
    }

    override fun getRemoteAddress(): SocketAddress? {
        return null
    }

    override fun read(dst: ByteBuffer): Int {
        return 0
    }

    override fun read(dsts: Array<ByteBuffer>, offset: Int, length: Int): Long {
        return 0
    }

    override fun write(src: ByteBuffer): Int {
        val remaining = src.remaining()
        src.position(src.limit())
        return remaining
    }

    override fun write(srcs: Array<ByteBuffer>, offset: Int, length: Int): Long {
        return 0
    }

    override fun getLocalAddress(): SocketAddress? {
        return null
    }

    override fun <T> getOption(name: SocketOption<T>): T? {
        return null
    }

    override fun supportedOptions(): Set<SocketOption<*>>? {
        return null
    }

    override fun implCloseSelectableChannel() {}
    override fun implConfigureBlocking(block: Boolean) {}

    companion object {
        val DEFAULT = MockSocketChannel()
    }
}
