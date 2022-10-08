package tel.schich.postfixrestconnector

import kotlinx.serialization.decodeFromString
import java.io.Closeable
import java.io.IOException
import java.net.http.HttpClient
import java.nio.ByteBuffer
import java.nio.channels.SelectableChannel
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.channels.spi.SelectorProvider
import java.nio.file.Files
import java.nio.file.Path

import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.net.StandardSocketOptions.TCP_NODELAY
import java.nio.channels.SelectionKey.OP_ACCEPT
import java.nio.channels.SelectionKey.OP_READ
import kotlin.jvm.Throws

private val LOGGER: Logger = LoggerFactory.getLogger(RestConnector::class.java)
private const val READ_BUFFER_SIZE = 2048

class RestConnector : Closeable {
    @Volatile
    private var selector: Selector? = null

    @Volatile
    private var keepPolling: Boolean = true

    @Throws(IOException::class)
    fun start(provider: SelectorProvider, config: Configuration) {
        start(provider, Json.Default, config)
    }

    @Throws(IOException::class)
    fun start(provider: SelectorProvider, configPath: Path) {
        val content = Files.readString(configPath)
        val mapper = Json
        val config = mapper.decodeFromString<Configuration>(content)

        start(provider, mapper, config);
    }

    @Throws(IOException::class)
    fun start(provider: SelectorProvider, mapper: Json, config: Configuration) {

        if (selector != null) {
            error("Connector already started!")
        }
        val selector = provider.openSelector()
        this.selector = selector

        val buffer: ByteBuffer = ByteBuffer.allocateDirect(READ_BUFFER_SIZE)
        val restClient: HttpClient = HttpClient.newHttpClient()

        for (endpoint in config.endpoints) {
            val serverChannel: ServerSocketChannel = provider.openServerSocketChannel()

            val request = when (endpoint.mode) {
                TcpLookupHandler.MODE_NAME -> TcpLookupHandler(endpoint, restClient, mapper, config.userAgent)
                SocketmapLookupHandler.MODE_NAME -> SocketmapLookupHandler(endpoint, restClient, mapper, config.userAgent)
                PolicyRequestHandler.MODE_NAME -> PolicyRequestHandler(endpoint, restClient, config.userAgent)
                else -> error("Unknown mode " + endpoint.mode + "!")
            }

            serverChannel.bind(endpoint.address())
            configureChannel(serverChannel)
            serverChannel.register(selector, OP_ACCEPT, request)

            LOGGER.info("Bound endpoint {} to address: {}", endpoint.name, serverChannel.localAddress)
        }

        while (keepPolling) {
            if (selector.select() <= 0) {
                continue;
            }

            val keys = selector.selectedKeys();
            val it = keys.iterator();
            while (it.hasNext()) {
                val key = it.next()
                it.remove();

                val channel = key.channel();

                if (key.isAcceptable && channel is ServerSocketChannel) {
                    val clientChannel = channel.accept()
                    configureChannel(clientChannel)
                    val handler = key.attachment() as PostfixRequestHandler
                    val endpoint = handler.endpoint
                    val state = handler.createState()
                    clientChannel.register(selector, OP_READ, state)
                    val remoteAddress = clientChannel.remoteAddress
                    LOGGER.info("{} - Client connected from {} on endpoint {}", state.id, remoteAddress, endpoint.name)
                    continue
                }

                if (!key.isValid) {
                    channel.close()
                }

                if (!channel.isOpen) {
                    key.cancel()
                    val attachment = key.attachment()
                    if (attachment is Closeable) {
                        attachment.close()
                    }
                    continue
                }

                if (key.isReadable && channel is SocketChannel) {
                    val state = key.attachment() as ConnectionState;
                    readChannel(channel, buffer, state);
                }
            }
        }
    }

    fun stop() {
        val selector = this.selector
        if (selector != null) {
            this.keepPolling = false
            selector.wakeup()
        }
    }

    @Override
    override fun close() {
        this.stop();
    }

    @Throws(IOException::class)
    private fun readChannel(ch: SocketChannel, buffer: ByteBuffer, state: ConnectionState) {
        buffer.clear()
        val bytesRead = try {
            ch.read(buffer)
        } catch (e: IOException) {
            LOGGER.error("{} - Channel Read failed!", state.id, e)
            -1
        }
        if (bytesRead == -1) {
            ch.close()
            return
        }

        buffer.flip()
        state.read(ch, buffer)
    }

    @Throws(IOException::class)
    private fun configureChannel(ch: SelectableChannel) {
        ch.configureBlocking(false)
        if (ch is SocketChannel)
            ch.setOption(TCP_NODELAY, true)
        }
    }
